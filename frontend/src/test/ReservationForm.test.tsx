import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReservationForm } from '../components/reservation/ReservationForm';
import type { InventoryView, ReservationView } from '../types/api';
import { toast } from 'sonner';

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    info: vi.fn(),
    error: vi.fn(),
  },
}));

const inventory: InventoryView[] = [
  { sku: 'A100', name: 'Mouse',    totalQuantity: 100, reservedQuantity: 0, availableQuantity: 100 },
  { sku: 'B200', name: 'Keyboard', totalQuantity: 10,  reservedQuantity: 8, availableQuantity: 2   },
];

describe('ReservationForm', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });
  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('blocks submission when quantity exceeds available stock', async () => {
    const onCreated = vi.fn();
    const user = userEvent.setup();

    render(<ReservationForm inventory={inventory} onReservationCreated={onCreated} />);

    await user.type(screen.getByLabelText('Order ID'), 'ORD-1');
    await user.selectOptions(screen.getByLabelText('SKU 1'), 'B200');
    const qty = screen.getByLabelText('Quantity 1');
    await user.clear(qty);
    await user.type(qty, '5'); // only 2 available

    await user.click(screen.getByRole('button', { name: 'Reserve' }));

    expect(await screen.findByText('Only 2 available')).toBeInTheDocument();
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('submits a valid reservation and notifies parent on success', async () => {
    const created: ReservationView = {
      id: 1,
      orderId: 'ORD-1',
      status: 'PENDING',
      canConfirm: true,
      canCancel: true,
      items: [{ sku: 'A100', name: 'Mouse', quantity: 2 }],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 201,
      headers: { get: () => 'false' },
      json: async () => created,
    } as unknown as Response);

    const onCreated = vi.fn();
    const user = userEvent.setup();
    render(<ReservationForm inventory={inventory} onReservationCreated={onCreated} />);

    await user.type(screen.getByLabelText('Order ID'), 'ORD-1');
    await user.selectOptions(screen.getByLabelText('SKU 1'), 'A100');
    const qty = screen.getByLabelText('Quantity 1');
    await user.clear(qty);
    await user.type(qty, '2');

    await user.click(screen.getByRole('button', { name: 'Reserve' }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created));
    expect(toast.success).toHaveBeenCalledWith('Reservation created', {
      description: 'Created reservation #1 for order ORD-1.',
    });
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/reservations',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('shows replay message for idempotent duplicate submission', async () => {
    const replayed: ReservationView = {
      id: 42,
      orderId: 'ORD-REPLAY',
      status: 'PENDING',
      canConfirm: true,
      canCancel: true,
      items: [{ sku: 'A100', name: 'Mouse', quantity: 1 }],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 200,
      headers: { get: (key: string) => (key === 'X-Reservation-Idempotent-Replay' ? 'true' : null) },
      json: async () => replayed,
    } as unknown as Response);

    const user = userEvent.setup();
    render(<ReservationForm inventory={inventory} onReservationCreated={vi.fn()} />);

    await user.type(screen.getByLabelText('Order ID'), 'ORD-REPLAY');
    await user.selectOptions(screen.getByLabelText('SKU 1'), 'A100');
    const qty = screen.getByLabelText('Quantity 1');
    await user.clear(qty);
    await user.type(qty, '1');
    await user.click(screen.getByRole('button', { name: 'Reserve' }));

    expect(toast.info).toHaveBeenCalledWith('Duplicate submit replayed', {
      description: 'Order already has active reservation #42. Returned existing reservation.',
    });
  });

  it('shows an error toast when the API returns INSUFFICIENT_STOCK', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({
        code: 'INSUFFICIENT_STOCK',
        message: 'Insufficient stock for A100',
        details: [],
        timestamp: new Date().toISOString(),
      }),
    } as Response);

    const user = userEvent.setup();
    render(<ReservationForm inventory={inventory} onReservationCreated={vi.fn()} />);

    await user.type(screen.getByLabelText('Order ID'), 'ORD-1');
    await user.selectOptions(screen.getByLabelText('SKU 1'), 'A100');
    const qty = screen.getByLabelText('Quantity 1');
    await user.clear(qty);
    await user.type(qty, '1');

    await user.click(screen.getByRole('button', { name: 'Reserve' }));

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('Reservation failed', {
        description: 'INSUFFICIENT_STOCK: Insufficient stock for A100',
      }),
    );
  });
});
