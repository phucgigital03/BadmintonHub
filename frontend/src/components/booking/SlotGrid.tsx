import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { SlotStatus, TimeSlot } from '../../types';
import { useBookingStore } from '../../store/bookingStore';
import { cn } from '../../lib/cn';

// slotColors — matches .claude/rules/frontend.md exactly.
const statusClass: Record<SlotStatus, string> = {
  AVAILABLE: 'bg-white hover:bg-green-100 cursor-pointer',
  RESERVED: 'bg-red-200 cursor-not-allowed',
  BLOCKED: 'bg-gray-300 cursor-not-allowed',
  EVENT: 'bg-purple-200 cursor-pointer',
};

const TIMES: string[] = (() => {
  const out: string[] = [];
  for (let h = 5; h < 22; h++) {
    for (const m of [0, 30]) out.push(`${String(h).padStart(2, '0')}:${m === 0 ? '00' : '30'}`);
  }
  return out; // 05:00 .. 21:30
})();

function LegendDot({ className, label }: { className: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5 text-sm">
      <span className={cn('inline-block h-4 w-5 rounded-sm border border-black/10', className)} />
      {label}
    </span>
  );
}

export function SlotGrid({ slots }: { slots: TimeSlot[] }) {
  const { t } = useTranslation();
  const { toggleSlot, isSelected } = useBookingStore();
  const [cellW, setCellW] = useState(48);

  const courts = useMemo(() => {
    const names: string[] = [];
    for (const s of slots) if (!names.includes(s.courtName)) names.push(s.courtName);
    return names;
  }, [slots]);

  const lookup = useMemo(() => {
    const m = new Map<string, TimeSlot>();
    for (const s of slots) m.set(`${s.courtName}|${s.start}`, s);
    return m;
  }, [slots]);

  return (
    <div className="rounded-xl bg-white p-3 text-gray-800">
      {/* Legend */}
      <div className="mb-3 flex flex-wrap items-center gap-4">
        <LegendDot className="bg-white" label={t('booking.legendAvailable')} />
        <LegendDot className="bg-red-200" label={t('booking.legendReserved')} />
        <LegendDot className="bg-gray-300" label={t('booking.legendBlocked')} />
        <LegendDot className="bg-purple-200" label={t('booking.legendEvent')} />
      </div>

      {/* Grid */}
      <div className="overflow-x-auto">
        <table className="border-separate" style={{ borderSpacing: 0 }}>
          <thead>
            <tr>
              <th className="sticky left-0 z-10 bg-sky-100 px-2 py-1 text-xs font-semibold" />
              {TIMES.map((time) => (
                <th
                  key={time}
                  className="border-b border-l border-gray-200 bg-sky-100 px-1 py-1 text-[11px] font-medium text-gray-600"
                  style={{ minWidth: cellW }}
                >
                  {time}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {courts.map((courtName) => (
              <tr key={courtName}>
                <td className="sticky left-0 z-10 border-b border-gray-200 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-800">
                  {courtName}
                </td>
                {TIMES.map((time) => {
                  const slot = lookup.get(`${courtName}|${time}`);
                  const status: SlotStatus = slot?.status ?? 'AVAILABLE';
                  const selected = slot ? isSelected(slot) : false;
                  const clickable = status === 'AVAILABLE';
                  return (
                    <td
                      key={time}
                      onClick={() => slot && clickable && toggleSlot(slot)}
                      className={cn(
                        'h-9 border-b border-l border-gray-200',
                        statusClass[status],
                        selected && 'bg-green-200 ring-2 ring-inset ring-green-600',
                      )}
                      style={{ minWidth: cellW }}
                      title={`${courtName} · ${time}`}
                    />
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Zoom slider */}
      <div className="mt-3 flex items-center justify-end gap-2 text-xs text-gray-500">
        <span>Thu phóng</span>
        <input
          type="range"
          min={32}
          max={80}
          value={cellW}
          onChange={(e) => setCellW(Number(e.target.value))}
          className="accent-emerald-600"
        />
      </div>
    </div>
  );
}
