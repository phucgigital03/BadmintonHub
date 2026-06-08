import type { HTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** "green" = brand panel (default), "white" = light card for payment/tables. */
  tone?: 'green' | 'white';
}

export function Card({ tone = 'green', className, ...props }: CardProps) {
  return (
    <div
      className={cn(
        'rounded-xl p-4 shadow-sm',
        tone === 'green' ? 'bg-brand-panel text-white' : 'bg-white text-gray-800',
        className,
      )}
      {...props}
    />
  );
}
