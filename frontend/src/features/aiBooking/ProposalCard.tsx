import type { AiCard } from '../../api/aiAssistant';
import { Button } from '../../components/ui/Button';
import { formatVnd } from '../../lib/cn';
import { hLabel } from '../../lib/format';

const hm = (t: string) => (t ? hLabel(t.slice(0, 5)) : t);

const SPORT_LABEL: Record<string, string> = {
  PICKLEBALL: 'Pickleball',
  BADMINTON: 'Cầu lông',
};

/**
 * A court proposal / alternative card (Sân · giờ · giá · "trong ngân sách").
 * Only the active proposal (the one the agent is currently awaiting confirmation on) shows the
 * primary "Xác nhận đặt" CTA; alternatives are display-only (choose by typing).
 */
export function ProposalCard({
  card,
  active,
  confirming,
  onConfirm,
}: {
  card: Extract<AiCard, { type: 'proposal' | 'alternative' }>;
  active: boolean;
  confirming: boolean;
  onConfirm: () => void;
}) {
  const option = card.type === 'proposal' ? card.option ?? null : card.option;
  const proposal = card.type === 'proposal' ? card.proposal : null;
  const isAlt = card.type === 'alternative';

  const price = option?.total_price ?? proposal?.total_price ?? 0;
  const sport = option?.sport ? (SPORT_LABEL[option.sport] ?? option.sport) : null;

  return (
    <div
      className={`rounded-xl border bg-white p-3 shadow-sm ${
        isAlt ? 'border-gray-200' : 'border-emerald-300'
      }`}
    >
      {isAlt && <div className="mb-1 text-[11px] font-semibold text-gray-400">Phương án thay thế</div>}

      {option ? (
        <>
          <div className="flex items-center justify-between gap-2">
            <div className="font-semibold text-gray-800">🏸 Sân {option.court_number}</div>
            {sport && (
              <span className="rounded-full bg-blue-100 px-2 py-0.5 text-[11px] font-medium text-blue-700">
                {sport}
              </span>
            )}
          </div>
          <div className="mt-1 text-sm text-gray-600">
            🕒 {hm(option.start_time)}–{hm(option.end_time)}
          </div>
          <div className="mt-0.5 text-sm font-bold text-gray-900">🪙 {formatVnd(price)}</div>
          <div className="mt-2">
            {option.within_budget ? (
              <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">
                ✓ Trong ngân sách
              </span>
            ) : (
              <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-700">
                ⚠ Vượt ngân sách
              </span>
            )}
          </div>
        </>
      ) : (
        // Resume path: GET session gives only the ProposedBooking (no CourtOption breakdown).
        <>
          <div className="text-sm text-gray-700">{proposal?.summary}</div>
          <div className="mt-1 text-sm font-bold text-gray-900">🪙 {formatVnd(price)}</div>
        </>
      )}

      {card.type === 'proposal' && active && (
        <Button variant="gold" size="sm" fullWidth className="mt-3" disabled={confirming} onClick={onConfirm}>
          {confirming ? 'Đang giữ chỗ…' : 'Xác nhận đặt'}
        </Button>
      )}
      {isAlt && <div className="mt-2 text-[11px] text-gray-400">Gõ để chọn phương án này</div>}
    </div>
  );
}
