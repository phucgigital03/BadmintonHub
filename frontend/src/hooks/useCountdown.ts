import { useEffect, useState } from 'react';

/** Counts down to an ISO timestamp; returns "mm:ss" and an expired flag. */
export function useCountdown(targetIso: string) {
  const target = new Date(targetIso).getTime();
  const [remaining, setRemaining] = useState(() => Math.max(0, target - Date.now()));

  useEffect(() => {
    const id = setInterval(() => setRemaining(Math.max(0, target - Date.now())), 1000);
    return () => clearInterval(id);
  }, [target]);

  const totalSec = Math.floor(remaining / 1000);
  const mm = String(Math.floor(totalSec / 60)).padStart(2, '0');
  const ss = String(totalSec % 60).padStart(2, '0');
  return { mmss: `${mm}:${ss}`, expired: remaining <= 0 };
}
