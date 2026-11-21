/**
 * Types mirroring contracts/chat-api.md. Keep in sync with the backend contract.
 */

/** Error codes returned by the server. */
export type ServerErrorCode =
  | 'VALIDATION_ERROR'
  | 'RATE_LIMITED'
  | 'PROVIDER_UNAVAILABLE'
  | 'INTERNAL';

/**
 * Client-only code for failures where no server response arrived (network drop or
 * stream cut). The contract does not return this code; the client detects it.
 */
export type ErrorCode = ServerErrorCode | 'NETWORK_ERROR';

export interface ChatRequest {
  conversationId: string;
  message: string;
}

export interface TokenEvent {
  text: string;
}

export interface DoneEvent {
  requestId: string;
  escalation: boolean;
  latencyMs: number;
}

export interface ErrorEvent {
  requestId: string;
  code: ErrorCode;
  message: string;
}

export type ChatStreamEvent =
  | { type: 'token'; data: TokenEvent }
  | { type: 'done'; data: DoneEvent }
  | { type: 'error'; data: ErrorEvent };

export type MessageRole = 'user' | 'assistant';

export type MessageStatus = 'streaming' | 'complete' | 'failed';

export interface Message {
  id: string;
  role: MessageRole;
  content: string;
  status: MessageStatus;
  /** Set on assistant messages when the reply referred the user to human support. */
  escalation?: boolean;
}

export interface ChatError {
  code: ErrorCode;
  message: string;
}
