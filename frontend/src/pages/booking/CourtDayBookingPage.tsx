import { useEffect } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { courtsApi } from '../../api/courts';
import { mockCourts, mockDayGrid } from '../../api/mockData';
import { PageShell } from '../../components/layout/PageShell';
import { MockBanner } from '../../components/ui/MockBanner';
import { Button } from '../../components/ui/Button';
import { SlotGrid } from '../../components/booking/SlotGrid';
import { useBookingStore } from '../../store/bookingStore';
import { formatVnd } from '../../lib/cn';

export default function CourtDayBookingPage() {
  const navigate = useNavigate();
  const { courtId } = useParams();
  const { date, setDate, setCourt, court, selected, totalHours, totalAmount, clearSelection } =
    useBookingStore();

  // Ensure a court is set (e.g. on hard refresh) from mock.
  useEffect(() => {
    if (!court && courtId) {
      const c = mockCourts.find((m) => m.id === courtId);
      if (c) setCourt(c);
    }
  }, [court, courtId, setCourt]);

  const query = useQuery({
    queryKey: ['day-grid', courtId, date],
    queryFn: () => courtsApi.dayGrid(courtId!, date),
    enabled: !!courtId,
    retry: 0,
  });
  const usingMock = query.isError;
  const slots = usingMock ? mockDayGrid() : query.data ?? [];

  return (
    <PageShell title="Đặt lịch ngày trực quan" onBack={() => navigate(-1)}>
      {usingMock && <MockBanner />}

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

      <SlotGrid slots={slots} />

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
