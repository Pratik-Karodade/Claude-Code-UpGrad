import { useChatSession } from '../hooks/useChatSession';
import { ErrorBanner } from './ErrorBanner';
import { MessageInput } from './MessageInput';
import { MessageList } from './MessageList';

export function ChatWindow() {
  const { messages, isStreaming, error, sendMessage, newChat, retry } = useChatSession();

  // Validation errors are fixed by editing the message, so they get no retry button.
  const canRetry = error !== null && error.code !== 'VALIDATION_ERROR';

  return (
    <main className="chat-window">
      <header className="chat-window__header">
        <h1>Caramel AI Tech Support</h1>
        <button type="button" onClick={newChat} disabled={isStreaming}>
          New chat
        </button>
      </header>

      <MessageList messages={messages} />
      <ErrorBanner error={error} onRetry={canRetry ? retry : undefined} />
      <MessageInput disabled={isStreaming} onSend={sendMessage} />
    </main>
  );
}
