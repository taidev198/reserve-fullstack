import type { InventoryView, ReservationView } from '../types/api';
import { Card } from '../components/ui/Card';
import { Banner } from '../components/ui/Banner';
import { Button } from '../components/ui/Button';
import { ReservationForm } from '../components/reservation/ReservationForm';
import { ReservationList } from '../components/reservation/ReservationList';
import type { AsyncState } from '../hooks/useAsync';

interface Props {
  inventory: InventoryView[];
  reservationsState: AsyncState<ReservationView[]>;
  onCreated: (r: ReservationView) => void;
  onUpdated: (r: ReservationView) => void;
  onRefresh: () => void;
}

export function ReservationsPage({
  inventory,
  reservationsState,
  onCreated,
  onUpdated,
  onRefresh,
}: Props) {
  return (
    <div className="grid gap-6 lg:grid-cols-5">
      <div className="min-w-0 lg:col-span-2">
        <Card title="New reservation" description="Hold stock for an incoming order.">
          <ReservationForm inventory={inventory} onReservationCreated={onCreated} />
        </Card>
      </div>
      <div className="min-w-0 lg:col-span-3">
        <Card
          title="Reservations"
          description="Confirm or cancel — buttons reflect what the backend permits."
          actions={
            <Button
              variant="secondary"
              size="sm"
              onClick={onRefresh}
              loading={reservationsState.status === 'loading'}
            >
              Refresh
            </Button>
          }
        >
          {reservationsState.status === 'loading' && !reservationsState.data && (
            <p className="text-sm text-slate-500">Loading reservations…</p>
          )}
          {reservationsState.status === 'error' && (
            <Banner tone="error" title="Could not load reservations">
              {reservationsState.error?.message}
            </Banner>
          )}
          {reservationsState.data && (
            <ReservationList reservations={reservationsState.data} onUpdated={onUpdated} />
          )}
        </Card>
      </div>
    </div>
  );
}
