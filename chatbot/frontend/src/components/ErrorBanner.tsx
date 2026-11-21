import { FRIENDLY_MESSAGES } from '../api/errorMessages';
import type { ChatError } from '../types/chat';

interface ErrorBannerProps {
  error: ChatError | null;
  onRetry?: () => void;
}

/**
 * Shows the friendly message for the error code. The text comes from FRIENDLY_MESSAGES,
 * so raw server or provider text is never rendered.
 */
export function ErrorBanner({ error, onRetry }: ErrorBannerProps) {
  if (!error) {
    return null;
  }

  return (
    <div className="error-banner" role="alert">
      <p>{FRIENDLY_MESSAGES[error.code]}</p>
      {onRetry && (
        <button type="button" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
