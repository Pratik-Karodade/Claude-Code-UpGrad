import { describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useChatSession, type SendFn } from '../src/hooks/useChatSession';
import type { ChatStreamEvent } from '../src/types/chat';

/** Creates a send function that emits the given events, then resolves. */
function scripted(events: ChatStreamEvent[]): SendFn {
  return vi.fn(async (_request, onEvent) => {
    for (const event of events) {
      onEvent(event);
    }
  });
}

const DONE = (escalation = false): ChatStreamEvent => ({
  type: 'done',
  data: { requestId: 'req_1', escalation, latencyMs: 10 },
});

describe('useChatSession', () => {
  it('appends tokens in order and marks the assistant reply complete on done', async () => {
    const send = scripted([
      { type: 'token', data: { text: 'Hel' } },
      { type: 'token', data: { text: 'lo' } },
      DONE(),
    ]);
    const { result } = renderHook(() => useChatSession(send));

    await act(async () => {
      await result.current.sendMessage('hi');
    });

    expect(result.current.messages).toHaveLength(2);
    expect(result.current.messages[0]).toMatchObject({ role: 'user', content: 'hi', status: 'complete' });
    expect(result.current.messages[1]).toMatchObject({
      role: 'assistant',
      content: 'Hello',
      status: 'complete',
      escalation: false,
    });
    expect(result.current.isStreaming).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('marks the reply failed and sets the error on an error event', async () => {
    const send = scripted([
      { type: 'token', data: { text: 'partial' } },
      { type: 'error', data: { requestId: 'req_2', code: 'PROVIDER_UNAVAILABLE', message: 'friendly' } },
    ]);
    const { result } = renderHook(() => useChatSession(send));

    await act(async () => {
      await result.current.sendMessage('hi');
    });

    expect(result.current.messages[1]).toMatchObject({ content: 'partial', status: 'failed' });
    expect(result.current.error?.code).toBe('PROVIDER_UNAVAILABLE');
    expect(result.current.isStreaming).toBe(false);
  });

  it('treats a stream that ends without a terminal event as a network failure', async () => {
    const send = scripted([{ type: 'token', data: { text: 'cut off' } }]);
    const { result } = renderHook(() => useChatSession(send));

    await act(async () => {
      await result.current.sendMessage('hi');
    });

    expect(result.current.messages[1].status).toBe('failed');
    expect(result.current.error?.code).toBe('NETWORK_ERROR');
  });

  it('ignores a second send while a reply is streaming', async () => {
    let finish: () => void = () => {};
    const send: SendFn = vi.fn(
      (_request, onEvent) =>
        new Promise<void>((resolve) => {
          finish = () => {
            onEvent(DONE());
            resolve();
          };
        }),
    );
    const { result } = renderHook(() => useChatSession(send));

    let first: Promise<void> = Promise.resolve();
    act(() => {
      first = result.current.sendMessage('one');
    });
    await act(async () => {
      await result.current.sendMessage('two');
    });
    expect(send).toHaveBeenCalledTimes(1);

    await act(async () => {
      finish();
      await first;
    });
  });

  it('marks escalation on the reply when done reports it', async () => {
    const send = scripted([{ type: 'token', data: { text: 'Please contact support.' } }, DONE(true)]);
    const { result } = renderHook(() => useChatSession(send));

    await act(async () => {
      await result.current.sendMessage('help');
    });

    expect(result.current.messages[1].escalation).toBe(true);
  });

  it('newChat clears messages and starts a new conversation ID', async () => {
    const send = scripted([{ type: 'token', data: { text: 'a' } }, DONE()]);
    const { result } = renderHook(() => useChatSession(send));

    await act(async () => {
      await result.current.sendMessage('hi');
    });
    const before = result.current.conversationId;

    act(() => {
      result.current.newChat();
    });

    expect(result.current.messages).toHaveLength(0);
    expect(result.current.error).toBeNull();
    expect(result.current.conversationId).not.toBe(before);
  });

  it('sends the same conversation ID on every message in one chat', async () => {
    const send = scripted([DONE()]);
    const { result } = renderHook(() => useChatSession(send));

    await act(async () => {
      await result.current.sendMessage('one');
    });
    await act(async () => {
      await result.current.sendMessage('two');
    });

    const calls = (send as ReturnType<typeof vi.fn>).mock.calls;
    expect(calls[0][0].conversationId).toBe(calls[1][0].conversationId);
  });
});
