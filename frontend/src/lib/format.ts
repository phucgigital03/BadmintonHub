/** "19:00" → "19h", "10:30" → "10h30", strips a leading zero. */
export function hLabel(time: string): string {
  const [hRaw, m] = time.split(':');
  const h = String(Number(hRaw));
  return m === '00' ? `${h}h` : `${h}h${m}`;
}

/** "2026-06-04" → "04/06/2026". */
export function dmy(iso: string): string {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

/** "2026-06-04" → "04/06". */
export function dm(iso: string): string {
  const [, m, d] = iso.split('-');
  return `${d}/${m}`;
}
