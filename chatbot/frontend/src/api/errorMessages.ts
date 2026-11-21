import type { ErrorCode } from '../types/chat';

/**
 * Friendly, non-technical messages per error code (contracts/chat-api.md).
 * The UI shows only these strings. Raw server or provider text is never rendered.
 */
export const FRIENDLY_MESSAGES: Record<ErrorCode, string> = {
  VALIDATION_ERROR: 'Please enter a message of up to 2,000 characters.',
  RATE_LIMITED: "We're receiving a lot of requests right now. Please try again in a moment.",
  PROVIDER_UNAVAILABLE: 'The assistant is temporarily unavailable. Please try again shortly.',
  INTERNAL: 'Something went wrong. Please try again.',
  NETWORK_ERROR: 'Connection lost. Check your internet connection and try again.',
};
