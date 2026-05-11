import type { InventoryView } from '../../types/api';
import { StockHealthBar } from '../ui/StockHealthBar';

interface Props {
  inventory: InventoryView[];
}

export function InventoryTable({ inventory }: Props) {
  if (inventory.length === 0) {
    return <p className="text-sm text-slate-500">No SKUs in the catalog yet.</p>;
  }

  return (
    <div className="min-w-0 overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead>
          <tr className="text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
            <th className="py-2 pr-4">SKU</th>
            <th className="py-2 pr-4">Name</th>
            <th className="py-2 pr-4 text-right">Total</th>
            <th className="py-2 pr-4 text-right">Reserved</th>
            <th className="py-2 pr-4 text-right">Available</th>
            <th className="py-2 pr-4">Health</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {inventory.map((row) => (
            <tr key={row.sku}>
              <td className="py-2 pr-4 font-mono font-medium text-slate-900">{row.sku}</td>
              <td className="py-2 pr-4 text-slate-700">{row.name}</td>
              <td className="py-2 pr-4 text-right tabular-nums">{row.totalQuantity}</td>
              <td className="py-2 pr-4 text-right tabular-nums">{row.reservedQuantity}</td>
              <td className="py-2 pr-4 text-right tabular-nums font-semibold">
                {row.availableQuantity}
              </td>
              <td className="py-2 pr-4">
                <StockHealthBar total={row.totalQuantity} available={row.availableQuantity} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
