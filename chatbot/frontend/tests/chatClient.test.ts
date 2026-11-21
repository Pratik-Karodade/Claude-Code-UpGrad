import { afterEach, describe, expect, it, vi } from 'vitest';
import { parseBlock, streamChat } from '../src/api/chatClient';
import { FRIENDLY_MESSAGES } from '../src/api/errorMessages';
import type { ChatStreamEvent } from '../src/types/chat';

const request = { conversationId: '3f2c9a1e-7b4d-4e8a-9c21-5d6e7f8a9b0c', message: 'hi' };

/** Builds a Response whose body emits the given string chunks in order. */
function sseResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder();
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    },
  });
  return new Response(stream, { status, headers: { 'Content-Type': 'text/event-stream' } });
}

async function collect(): Promise<{ events: ChatStreamEvent[]; onEvent: (e: ChatStreamEvent) => void }> {
  const events: ChatStreamEvent[] = [];
  return { events, onEvent: (e) => events.push(e) };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('streamChat', () => {
  it('reassembles SSE frames split across chunk boundaries', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        sseResponse([
          'event: token\ndata: {"te',
          'xt":"Hi"}\n\nevent: done\ndata: {"requestId":"req_1",',
          '"escalation":false,"latencyMs":7}\n\n',
        ]),
      ),
    );
    const { events, onEvent } = await collect();

    await streamChat(request, onEvent);

    expect(events).toEqual([
      { type: 'token', data: { text: 'Hi' } },
      { type: 'done', data: { requestId: 'req_1', escalation: false, latencyMs: 7 } },
    ]);
  });

  it('maps a server error event to its friendly message and never shows raw text', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        sseResponse(['event: error\ndata: {"requestId":"req_2","code":"RATE_LIMITED","message":"RAW-PROVIDER-TEXT"}\n\n']),
      ),
    );
    const { events, onEvent } = await collect();

    await streamChat(request, onEvent);

    expect(events).toEqual([
      {
        type: 'error',
        data: {
          requestId: 'req_2',
          code: 'RATE_LIMITED',
          message: FRIENDLY_MESSAGES.RATE_LIMITED,
        },
      },
    ]);
    expect(JSON.stringify(events)).not.toContain('RAW-PROVIDER-TEXT');
  });

  it('emits NETWORK_ERROR when fetch itself fails', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));
    const { events, onEvent } = await collect();

    await streamChat(request, onEvent);

    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject({ type: 'error', data: { code: 'NETWORK_ERROR' } });
  });

  it('emits NETWORK_ERROR when the stream ends without a terminal event', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => sseResponse(['event: token\ndata: {"text":"partial"}\n\n'])),
    );
    const { events, onEvent } = await collect();

    await streamChat(request, onEvent);

    expect(events.at(-1)).toMatchObject({ type: 'error', data: { code: 'NETWORK_ERROR' } });
  });

  it('maps an HTTP 400 JSON body to VALIDATION_ERROR', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ code: 'VALIDATION_ERROR', message: 'x', requestId: 'req_3' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const { events, onEvent } = await collect();

    await streamChat(request, onEvent);

    expect(events).toEqual([
      {
        type: 'error',
        data: { requestId: 'req_3', code: 'VALIDATION_ERROR', message: FRIENDLY_MESSAGES.VALIDATION_ERROR },
      },
    ]);
  });
});

describe('parseBlock', () => {
  it('returns null for comments and unknown events', () => {
    expect(parseBlock(': keep-alive')).toBeNull();
    expect(parseBlock('event: ping\ndata: {}')).toBeNull();
  });
});
