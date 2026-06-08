import { forwardRef, type InputHTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, className, ...props }, ref) => {
    return (
      <div className="w-full">
        {label && (
          <label className="mb-1.5 block text-sm font-semibold uppercase tracking-wide text-white/90">
            {label}
          </label>
        )}
        <input
          ref={ref}
          className={cn(
            'w-full rounded-xl bg-white px-4 py-3 text-gray-800 outline-none placeholder:text-gray-400',
            'focus:ring-2 focus:ring-brand-gold',
            error && 'ring-2 ring-red-400',
            className,
          )}
          {...props}
        />
        {error && <p className="mt-1 text-sm text-red-300">{error}</p>}
      </div>
    );
  },
);
Input.displayName = 'Input';
