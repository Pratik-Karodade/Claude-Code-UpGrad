import { FRIENDLY_MESSAGES } from './errorMessages';
import type {
  ChatRequest,
  ChatStreamEvent,
  DoneEvent,
  ErrorCode,
  ErrorEvent,
  ServerErrorCode,
  TokenEvent,
} from '../types/chat';

/**
 * The only module that calls fetch for chat. The API key never reaches the browser;
 * this client talks to the backend only.
 */
export const API_BASE_URL: string =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080';

export type ChatEventHandler = (event: ChatStreamEvent) => void;

const SERVER_CODES: ReadonlySet<string> = new Set<ServerErrorCode>([
  'VALIDATION_ERROR',
  'RATE_LIMITED',
  'PROVIDER_UNAVAILABLE',
  'INTERNAL',
]);

/**
 * POSTs a message and streams SSE events to onEvent. Always ends with exactly one
 * terminal event (done or error). Connection failures become NETWORK_ERROR.
 *
 * Uses fetch with a streamed body because EventSource cannot send a POST body (research R5).
 */
export async function streamChat(
  request: ChatRequest,
  onEvent: ChatEventHandler,
  signal?: AbortSignal,
): Promise<void> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/api/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(request),
      signal,
    });
  } catch {
    onEvent(networkError());
    return;
  }

  if (!response.ok) {
    onEvent(await httpError(response));
    return;
  }

  if (!response.body) {
    onEvent(networkError());
    return;
  }

  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';
  let terminal = false;

  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }
      buffer += value.replace(/\r\n/g, '\n');

      let boundary = buffer.indexOf('\n\n');
      while (boundary !== -1) {
        const block = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const event = parseBlock(block);
        if (event) {
          onEvent(event);
          if (event.type === 'done' || event.type === 'error') {
            terminal = true;
          }
        }
        boundary = buffer.indexOf('\n\n');
      }
    }
  } catch {
    onEvent(networkError());
    return;
  }

  if (!terminal) {
    onEvent(networkError());
  }
}

/** Parses one SSE block ("event: x\ndata: y") into a typed event, or null if unrecognized. */
export function parseBlock(block: string): ChatStreamEvent | null {
  let eventName: string | null = null;
  const dataLines: string[] = [];

  for (const line of block.split('\n')) {
    if (line.startsWith(':')) {
      continue; // SSE comment
    }
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).replace(/^ /, ''));
    }
  }

  if (eventName === null || dataLines.length === 0) {
    return null;
  }

  let payload: unknown;
  try {
    payload = JSON.parse(dataLines.join('\n'));
  } catch {
    return null;
  }

  switch (eventName) {
    case 'token':
      return { type: 'token', data: payload as TokenEvent };
    case 'done':
      return { type: 'done', data: payload as DoneEvent };
    case 'error': {
      const raw = payload as Partial<ErrorEvent>;
      const code = toErrorCode(raw.code);
      return {
        type: 'error',
        data: { requestId: raw.requestId ?? '', code, message: FRIENDLY_MESSAGES[code] },
      };
    }
    default:
      return null;
  }
}

async function httpError(response: Response): Promise<ChatStreamEvent> {
  let code: string | undefined;
  let requestId = '';
  try {
    const body = (await response.json()) as { code?: string; requestId?: string };
    code = body.code;
    requestId = body.requestId ?? '';
  } catch {
    // Body was not JSON; fall through to the generic code below.
  }
  const resolved = toErrorCode(code);
  return {
    type: 'error',
    data: { requestId, code: resolved, message: FRIENDLY_MESSAGES[resolved] },
  };
}

function toErrorCode(code: string | undefined): ErrorCode {
  return code !== undefined && SERVER_CODES.has(code) ? (code as ServerErrorCode) : 'INTERNAL';
}

function networkError(): ChatStreamEvent {
  return {
    type: 'error',
    data: { requestId: '', code: 'NETWORK_ERROR', message: FRIENDLY_MESSAGES.NETWORK_ERROR },
  };
}
