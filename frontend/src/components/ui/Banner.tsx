import { ReactNode } from 'react';

type Tone = 'error' | 'info' | 'success';

const TONE_CLASS: Record<Tone, string> = {
  error: 'border-rose-200 bg-rose-50 text-rose-800',
  info: 'border-sky-200 bg-sky-50 text-sky-800',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
};

export function Banner({
  tone = 'info',
  title,
  children,
}: {
  tone?: Tone;
  title?: string;
  children: ReactNode;
}) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`rounded-md border px-4 py-3 text-sm ${TONE_CLASS[tone]}`}
    >
      {title && <div className="font-semibold">{title}</div>}
      <div>{children}</div>
    </div>
  );
}
