import { useEffect } from 'react';
import { useNavigate, useParams, useSearchParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { clubsApi, clubSportToCourt } from '../../api/clubs';
import { PageShell } from '../../components/layout/PageShell';
import { EmptyState, Spinner } from '../../components/ui/EmptyState';
import { Button } from '../../components/ui/Button';
import { SlotGrid } from '../../components/booking/SlotGrid';
import { useBookingStore } from '../../store/bookingStore';
import { formatVnd } from '../../lib/cn';

export default function CourtDayBookingPage() {
  const navigate = useNavigate();
  const { courtId } = useParams(); // = club UUID
  const [searchParams] = useSearchParams();
  const sport = searchParams.get('sport');
  const { date, setDate, setCourt, court, selected, totalHours, totalAmount, clearSelection } =
    useBookingStore();

  // Restore the booking context (club + chosen sport) on hard-refresh from the real club query.
  const clubQuery = useQuery({
    queryKey: ['club'],
    queryFn: clubsApi.getClubWithSports,
    retry: 1,
    staleTime: 60_000,
  });
  useEffect(() => {
    if (!court && clubQuery.data) {
      const cl = clubQuery.data;
      const s = cl.sports.find((x) => x.sport === sport) ?? cl.sports[0];
      if (s) setCourt(clubSportToCourt(cl, s));
    }
  }, [court, sport, clubQuery.data, setCourt]);

  const gridQuery = useQuery({
    queryKey: ['day-grid', courtId, sport, date],
    queryFn: () => clubsApi.dayGrid(courtId!, date, sport ?? ''),
    enabled: !!courtId,
    retry: 1,
  });

  return (
    <PageShell title={`Đặt lịch ngày trực quan — ${court?.type ?? sport ?? ''}`} onBack={() => navigate(-1)}>
      <div className="mb-3 flex items-center justify-between gap-3">
        <Link to={`/courts/${courtId}/pricing`} className="font-semibold text-brand-accent hover:underline">
          Xem sân & bảng giá
        </Link>
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          className="rounded-lg bg-brand-panel2 px-3 py-2 text-white outline-none"
        />
      </div>

      <p className="mb-3 text-center text-sm text-red-300">
        Lưu ý: Nếu bạn cần đặt lịch tháng vui lòng liên hệ số điện thoại 0908 334 461 để được hỗ trợ
      </p>

      {gridQuery.isLoading ? (
        <Spinner label="Đang tải lịch sân..." />
      ) : gridQuery.isError ? (
        <EmptyState icon="⚠️" title="Không tải được lịch sân">
          Kiểm tra court-service (:3002) và API Gateway (:3000), rồi thử lại.
        </EmptyState>
      ) : (
        <SlotGrid slots={gridQuery.data ?? []} />
      )}

      {/* Sticky bottom bar */}
      <div className="sticky bottom-0 -mx-4 mt-4 flex items-center justify-between gap-4 bg-brand-header px-4 py-3">
        <div className="text-sm">
          <div>Tổng giờ: <span className="font-semibold">{totalHours()}h00</span></div>
          <div className="text-brand-accent">Tổng tiền: <span className="font-bold">{formatVnd(totalAmount())}</span></div>
        </div>
        <div className="flex gap-2">
          {selected.length > 0 && (
            <Button variant="ghost" size="sm" onClick={clearSelection}>Xoá chọn</Button>
          )}
          <Button
            variant="gold"
            disabled={selected.length === 0}
            onClick={() => navigate('/booking/confirm')}
          >
            TIẾP THEO
          </Button>
        </div>
      </div>
    </PageShell>
  );
}
