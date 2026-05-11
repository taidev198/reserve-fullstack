interface Props {
  total: number;
  available: number;
}

/**
 * Visualises stock health:
 *   green  — >= 50% available
 *   amber  — 1–49%
 *   red    — 0
 */
export function StockHealthBar({ total, available }: Props) {
  const pct = total === 0 ? 0 : Math.round((available / total) * 100);
  let tone: string;
  let label: string;
  if (available === 0) {
    tone = 'bg-rose-500';
    label = 'Out';
  } else if (pct < 50) {
    tone = 'bg-amber-500';
    label = 'Low';
  } else {
    tone = 'bg-emerald-500';
    label = 'Ample';
  }
  return (
    <div className="flex items-center gap-2">
      <div
        className="h-2 w-24 overflow-hidden rounded-full bg-slate-200"
        role="meter"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={`${available} of ${total} available`}
      >
        <div className={`h-full ${tone}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-xs font-medium text-slate-500">{label}</span>
    </div>
  );
}
