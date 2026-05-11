import type { ReservationStatus } from '../../types/api';

const STYLES: Record<ReservationStatus, string> = {
  PENDING: 'bg-amber-100 text-amber-800 ring-amber-200',
  CONFIRMED: 'bg-emerald-100 text-emerald-800 ring-emerald-200',
  CANCELLED: 'bg-slate-100 text-slate-600 ring-slate-200',
};

export function StatusBadge({ status }: { status: ReservationStatus }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${STYLES[status]}`}
    >
      {status}
    </span>
  );
}
