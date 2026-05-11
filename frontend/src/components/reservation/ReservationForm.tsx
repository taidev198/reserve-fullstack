import { FormEvent, useEffect, useMemo, useState } from 'react';
import type { CreateReservationLine, InventoryView, ReservationView } from '../../types/api';
import { ApiError, api } from '../../api/client';
import { useAsync } from '../../hooks/useAsync';
import { Button } from '../ui/Button';
import { toast } from 'sonner';

interface Props {
  inventory: InventoryView[];
  onReservationCreated: (r: ReservationView) => void;
}

interface DraftLine {
  sku: string;
  quantity: string;
}

const emptyLine = (): DraftLine => ({ sku: '', quantity: '1' });

/**
 * Reservation Form
 *
 * - Order ID input
 * - One or more SKU + quantity rows
 * - Client-side validation: SKU must be picked, quantity is a positive
 *   integer not exceeding the live available stock for that SKU.
 * - Submit is disabled while the draft is invalid OR the API call is
 *   in-flight, so a user can't double-submit.
 */
export function ReservationForm({ inventory, onReservationCreated }: Props) {
  const [orderId, setOrderId] = useState('');
  const [lines, setLines] = useState<DraftLine[]>([emptyLine()]);
  const [touched, setTouched] = useState(false);

  const create = useAsync(api.createReservation);

  const inventoryBySku = useMemo(
    () => Object.fromEntries(inventory.map((i) => [i.sku, i])),
    [inventory],
  );

  function updateLine(idx: number, patch: Partial<DraftLine>) {
    setLines((prev) => prev.map((l, i) => (i === idx ? { ...l, ...patch } : l)));
  }
  function addLine() {
    setLines((prev) => [...prev, emptyLine()]);
  }
  function removeLine(idx: number) {
    setLines((prev) => (prev.length === 1 ? prev : prev.filter((_, i) => i !== idx)));
  }

  const validation = useMemo(() => validate(orderId, lines, inventoryBySku), [
    orderId,
    lines,
    inventoryBySku,
  ]);

  useEffect(() => {
    if (create.status === 'error' && create.error) {
      toast.error('Reservation failed', { description: formatApiError(create.error) });
    }
  }, [create.status, create.error]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!validation.ok) return;

    const result = await create.run({
      orderId: orderId.trim(),
      items: validation.items,
    });
    if (result) {
      onReservationCreated(result.reservation);
      setOrderId('');
      setLines([emptyLine()]);
      setTouched(false);
      if (result.idempotentReplay) {
        toast.info('Duplicate submit replayed', {
          description: `Order already has active reservation #${result.reservation.id}. Returned existing reservation.`,
        });
      } else {
        toast.success('Reservation created', {
          description: `Created reservation #${result.reservation.id} for order ${result.reservation.orderId}.`,
        });
      }
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      <div>
        <label htmlFor="orderId" className="block text-sm font-medium text-slate-700">
          Order ID
        </label>
        <input
          id="orderId"
          value={orderId}
          onChange={(e) => setOrderId(e.target.value)}
          placeholder="ORD-1001"
          className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
        />
        {touched && validation.errors.orderId && (
          <p className="mt-1 text-xs text-rose-600">{validation.errors.orderId}</p>
        )}
      </div>

      <div className="space-y-2">
        <span className="block text-sm font-medium text-slate-700">Line items</span>
        {lines.map((line, idx) => {
          const inv = line.sku ? inventoryBySku[line.sku] : undefined;
          const lineError = touched ? validation.errors.lines[idx] : undefined;
          return (
            <div key={idx} className="grid grid-cols-1 gap-2 sm:grid-cols-12">
              <div className="min-w-0 sm:col-span-6">
                <select
                  aria-label={`SKU ${idx + 1}`}
                  value={line.sku}
                  onChange={(e) => updateLine(idx, { sku: e.target.value })}
                  className="block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                >
                  <option value="">Select SKU</option>
                  {inventory.map((opt) => (
                    <option key={opt.sku} value={opt.sku}>
                      {opt.sku} — {opt.name} (avail {opt.availableQuantity})
                    </option>
                  ))}
                </select>
              </div>
              <div className="min-w-0 sm:col-span-4">
                <input
                  type="number"
                  inputMode="numeric"
                  min={1}
                  step={1}
                  aria-label={`Quantity ${idx + 1}`}
                  value={line.quantity}
                  onChange={(e) => updateLine(idx, { quantity: e.target.value })}
                  className="block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                />
              </div>
              <div className="flex items-center justify-end sm:col-span-2">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => removeLine(idx)}
                  disabled={lines.length === 1}
                  aria-label={`Remove line ${idx + 1}`}
                >
                  Remove
                </Button>
              </div>
              {lineError && (
                <p className="col-span-12 -mt-1 text-xs text-rose-600">{lineError}</p>
              )}
              {inv && !lineError && (
                <p className="col-span-12 -mt-1 text-xs text-slate-500">
                  {inv.availableQuantity} available
                </p>
              )}
            </div>
          );
        })}
        <Button type="button" variant="secondary" size="sm" onClick={addLine}>
          + Add another SKU
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="submit"
          loading={create.status === 'loading'}
          disabled={touched && !validation.ok}
        >
          Reserve
        </Button>
        {touched && !validation.ok && (
          <span className="text-xs text-slate-500">Fix the errors above to continue.</span>
        )}
      </div>
    </form>
  );
}

function validate(
  orderId: string,
  lines: DraftLine[],
  invBySku: Record<string, InventoryView>,
) {
  const errors: { orderId?: string; lines: (string | undefined)[] } = { lines: [] };
  const items: CreateReservationLine[] = [];

  if (!orderId.trim()) errors.orderId = 'Order ID is required';

  for (const line of lines) {
    let err: string | undefined;
    const qty = Number(line.quantity);
    if (!line.sku) {
      err = 'Select a SKU';
    } else if (!Number.isInteger(qty) || qty <= 0) {
      err = 'Quantity must be a positive integer';
    } else if (invBySku[line.sku] && qty > invBySku[line.sku].availableQuantity) {
      err = `Only ${invBySku[line.sku].availableQuantity} available`;
    } else {
      items.push({ sku: line.sku, quantity: qty });
    }
    errors.lines.push(err);
  }

  const ok = !errors.orderId && errors.lines.every((e) => !e) && items.length > 0;
  return { ok, errors, items };
}

function formatApiError(err: Error): string {
  if (err instanceof ApiError) {
    if (err.body.details.length > 0) {
      return err.body.details.map((d) => `${d.field}: ${d.message}`).join(' · ');
    }
    return `${err.body.code}: ${err.body.message}`;
  }
  return err.message;
}
