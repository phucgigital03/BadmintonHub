import { forwardRef, type InputHTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

interface PhoneInputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

/** Phone field with a fixed +84 (VN) prefix, matching the alobo booking form. */
export const PhoneInput = forwardRef<HTMLInputElement, PhoneInputProps>(
  ({ label, error, className, ...props }, ref) => {
    return (
      <div className="w-full">
        {label && (
          <label className="mb-1.5 block text-sm font-semibold uppercase tracking-wide text-white/90">
            {label}
          </label>
        )}
        <div
          className={cn(
            'flex items-center overflow-hidden rounded-xl bg-white',
            'focus-within:ring-2 focus-within:ring-brand-gold',
            error && 'ring-2 ring-red-400',
          )}
        >
          <span className="flex items-center gap-1 border-r border-gray-200 px-3 py-3 text-gray-700">
            <span aria-hidden>🇻🇳</span> +84
          </span>
          <input
            ref={ref}
            inputMode="tel"
            className={cn('flex-1 px-4 py-3 text-gray-800 outline-none placeholder:text-gray-400', className)}
            {...props}
          />
        </div>
        {error && <p className="mt-1 text-sm text-red-300">{error}</p>}
      </div>
    );
  },
);
PhoneInput.displayName = 'PhoneInput';
