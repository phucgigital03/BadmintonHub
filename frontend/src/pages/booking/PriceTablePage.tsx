import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { clubsApi, type PricingRuleResponse, type DayTypeEnum } from '../../api/clubs';
import { PageShell } from '../../components/layout/PageShell';
import { EmptyState, Spinner } from '../../components/ui/EmptyState';
import { formatVnd } from '../../lib/cn';

interface Row {
  group: string;
  span: number;
  slot: string;
  fixed: string;
  walkin: string;
}

interface SportPricing {
  sport: string;
  rows: Row[];
}

const hhmm = (t: string) => t.slice(0, 5);
const DAY_LABEL: Record<DayTypeEnum, string> = { WEEKDAY: 'T2 - T6', WEEKEND: 'T7 - CN' };

/** Pivot the multi-dimensional pricing rules into the T2-T6 / T7-CN × windows × Cố định/Vãng lai table. */
function buildRows(rules: PricingRuleResponse[]): Row[] {
  const rows: Row[] = [];
  for (const dt of ['WEEKDAY', 'WEEKEND'] as DayTypeEnum[]) {
    const dtRules = rules.filter((r) => r.dayType === dt);
    if (dtRules.length === 0) continue;
    // distinct windows (startTime-endTime), chronological (lexical sort on "HH:mm:ss")
    const windows = [...new Set(dtRules.map((r) => `${r.startTime}|${r.endTime}`))].sort();
    windows.forEach((w, i) => {
      const [start, end] = w.split('|');
      const fixed = dtRules.find((r) => `${r.startTime}|${r.endTime}` === w && r.customerType === 'FIXED');
      const walkin = dtRules.find((r) => `${r.startTime}|${r.endTime}` === w && r.customerType === 'WALK_IN');
      rows.push({
        group: i === 0 ? DAY_LABEL[dt] : '',
        span: i === 0 ? windows.length : 0,
        slot: `${hhmm(start)} - ${hhmm(end)}`,
        fixed: fixed ? formatVnd(fixed.pricePerHour) : '—',
        walkin: walkin ? formatVnd(walkin.pricePerHour) : '—',
      });
    });
  }
  return rows;
}

function SportTable({ sport, rows }: SportPricing) {
  return (
    <div className="overflow-hidden rounded-lg border border-white/20 bg-white text-gray-800">
      <div className="bg-white py-2 text-center text-lg font-bold border-b border-gray-200">{sport}</div>
      <table className="w-full text-center text-sm">
        <thead>
          <tr className="text-gray-700">
            <th className="border-b border-gray-200 px-3 py-3 font-semibold">Thứ</th>
            <th className="border-b border-l border-gray-200 px-3 py-3 font-semibold">Khung giờ</th>
            <th className="border-b border-l border-gray-200 px-3 py-3 font-semibold">Cố định</th>
            <th className="border-b border-l border-gray-200 px-3 py-3 font-semibold">Vãng lai</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              {r.span > 0 && (
                <td rowSpan={r.span} className="border-b border-gray-200 px-3 py-3 font-medium align-middle">
                  {r.group}
                </td>
              )}
              <td className="border-b border-l border-gray-200 px-3 py-3">{r.slot}</td>
              <td className="border-b border-l border-gray-200 px-3 py-3">{r.fixed}</td>
              <td className="border-b border-l border-gray-200 px-3 py-3">{r.walkin}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function PriceTablePage() {
  const navigate = useNavigate();
  const { courtId } = useParams(); // = club UUID

  const { data, isLoading, isError } = useQuery({
    queryKey: ['pricing-table', courtId],
    queryFn: async (): Promise<SportPricing[]> => {
      const [pickleball, badminton] = await Promise.all([
        clubsApi.pricing(courtId!, 'Pickleball'),
        clubsApi.pricing(courtId!, 'Badminton'),
      ]);
      return [
        { sport: 'Pickleball', rows: buildRows(pickleball) },
        { sport: 'Badminton', rows: buildRows(badminton) },
      ].filter((s) => s.rows.length > 0);
    },
    enabled: !!courtId,
    retry: 1,
  });

  return (
    <PageShell title="Xem sân và bảng giá" onBack={() => navigate(-1)} maxWidth="max-w-3xl">
      <h2 className="mb-3 text-lg font-semibold">Bảng giá sân</h2>
      {isLoading ? (
        <Spinner label="Đang tải bảng giá..." />
      ) : isError ? (
        <EmptyState icon="⚠️" title="Không tải được bảng giá">
          Kiểm tra court-service (:3002) và API Gateway (:3000), rồi thử lại.
        </EmptyState>
      ) : !data || data.length === 0 ? (
        <EmptyState title="CLB chưa cấu hình bảng giá" />
      ) : (
        <div className="space-y-6">
          {data.map((p) => (
            <SportTable key={p.sport} {...p} />
          ))}
        </div>
      )}
    </PageShell>
  );
}
