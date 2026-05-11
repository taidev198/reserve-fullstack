import type { InventoryView } from '../types/api';
import { Card } from '../components/ui/Card';
import { Banner } from '../components/ui/Banner';
import { Button } from '../components/ui/Button';
import { InventoryTable } from '../components/inventory/InventoryTable';
import type { AsyncState } from '../hooks/useAsync';

interface Props {
  state: AsyncState<InventoryView[]>;
  onRefresh: () => void;
}

export function DashboardPage({ state, onRefresh }: Props) {
  return (
    <Card
      title="Inventory Dashboard"
      description="Live view of total / reserved / available stock per SKU."
      actions={
        <Button variant="secondary" size="sm" onClick={onRefresh} loading={state.status === 'loading'}>
          Refresh
        </Button>
      }
    >
      {state.status === 'loading' && !state.data && (
        <p className="text-sm text-slate-500">Loading inventory…</p>
      )}
      {state.status === 'error' && (
        <Banner tone="error" title="Could not load inventory">
          {state.error?.message}
        </Banner>
      )}
      {state.data && <InventoryTable inventory={state.data} />}
    </Card>
  );
}
