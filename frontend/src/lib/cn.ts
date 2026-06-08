/** Tiny classNames joiner — filters out falsy values. */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ');
}

/** Format a VND amount, e.g. 60000 → "60.000 đ". */
export function formatVnd(amount: number): string {
  return amount.toLocaleString('vi-VN') + ' đ';
}

/** Short price label, e.g. 60000 → "60k". */
export function formatK(amount: number): string {
  return Math.round(amount / 1000) + 'k';
}
