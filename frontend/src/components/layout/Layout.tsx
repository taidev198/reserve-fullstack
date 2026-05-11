import { ReactNode } from 'react';

type Tab = 'dashboard' | 'reservations';

interface Props {
  active: Tab;
  onChange: (tab: Tab) => void;
  children: ReactNode;
}

export function Layout({ active, onChange, children }: Props) {
  const tabs: { id: Tab; label: string }[] = [
    { id: 'dashboard', label: 'Inventory' },
    { id: 'reservations', label: 'Reservations' },
  ];

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4 sm:px-6">
          <div>
            <h1 className="text-lg font-semibold text-slate-900">Warehouse Reservation</h1>
            <p className="text-xs text-slate-500">Hold, confirm, or cancel stock for incoming orders.</p>
          </div>
          <nav className="flex gap-1 rounded-lg bg-slate-100 p-1">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => onChange(tab.id)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                  active === tab.id
                    ? 'bg-white text-slate-900 shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl space-y-6 px-4 py-6 sm:px-6">{children}</main>
    </div>
  );
}
