import { useEffect, useState } from 'react';
import type { ReservationStatus, ReservationView } from '../../types/api';
import { ApiError, api } from '../../api/client';
import { Button } from '../ui/Button';
import { StatusBadge } from '../ui/StatusBadge';

interface Props {
  reservation: ReservationView;
  onUpdated: (r: ReservationView) => void;
  onActionError: (msg: string) => void;
}

/**
 * One reservation row with inline Confirm / Cancel actions.
 * Buttons are enabled only when the backend reports the transition is
 * legal (status === PENDING).
 *
 * While a mutation is in-flight: the clicked button shows a spinner, both
 * actions are disabled, and the status badge updates optimistically so the
 * row reflects the intended transition immediately. On failure the row
 * reverts; on success the parent replaces props with the server DTO.
 */
export function ReservationRow({ reservation: r, onUpdated, onActionError }: Props) {
  const [pendingAction, setPendingAction] = useState<'confirm' | 'cancel' | null>(null);
  const [optimistic, setOptimistic] = useState<ReservationView | null>(null);

  // Parent refetches after mutations; keep showing optimistic status until the
  // list row reflects a non-pending server state (avoid flashing back to PENDING).
  useEffect(() => {
    setOptimistic((o) => {
      if (!o) return null;
      if (r.id !== o.id) return null;
      if (r.status !== 'PENDING') return null;
      return o;
    });
  }, [r.id, r.status, r.updatedAt]);

  const display = optimistic ?? r;

  async function perform(action: 'confirm' | 'cancel') {
    const nextStatus: ReservationStatus = action === 'confirm' ? 'CONFIRMED' : 'CANCELLED';
    setOptimistic({
      ...r,
      status: nextStatus,
      canConfirm: false,
      canCancel: false,
      updatedAt: new Date().toISOString(),
    });
    setPendingAction(action);
    try {
      const updated = action === 'confirm'
        ? await api.confirmReservation(r.id)
        : await api.cancelReservation(r.id);
      onUpdated(updated);
    } catch (err) {
      setOptimistic(null);
      if (err instanceof ApiError) {
        onActionError(`${err.body.code}: ${err.body.message}`);
      } else if (err instanceof Error) {
        onActionError(err.message);
      } else {
        onActionError('Unknown error');
      }
    } finally {
      setPendingAction(null);
    }
  }

  return (
    <li
      aria-busy={pendingAction !== null}
      className="flex min-w-0 flex-col gap-3 rounded-md border border-slate-200 p-4 xl:flex-row xl:items-start xl:justify-between"
    >
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="font-mono text-sm font-semibold text-slate-900">#{display.id}</span>
          <span className="text-sm text-slate-500">·</span>
          <span className="min-w-0 break-words text-sm text-slate-700">{display.orderId}</span>
          <StatusBadge status={display.status} />
        </div>
        <ul className="text-xs text-slate-600">
          {display.items.map((item) => (
            <li key={item.sku}>
              <span className="font-mono">{item.sku}</span> × {item.quantity}{' '}
              <span className="text-slate-400">({item.name})</span>
            </li>
          ))}
        </ul>
        <p className="text-xs text-slate-400">
          Created {new Date(display.createdAt).toLocaleString()}
        </p>
      </div>

      <div className="flex w-full shrink-0 flex-wrap gap-2 xl:w-auto xl:justify-end">
        <Button
          size="sm"
          variant="primary"
          onClick={() => perform('confirm')}
          disabled={!display.canConfirm}
          loading={pendingAction === 'confirm'}
        >
          Confirm
        </Button>
        <Button
          size="sm"
          variant="danger"
          onClick={() => perform('cancel')}
          disabled={!display.canCancel}
          loading={pendingAction === 'cancel'}
        >
          Cancel
        </Button>
      </div>
    </li>
  );
}
