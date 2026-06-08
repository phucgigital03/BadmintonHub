import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

type PillVariant = 'slot' | 'price' | 'skill' | 'status' | 'neutral';

const variants: Record<PillVariant, string> = {
  // filled green pill — slot count "0/16"
  slot: 'bg-emerald-500 text-white rounded-full',
  // yellow-outlined pill — price "60k/Vé"
  price: 'border border-amber-300 text-amber-200 rounded-full',
  // blue pill — skill range "1.0 → 2.5"
  skill: 'bg-blue-500 text-white rounded-md',
  status: 'bg-white/15 text-white rounded-full',
  neutral: 'bg-white/10 text-white/80 rounded-full',
};

export function Pill({
  variant = 'neutral',
  children,
  className,
}: {
  variant?: PillVariant;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center px-2.5 py-1 text-sm font-semibold',
        variants[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}
