import { useMemo, useState } from 'react';
import type { ReservationStatus, ReservationView } from '../../types/api';
import { ReservationRow } from './ReservationRow';
import { Banner } from '../ui/Banner';

type Filter = 'ALL' | ReservationStatus;

interface Props {
  reservations: ReservationView[];
  onUpdated: (r: ReservationView) => void;
}

const FILTERS: { id: Filter; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'PENDING', label: 'Pending' },
  { id: 'CONFIRMED', label: 'Confirmed' },
  { id: 'CANCELLED', label: 'Cancelled' },
];

export function ReservationList({ reservations, onUpdated }: Props) {
  const [filter, setFilter] = useState<Filter>('ALL');
  const [actionError, setActionError] = useState<string | null>(null);

  const filtered = useMemo(
    () => (filter === 'ALL' ? reservations : reservations.filter((r) => r.status === filter)),
    [filter, reservations],
  );

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-1">
        {FILTERS.map((f) => (
          <button
            key={f.id}
            onClick={() => setFilter(f.id)}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              filter === f.id
                ? 'bg-slate-900 text-white'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            {f.label}
          </button>
        ))}
        <span className="ml-2 text-xs text-slate-500">
          {filtered.length} of {reservations.length}
        </span>
      </div>

      {actionError && (
        <Banner tone="error" title="Action failed">
          {actionError}
        </Banner>
      )}

      {filtered.length === 0 ? (
        <p className="rounded-md border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">
          No reservations yet.
        </p>
      ) : (
        <ul className="space-y-2">
          {filtered.map((r) => (
            <ReservationRow
              key={r.id}
              reservation={r}
              onUpdated={(u) => {
                setActionError(null);
                onUpdated(u);
              }}
              onActionError={setActionError}
            />
          ))}
        </ul>
      )}
    </div>
  );
}
