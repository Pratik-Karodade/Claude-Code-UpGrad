import { useCallback, useRef, useState } from 'react';
import { streamChat } from '../api/chatClient';
import { FRIENDLY_MESSAGES } from '../api/errorMessages';
import type { ChatError, ChatRequest, ChatStreamEvent, Message } from '../types/chat';

export const MAX_MESSAGE_CHARS = 2000;

export type SendFn = (
  request: ChatRequest,
  onEvent: (event: ChatStreamEvent) => void,
) => Promise<void>;

function newId(): string {
  return globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2);
}

/**
 * Chat state for one browser session: messages, streaming status, error, and the
 * conversation ID sent with every request. Nothing is persisted (FR-011).
 */
export function useChatSession(send: SendFn = streamChat) {
  const [conversationId, setConversationId] = useState<string>(newId);
  const [messages, setMessages] = useState<Message[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<ChatError | null>(null);

  // Ref, not state: blocks a second send that starts before the re-render lands.
  const streamingRef = useRef(false);

  const sendMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || streamingRef.current) {
        return;
      }
      if (trimmed.length > MAX_MESSAGE_CHARS) {
        setError({ code: 'VALIDATION_ERROR', message: FRIENDLY_MESSAGES.VALIDATION_ERROR });
        return;
      }

      streamingRef.current = true;
      const assistantId = newId();
      setMessages((prev) => [
        ...prev,
        { id: newId(), role: 'user', content: trimmed, status: 'complete' },
        { id: assistantId, role: 'assistant', content: '', status: 'streaming' },
      ]);
      setIsStreaming(true);
      setError(null);

      const updateAssistant = (change: (m: Message) => Message) =>
        setMessages((prev) => prev.map((m) => (m.id === assistantId ? change(m) : m)));

      const terminal = { seen: false };
      try {
        await send({ conversationId, message: trimmed }, (event) => {
          switch (event.type) {
            case 'token':
              updateAssistant((m) => ({ ...m, content: m.content + event.data.text }));
              break;
            case 'done':
              terminal.seen = true;
              updateAssistant((m) => ({
                ...m,
                status: 'complete',
                escalation: event.data.escalation,
              }));
              break;
            case 'error':
              terminal.seen = true;
              updateAssistant((m) => ({ ...m, status: 'failed' }));
              setError({ code: event.data.code, message: FRIENDLY_MESSAGES[event.data.code] });
              break;
          }
        });
      } finally {
        if (!terminal.seen) {
          updateAssistant((m) => ({ ...m, status: 'failed' }));
          setError({ code: 'NETWORK_ERROR', message: FRIENDLY_MESSAGES.NETWORK_ERROR });
        }
        streamingRef.current = false;
        setIsStreaming(false);
      }
    },
    [conversationId, send],
  );

  /** Clears history and starts a new conversation ID, so the server window is abandoned. */
  const newChat = useCallback(() => {
    if (streamingRef.current) {
      return;
    }
    setMessages([]);
    setError(null);
    setConversationId(newId());
  }, []);

  /** Removes the failed exchange and resends its user message. */
  const retry = useCallback(() => {
    if (streamingRef.current || !error) {
      return;
    }
    const lastUser = [...messages].reverse().find((m) => m.role === 'user');
    if (!lastUser) {
      return;
    }
    setMessages((prev) => {
      const index = prev.findIndex((m) => m.id === lastUser.id);
      return index === -1 ? prev : prev.slice(0, index);
    });
    void sendMessage(lastUser.content);
  }, [error, messages, sendMessage]);

  return {
    conversationId,
    messages,
    isStreaming,
    error,
    sendMessage,
    newChat,
    retry,
  };
}
