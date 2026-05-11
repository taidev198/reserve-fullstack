import { useCallback, useEffect, useState } from 'react';
import { useAsync } from './hooks/useAsync';
import { api } from './api/client';
import type { PageResponse, ReservationView, InventoryView } from './types/api';
import { Card } from './components/ui/Card';
import { Button } from './components/ui/Button';
import { Banner } from './components/ui/Banner';
import { InventoryTable } from './components/inventory/InventoryTable';
import { ReservationForm } from './components/reservation/ReservationForm';
import { ReservationList } from './components/reservation/ReservationList';

export default function App() {
  const INVENTORY_PAGE_SIZE = 10;
  const RESERVATIONS_PAGE_SIZE = 5;
  const [inventoryPage, setInventoryPage] = useState(0);
  const [reservationsPage, setReservationsPage] = useState(0);
  const inventoryAsync = useAsync(api.listInventory);
  const reservationsAsync = useAsync(api.listReservations);
  const [refreshKey, setRefreshKey] = useState(0);

  const refreshAll = useCallback((invPage = inventoryPage, resPage = reservationsPage) => {
    void inventoryAsync.run(invPage, INVENTORY_PAGE_SIZE);
    void reservationsAsync.run(resPage, RESERVATIONS_PAGE_SIZE);
  }, [inventoryAsync, reservationsAsync, inventoryPage, reservationsPage]);

  useEffect(() => {
    refreshAll(inventoryPage, reservationsPage);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inventoryPage, reservationsPage]);

  const onReservationCreated = (created: ReservationView) => {
    refreshAll(inventoryPage, reservationsPage);
    setRefreshKey((prev) => prev + 1);
    void created;
  };

  const onReservationUpdated = (_updated: ReservationView) => {
    refreshAll(inventoryPage, reservationsPage);
  };

  const inventoryPageData: PageResponse<InventoryView> | undefined = inventoryAsync.data;
  const inventoryRows = inventoryPageData?.content ?? [];
  const reservationsPageData: PageResponse<ReservationView> | undefined = reservationsAsync.data;
  const reservationRows = reservationsPageData?.content ?? [];

  return (
    <main className="min-h-screen w-full min-w-0 overflow-x-hidden bg-slate-50">
      <div className="border-b border-slate-200 bg-white">
        <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6">
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">Inventory Dashboard</h1>
          <p className="mt-2 text-slate-500">Manage stock levels and reservations.</p>
        </div>
      </div>

      <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6">
        {/* lg (1024px): side-by-side; narrower viewports stack so resize reflows cleanly */}
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
          {/* min-w-0 lets grid tracks shrink below intrinsic flex/min-content width (prevents horizontal overflow) */}
          <div className="min-w-0 space-y-8 lg:col-span-2">
            <Card
              title="Inventory Summary"
              description="Stock levels and availability across all SKUs."
              actions={
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => inventoryAsync.run(inventoryPage, INVENTORY_PAGE_SIZE)}
                  loading={inventoryAsync.status === 'loading'}
                >
                  Refresh
                </Button>
              }
            >
              {inventoryAsync.status === 'loading' && !inventoryAsync.data && (
                <p className="text-sm text-slate-500">Loading inventory...</p>
              )}
              {inventoryAsync.status === 'error' && (
                <Banner tone="error" title="Could not load inventory">
                  {inventoryAsync.error?.message}
                </Banner>
              )}
              {inventoryPageData && (
                <>
                  <InventoryTable inventory={inventoryRows} />
                  <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-3">
                    <p className="text-xs text-slate-500">
                      Page {Number.isFinite(inventoryPageData.page) ? inventoryPageData.page + 1 : 1} /{' '}
                      {Number.isFinite(inventoryPageData.totalPages)
                        ? Math.max(1, inventoryPageData.totalPages)
                        : 1}{' '}
                      • {Number.isFinite(inventoryPageData.totalElements) ? inventoryPageData.totalElements : 0}{' '}
                      items
                    </p>
                    <div className="flex shrink-0 flex-wrap gap-2">
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => setInventoryPage((p) => Math.max(0, p - 1))}
                        disabled={inventoryPageData.first || inventoryAsync.status === 'loading'}
                      >
                        Previous
                      </Button>
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => setInventoryPage((p) => p + 1)}
                        disabled={inventoryPageData.last || inventoryAsync.status === 'loading'}
                      >
                        Next
                      </Button>
                    </div>
                  </div>
                </>
              )}
            </Card>

            <Card title="Create Reservation" description="Reserve inventory for an order.">
              <ReservationForm
                inventory={inventoryRows}
                onReservationCreated={onReservationCreated}
              />
            </Card>
          </div>

          <div className="min-w-0 lg:col-span-1">
            <Card
              title="Reservations"
              description="View and manage reservation states."
              actions={
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => reservationsAsync.run(reservationsPage, RESERVATIONS_PAGE_SIZE)}
                  loading={reservationsAsync.status === 'loading'}
                >
                  Refresh
                </Button>
              }
            >
              {reservationsAsync.status === 'loading' && !reservationsAsync.data && (
                <p className="text-sm text-slate-500">Loading reservations...</p>
              )}
              {reservationsAsync.status === 'error' && (
                <Banner tone="error" title="Could not load reservations">
                  {reservationsAsync.error?.message}
                </Banner>
              )}
              {reservationsPageData && (
                <>
                  <ReservationList
                    key={`reservations-${refreshKey}`}
                    reservations={reservationRows}
                    onUpdated={onReservationUpdated}
                  />
                  <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-3">
                    <p className="text-xs text-slate-500">
                      Page {Number.isFinite(reservationsPageData.page) ? reservationsPageData.page + 1 : 1} /{' '}
                      {Number.isFinite(reservationsPageData.totalPages)
                        ? Math.max(1, reservationsPageData.totalPages)
                        : 1}{' '}
                      • {Number.isFinite(reservationsPageData.totalElements) ? reservationsPageData.totalElements : 0}{' '}
                      items
                    </p>
                    <div className="flex shrink-0 flex-wrap gap-2">
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => setReservationsPage((p) => Math.max(0, p - 1))}
                        disabled={reservationsPageData.first || reservationsAsync.status === 'loading'}
                      >
                        Previous
                      </Button>
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => setReservationsPage((p) => p + 1)}
                        disabled={reservationsPageData.last || reservationsAsync.status === 'loading'}
                      >
                        Next
                      </Button>
                    </div>
                  </div>
                </>
              )}
            </Card>
          </div>
        </div>
      </div>
    </main>
  );
}
