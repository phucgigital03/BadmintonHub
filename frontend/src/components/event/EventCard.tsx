import type { EventItem } from '../../types';
import { Pill } from '../ui/Pill';
import { formatK } from '../../lib/cn';
import { hLabel, dmy } from '../../lib/format';

export function EventCard({ event, onOpen }: { event: EventItem; onOpen: () => void }) {
  return (
    <div className="rounded-xl bg-brand-panel p-4 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <h3 className="font-bold text-brand-accent">
          #{event.id}: [Xé vé] - {event.type}
        </h3>
        <span className="shrink-0 text-sm text-white/80">{dmy(event.date)}</span>
      </div>

      <p className="mt-1 text-white/90">
        {hLabel(event.startTime)} - {hLabel(event.endTime)}
        <span className="mx-1 text-white/40">|</span>
        {event.courts.join(' - ')}
      </p>

      <div className="mt-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1.5 rounded-md bg-brand-panel2 px-2 py-1 text-sm">
            <span aria-hidden>🏓</span> {event.sport}
          </span>
          <Pill variant="skill">{event.skillFrom} → {event.skillTo}</Pill>
        </div>
        <span className="text-white/60" title="Chi tiết" aria-hidden>ⓘ</span>
      </div>

      <div className="mt-3 flex items-end justify-between">
        <Pill variant="slot">{event.filledSlots}/{event.totalSlots}</Pill>
        <div className="flex items-center gap-3">
          <Pill variant="price">{formatK(event.ticketPrice)}/Vé</Pill>
          <button
            onClick={onOpen}
            className="flex h-9 w-12 items-center justify-center rounded-lg bg-brand-panel2 text-white transition-transform hover:translate-x-0.5"
            aria-label="Mở sự kiện"
          >
            →
          </button>
        </div>
      </div>
    </div>
  );
}
