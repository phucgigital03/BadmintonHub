import type { ReactNode } from 'react';

export function EmptyState({ icon = '📭', title, children }: { icon?: string; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl bg-white/5 px-6 py-16 text-center text-white/70">
      <div className="mb-3 text-4xl" aria-hidden>{icon}</div>
      <p className="text-lg font-semibold text-white/90">{title}</p>
      {children && <p className="mt-1 text-sm">{children}</p>}
    </div>
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-white/70">
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-white/30 border-t-brand-gold" />
      {label && <p className="mt-3 text-sm">{label}</p>}
    </div>
  );
}
