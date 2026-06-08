import type { ButtonHTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

type Variant = 'gold' | 'outline' | 'ghost' | 'danger';
type Size = 'md' | 'lg' | 'sm';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  fullWidth?: boolean;
}

const variants: Record<Variant, string> = {
  gold: 'bg-brand-gold hover:bg-brand-gold-dark text-[#3a2c08] font-bold shadow-sm',
  outline: 'border border-white/40 text-white hover:bg-white/10',
  ghost: 'text-white/80 hover:bg-white/10',
  danger: 'bg-red-500 hover:bg-red-600 text-white font-semibold',
};

const sizes: Record<Size, string> = {
  sm: 'px-3 py-1.5 text-sm rounded-lg',
  md: 'px-4 py-2.5 rounded-xl',
  lg: 'px-5 py-3.5 text-lg rounded-xl tracking-wide',
};

export function Button({
  variant = 'gold',
  size = 'md',
  fullWidth,
  className,
  ...props
}: ButtonProps) {
  return (
    <button
      className={cn(
        'inline-flex items-center justify-center gap-2 transition-colors disabled:opacity-50 disabled:cursor-not-allowed',
        variants[variant],
        sizes[size],
        fullWidth && 'w-full',
        className,
      )}
      {...props}
    />
  );
}
