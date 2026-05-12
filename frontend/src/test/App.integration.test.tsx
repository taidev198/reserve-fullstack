import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import type { InventoryView, PageResponse, ReservationView } from '../types/api';

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    info: vi.fn(),
    error: vi.fn(),
  },
}));

const iso = () => new Date().toISOString();

const inventoryRows: InventoryView[] = [
  { sku: 'A100', name: 'Wireless Mouse', totalQuantity: 100, reservedQuantity: 0, availableQuantity: 100 },
  { sku: 'B200', name: 'Mechanical Keyboard', totalQuantity: 50, reservedQuantity: 0, availableQuantity: 50 },
  { sku: 'C300', name: '27" Monitor', totalQuantity: 20, reservedQuantity: 0, availableQuantity: 20 },
  { sku: 'D400', name: 'USB-C Hub', totalQuantity: 10, reservedQuantity: 0, availableQuantity: 10 },
  { sku: 'E500', name: 'Noise-Cancelling Headphones', totalQuantity: 5, reservedQuantity: 0, availableQuantity: 5 },
];

const inventoryPage: PageResponse<InventoryView> = {
  content: inventoryRows,
  page: 0,
  size: 10,
  totalElements: 5,
  totalPages: 1,
  first: true,
  last: true,
};

function envelope<T>(statusCode: number, message: string, data: T) {
  return { statusCode, message, data };
}

function jsonResponse(status: number, jsonBody: unknown, extraHeaders: Record<string, string> = {}): Response {
  const lower = new Map(
    Object.entries({ 'content-type': 'application/json', ...extraHeaders }).map(([k, v]) => [
      k.toLowerCase(),
      v,
    ]),
  );
  return {
    ok: status < 400,
    status,
    headers: {
      get: (name: string) => lower.get(name.toLowerCase()) ?? null,
    },
    json: async () => jsonBody,
  } as Response;
}

/** Mutable server state exercised through `globalThis.fetch` for dashboard integration tests. */
let serverReservations: ReservationView[] = [];

describe('App integration', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    serverReservations = [];
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      const method = init?.method ?? 'GET';

      if (url.includes('/api/inventory')) {
        return jsonResponse(200, envelope(200, 'OK', inventoryPage));
      }

      if (url.includes('/api/reservations') && method === 'GET') {
        const page: PageResponse<ReservationView> = {
          content: [...serverReservations],
          page: 0,
          size: 5,
          totalElements: serverReservations.length,
          totalPages: serverReservations.length === 0 ? 0 : 1,
          first: true,
          last: true,
        };
        return jsonResponse(200, envelope(200, 'OK', page));
      }

      if (url.includes('/api/reservations') && method === 'POST' && !url.includes('/confirm') && !url.includes('/cancel')) {
        const body = JSON.parse(init?.body as string) as { orderId: string; items: { sku: string; quantity: number }[] };
        const created: ReservationView = {
          id: 1,
          orderId: body.orderId,
          status: 'PENDING',
          canConfirm: true,
          canCancel: true,
          items: body.items.map((i) => ({
            sku: i.sku,
            name: inventoryRows.find((r) => r.sku === i.sku)?.name ?? i.sku,
            quantity: i.quantity,
          })),
          createdAt: iso(),
          updatedAt: iso(),
        };
        serverReservations = [created];
        return jsonResponse(201, envelope(201, 'Created', created), {
          'X-Reservation-Idempotent-Replay': 'false',
        });
      }

      if (url.includes('/confirm') && method === 'POST') {
        const m = url.match(/\/reservations\/(\d+)\/confirm/);
        const id = m ? Number(m[1]) : 0;
        const current = serverReservations.find((r) => r.id === id) ?? serverReservations[0];
        const updated: ReservationView = {
          ...current,
          id,
          status: 'CONFIRMED',
          canConfirm: false,
          canCancel: false,
          updatedAt: iso(),
        };
        serverReservations = [updated];
        return jsonResponse(200, envelope(200, 'OK', updated));
      }

      throw new Error(`Unhandled fetch in App integration test: ${method} ${url}`);
    });
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('loads inventory and reservations from the API on mount', async () => {
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Inventory Dashboard' })).toBeInTheDocument();
    expect(await screen.findByText('Wireless Mouse')).toBeInTheDocument();
    expect(await screen.findByText('No reservations yet.')).toBeInTheDocument();

    expect(globalThis.fetch).toHaveBeenCalled();
    const urls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.map((c) =>
      typeof c[0] === 'string' ? c[0] : String(c[0]),
    );
    expect(urls.some((u) => u.includes('/api/inventory'))).toBe(true);
    expect(urls.some((u) => u.includes('/api/reservations'))).toBe(true);
  });

  it('creates a reservation via the form and shows it in the list after refresh', async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findByText('Wireless Mouse');

    await user.type(screen.getByLabelText('Order ID'), 'ORD-FE-INT-1');
    await user.selectOptions(screen.getByLabelText('SKU 1'), 'A100');
    await user.click(screen.getByRole('button', { name: 'Reserve' }));

    await waitFor(() => {
      expect(screen.getByText('ORD-FE-INT-1')).toBeInTheDocument();
    });
    expect(screen.getByText('#1')).toBeInTheDocument();

    const postCalls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.filter(
      (c) => (c[1] as RequestInit | undefined)?.method === 'POST' && String(c[0]).includes('/api/reservations'),
    );
    expect(postCalls.length).toBeGreaterThanOrEqual(1);
    const [, init] = postCalls[postCalls.length - 1];
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      orderId: 'ORD-FE-INT-1',
      items: [{ sku: 'A100', quantity: 1 }],
    });
  });

  it('confirms a reservation from the list and updates status', async () => {
    serverReservations = [
      {
        id: 42,
        orderId: 'ORD-PRESEED',
        status: 'PENDING',
        canConfirm: true,
        canCancel: true,
        items: [{ sku: 'B200', name: 'Mechanical Keyboard', quantity: 1 }],
        createdAt: iso(),
        updatedAt: iso(),
      },
    ];

    const user = userEvent.setup();
    render(<App />);

    await screen.findByText('ORD-PRESEED');
    await user.click(screen.getByRole('button', { name: 'Confirm' }));

    await waitFor(() => {
      expect(screen.getByText('CONFIRMED')).toBeInTheDocument();
    });
  });
});
