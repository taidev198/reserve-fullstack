import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReservationRow } from '../components/reservation/ReservationRow';
import type { ReservationView } from '../types/api';

const base: ReservationView = {
  id: 42,
  orderId: 'ORD-42',
  status: 'PENDING',
  canConfirm: true,
  canCancel: true,
  items: [{ sku: 'A100', name: 'Mouse', quantity: 3 }],
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

describe('ReservationRow', () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });
  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('disables both action buttons in a terminal state', () => {
    render(
      <ReservationRow
        reservation={{ ...base, status: 'CONFIRMED', canConfirm: false, canCancel: false }}
        onUpdated={vi.fn()}
        onActionError={vi.fn()}
      />,
    );
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled();
  });

  it('shows optimistic CONFIRMED on the badge while confirm is in-flight', async () => {
    let finish!: (value: Response) => void;
    const deferred = new Promise<Response>((resolve) => {
      finish = resolve;
    });
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation(() => deferred);

    const updated: ReservationView = { ...base, status: 'CONFIRMED', canConfirm: false, canCancel: false };
    const user = userEvent.setup();
    render(<ReservationRow reservation={base} onUpdated={vi.fn()} onActionError={vi.fn()} />);

    const clickPromise = user.click(screen.getByRole('button', { name: 'Confirm' }));

    await vi.waitFor(() => {
      expect(screen.getByText('CONFIRMED')).toBeInTheDocument();
    });

    await act(async () => {
      finish({
        ok: true,
        status: 200,
        json: async () => updated,
      } as Response);
      await clickPromise;
    });
  });

  it('calls onUpdated with the server response after confirming', async () => {
    const updated: ReservationView = { ...base, status: 'CONFIRMED', canConfirm: false, canCancel: false };
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => updated,
    } as Response);

    const onUpdated = vi.fn();
    const user = userEvent.setup();
    render(<ReservationRow reservation={base} onUpdated={onUpdated} onActionError={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'Confirm' }));

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/reservations/42/confirm',
      expect.objectContaining({ method: 'POST' }),
    );
    await vi.waitFor(() => expect(onUpdated).toHaveBeenCalledWith(updated));
  });

  it('surfaces structured API errors through onActionError', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({
        code: 'ILLEGAL_TRANSITION',
        message: 'Cannot confirm reservation in state CANCELLED',
        details: [],
        timestamp: new Date().toISOString(),
      }),
    } as Response);

    const onError = vi.fn();
    const user = userEvent.setup();
    render(<ReservationRow reservation={base} onUpdated={vi.fn()} onActionError={onError} />);

    await user.click(screen.getByRole('button', { name: 'Confirm' }));

    await vi.waitFor(() =>
      expect(onError).toHaveBeenCalledWith(
        'ILLEGAL_TRANSITION: Cannot confirm reservation in state CANCELLED',
      ),
    );
  });
});
