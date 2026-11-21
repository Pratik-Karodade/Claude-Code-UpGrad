import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MessageList } from '../src/components/MessageList';
import type { Message } from '../src/types/chat';

const user: Message = { id: 'u1', role: 'user', content: 'How do I rotate my key?', status: 'complete' };
const assistant: Message = { id: 'a1', role: 'assistant', content: 'Go to Settings.', status: 'complete' };

describe('MessageList', () => {
  it('renders user and assistant messages in order', () => {
    render(<MessageList messages={[user, assistant]} />);

    const log = screen.getByRole('log');
    const articles = within(log).getAllByRole('article');
    expect(articles).toHaveLength(2);
    expect(articles[0]).toHaveTextContent('How do I rotate my key?');
    expect(articles[1]).toHaveTextContent('Go to Settings.');
  });

  it('shows a typing indicator while a reply is streaming', () => {
    const streaming: Message = { id: 'a2', role: 'assistant', content: 'Let me', status: 'streaming' };
    render(<MessageList messages={[user, streaming]} />);

    expect(screen.getByRole('status', { name: /typing/i })).toBeInTheDocument();
  });

  it('shows the human-support referral only when escalation is true', () => {
    const escalated: Message = { ...assistant, id: 'a3', escalation: true };
    const { rerender } = render(<MessageList messages={[user, escalated]} />);
    expect(screen.getByText(/contact caramel ai human support/i)).toBeInTheDocument();

    rerender(<MessageList messages={[user, { ...assistant, id: 'a4', escalation: false }]} />);
    expect(screen.queryByText(/contact caramel ai human support/i)).not.toBeInTheDocument();
  });

  it('shows an empty-state hint when there are no messages', () => {
    render(<MessageList messages={[]} />);
    expect(screen.getByText(/ask about caramel ai/i)).toBeInTheDocument();
  });
});
