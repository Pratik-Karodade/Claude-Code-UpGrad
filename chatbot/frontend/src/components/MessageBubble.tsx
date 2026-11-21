import type { Message } from '../types/chat';

interface MessageBubbleProps {
  message: Message;
}

export function MessageBubble({ message }: MessageBubbleProps) {
  const isAssistant = message.role === 'assistant';
  const showEscalation = isAssistant && message.status === 'complete' && message.escalation === true;

  return (
    <article className={`message message--${message.role} message--${message.status}`}>
      <p className="message__label">{isAssistant ? 'Caramel AI Support' : 'You'}</p>
      <p className="message__content">{message.content}</p>

      {message.status === 'streaming' && (
        <span className="message__typing" role="status" aria-label="Assistant is typing">
          Typing…
        </span>
      )}

      {message.status === 'failed' && (
        <p className="message__failed">This reply did not finish.</p>
      )}

      {showEscalation && (
        <p className="message__referral">
          Need more help? Contact Caramel AI human support.
        </p>
      )}
    </article>
  );
}
