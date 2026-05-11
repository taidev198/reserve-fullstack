import { ReactNode } from 'react';

interface Props {
  title?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}

export function Card({ title, description, actions, children, className = '' }: Props) {
  return (
    <section
      className={`w-full min-w-0 max-w-full rounded-lg border border-slate-200 bg-white shadow-sm ${className}`}
    >
      {(title || actions) && (
        <header className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-200 px-5 py-3">
          <div className="min-w-0 flex-1">
            {title && <h2 className="text-base font-semibold text-slate-900">{title}</h2>}
            {description && <p className="mt-0.5 text-sm text-slate-500">{description}</p>}
          </div>
          {actions && <div className="shrink-0">{actions}</div>}
        </header>
      )}
      <div className="min-w-0 p-5">{children}</div>
    </section>
  );
}
