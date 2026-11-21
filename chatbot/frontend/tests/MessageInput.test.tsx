import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MessageInput } from '../src/components/MessageInput';

describe('MessageInput', () => {
  it('does not send whitespace-only input and shows a message', async () => {
    const onSend = vi.fn();
    render(<MessageInput disabled={false} onSend={onSend} />);

    await userEvent.type(screen.getByRole('textbox'), '   ');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    expect(onSend).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('Please enter a message before sending.');
  });

  it('blocks input over 2,000 characters and shows the limit message', async () => {
    const onSend = vi.fn();
    render(<MessageInput disabled={false} onSend={onSend} />);

    // Paste is faster than typing 2,001 characters.
    const textbox = screen.getByRole('textbox') as HTMLTextAreaElement;
    textbox.focus();
    await userEvent.paste('a'.repeat(2001));
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    expect(onSend).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('up to 2,000 characters');
  });

  it('sends trimmed text and clears the box', async () => {
    const onSend = vi.fn();
    render(<MessageInput disabled={false} onSend={onSend} />);

    await userEvent.type(screen.getByRole('textbox'), '  hello  ');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    expect(onSend).toHaveBeenCalledWith('hello');
    expect(screen.getByRole('textbox')).toHaveValue('');
  });

  it('disables the send button and input while streaming', () => {
    render(<MessageInput disabled={true} onSend={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled();
    expect(screen.getByRole('textbox')).toBeDisabled();
  });
});
