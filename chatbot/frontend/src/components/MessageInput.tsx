import { useState, type FormEvent, type KeyboardEvent } from 'react';

interface MessageInputProps {
  disabled: boolean;
  onSend: (text: string) => void;
  maxChars?: number;
}

export function MessageInput({ disabled, onSend, maxChars = 2000 }: MessageInputProps) {
  const [text, setText] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);

  function submit() {
    if (disabled) {
      return;
    }
    const trimmed = text.trim();
    if (!trimmed) {
      setLocalError('Please enter a message before sending.');
      return;
    }
    if (trimmed.length > maxChars) {
      setLocalError(`Please enter a message of up to ${maxChars.toLocaleString('en-US')} characters.`);
      return;
    }
    setLocalError(null);
    onSend(trimmed);
    setText('');
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    submit();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      submit();
    }
  }

  return (
    <form className="message-input" onSubmit={handleSubmit}>
      <label htmlFor="chat-input" className="visually-hidden">
        Your message
      </label>
      <textarea
        id="chat-input"
        rows={2}
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          if (localError) {
            setLocalError(null);
          }
        }}
        onKeyDown={handleKeyDown}
        placeholder="Type your question"
        disabled={disabled}
      />
      <div className="message-input__footer">
        <span className="message-input__count" aria-live="polite">
          {text.trim().length.toLocaleString('en-US')} / {maxChars.toLocaleString('en-US')}
        </span>
        <button type="submit" disabled={disabled}>
          Send
        </button>
      </div>
      {localError && (
        <p className="message-input__error" role="alert">
          {localError}
        </p>
      )}
    </form>
  );
}
