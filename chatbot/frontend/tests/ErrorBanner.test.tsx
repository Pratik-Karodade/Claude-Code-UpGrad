import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ErrorBanner } from '../src/components/ErrorBanner';
import { FRIENDLY_MESSAGES } from '../src/api/errorMessages';
import type { ErrorCode } from '../src/types/chat';

const CODES: ErrorCode[] = ['VALIDATION_ERROR', 'RATE_LIMITED', 'PROVIDER_UNAVAILABLE', 'INTERNAL', 'NETWORK_ERROR'];

describe('ErrorBanner', () => {
  it.each(CODES)('renders the friendly message for %s', (code) => {
    render(<ErrorBanner error={{ code, message: 'ignored' }} />);

    expect(screen.getByRole('alert')).toHaveTextContent(FRIENDLY_MESSAGES[code]);
  });

  it('calls onRetry when the retry button is pressed', async () => {
    const onRetry = vi.fn();
    render(<ErrorBanner error={{ code: 'RATE_LIMITED', message: 'x' }} onRetry={onRetry} />);

    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('never renders raw error text', () => {
    render(<ErrorBanner error={{ code: 'INTERNAL', message: 'RAW-STACK-TRACE 500' }} />);

    expect(screen.queryByText(/RAW-STACK-TRACE/)).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).not.toHaveTextContent('500');
  });

  it('renders nothing when there is no error', () => {
    const { container } = render(<ErrorBanner error={null} />);
    expect(container).toBeEmptyDOMElement();
  });
});
