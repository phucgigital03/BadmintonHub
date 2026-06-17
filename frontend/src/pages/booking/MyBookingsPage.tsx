import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import type { AxiosError } from 'axios';
import { PageShell } from '../../components/layout/PageShell';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { EmptyState, Spinner } from '../../components/ui/EmptyState';
import { useCountdown } from '../../hooks/useCountdown';
import { formatVnd } from '../../lib/cn';
import {
  bookingsApi,
  bookingItemLabel,
  type BookingResponse,
  type BookingStatus,
} from '../../api/bookings';

const STATUS_LABEL: Record<BookingStatus, string> = {
  PENDING: 'Chờ thanh toán',
  CONFIRMED: 'Đã xác nhận',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã huỷ',
};
const STATUS_CLASS: Record<BookingStatus, string> = {
  PENDING: 'bg-amber-300/20 text-amber-200',
  CONFIRMED: 'bg-emerald-400/20 text-emerald-200',
  COMPLETED: 'bg-blue-400/20 text-blue-200',
  CANCELLED: 'bg-red-400/20 text-red-200',
};
const dmy = (d: string) => d.split('-').reverse().join('/');
const cancellable = (s: BookingStatus) => s === 'PENDING' || s === 'CONFIRMED';

function StatusBadge({ s }: { s: BookingStatus }) {
  return (
    <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${STATUS_CLASS[s]}`}>
      {STATUS_LABEL[s]}
    </span>
  );
}

/** Live "giữ trong mm:ss" for a PENDING hold (own component — hooks can't be conditional). */
function HoldCountdown({ expiresAt }: { expiresAt: string }) {
  const { mmss, expired } = useCountdown(expiresAt);
  return (
    <span className={`text-xs font-semibold ${expired ? 'text-red-300' : 'text-amber-200'}`}>
      ⏱ {expired ? 'đã hết hạn giữ chỗ' : `giữ trong ${mmss}`}
    </span>
  );
}

export default function MyBookingsPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [detailId, setDetailId] = useState<string | null>(null);

  const listQuery = useQuery({
    queryKey: ['my-bookings'],
    queryFn: () => bookingsApi.listMine(0, 50),
    retry: false,
  });

  /** Re-enter payment for a PENDING order (initiate is idempotent → reuses the active payment). */
  const goPay = (b: BookingResponse) =>
    navigate('/payment', {
      state: {
        bookingId: b.id,
        summary: {
          customerName: b.customerName,
          customerPhone: b.customerPhone,
          detail: b.items.map(bookingItemLabel).join(', '),
          date: b.bookingDate,
        },
      },
    });

  const cancelMut = useMutation({
    mutationFn: (id: string) => bookingsApi.cancel(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['my-bookings'] });
      setDetailId(null);
      toast.success('Đã huỷ đơn — các ô đã được trả lại');
    },
    onError: (err: AxiosError<{ message?: string }>) =>
      toast.error(err.response?.data?.message ?? 'Huỷ đơn thất bại'),
  });

  return (
    <PageShell title="Lịch đặt của tôi" onBack={() => navigate('/dashboard')}>
      {listQuery.isPending ? (
        <Spinner label="Đang tải đơn đặt…" />
      ) : listQuery.isError ? (
        <EmptyState icon="⚠️" title="Không tải được lịch đặt">
          Vui lòng đăng nhập lại hoặc thử lại sau.
        </EmptyState>
      ) : listQuery.data.content.length === 0 ? (
        <EmptyState icon="📭" title="Chưa có đơn đặt nào">
          <Button variant="gold" className="mt-4" onClick={() => navigate('/courts')}>
            Đặt sân ngay
          </Button>
        </EmptyState>
      ) : (
        <div className="space-y-3">
          {listQuery.data.content.map((b) => (
            <Card key={b.id}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-semibold">
                    Ngày {dmy(b.bookingDate)}{' '}
                    <span className="text-white/50">· #{b.id.slice(0, 8)}</span>
                  </p>
                  <p className="mt-0.5 truncate text-sm text-white/75">
                    {b.items.length > 0
                      ? `${b.items.length} ô · ${[...new Set(b.items.map((i) => i.courtName))].join(', ')}`
                      : '— (đã trả ô)'}
                  </p>
                  <p className="mt-0.5 text-sm">
                    Tổng: <span className="font-bold text-brand-accent">{formatVnd(b.totalPrice)}</span>
                  </p>
                  {b.status === 'PENDING' && b.holdExpiresAt && (
                    <div className="mt-1">
                      <HoldCountdown expiresAt={b.holdExpiresAt} />
                    </div>
                  )}
                </div>
                <StatusBadge s={b.status} />
              </div>
              <div className="mt-3 flex gap-2">
                <Button variant="outline" size="sm" onClick={() => setDetailId(b.id)}>
                  Chi tiết
                </Button>
                {b.status === 'PENDING' && (
                  <Button variant="gold" size="sm" onClick={() => goPay(b)}>
                    Thanh toán
                  </Button>
                )}
                {cancellable(b.status) && (
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={cancelMut.isPending}
                    onClick={() => {
                      if (window.confirm('Huỷ đơn đặt sân này?')) cancelMut.mutate(b.id);
                    }}
                  >
                    Huỷ
                  </Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      <BookingDetailModal
        id={detailId}
        onClose={() => setDetailId(null)}
        onCancel={(id) => {
          if (window.confirm('Huỷ đơn đặt sân này?')) cancelMut.mutate(id);
        }}
        cancelling={cancelMut.isPending}
      />
    </PageShell>
  );
}

/** Detail via GET /api/bookings/{id} (exercises the single-booking endpoint). */
function BookingDetailModal({
  id,
  onClose,
  onCancel,
  cancelling,
}: {
  id: string | null;
  onClose: () => void;
  onCancel: (id: string) => void;
  cancelling: boolean;
}) {
  const detailQuery = useQuery({
    queryKey: ['booking', id],
    queryFn: () => bookingsApi.getById(id as string),
    enabled: !!id,
  });
  const b: BookingResponse | undefined = detailQuery.data;

  return (
    <Modal open={!!id} onClose={onClose} title="Chi tiết đơn đặt">
      {detailQuery.isPending ? (
        <p className="py-8 text-center text-gray-500">Đang tải…</p>
      ) : detailQuery.isError || !b ? (
        <p className="py-8 text-center text-red-500">Không tải được đơn.</p>
      ) : (
        <div className="space-y-3 text-sm">
          <div className="flex items-center justify-between">
            <span className="text-gray-500">Mã đơn</span>
            <span className="font-mono">#{b.id.slice(0, 8)}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-gray-500">Trạng thái</span>
            <span className="font-semibold">{STATUS_LABEL[b.status]}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-gray-500">Ngày</span>
            <span>{dmy(b.bookingDate)}</span>
          </div>
          <div>
            <p className="mb-1 text-gray-500">Khung giờ</p>
            {b.items.length > 0 ? (
              <ul className="space-y-1">
                {b.items.map((it) => (
                  <li key={it.id} className="flex justify-between">
                    <span>{bookingItemLabel(it)}</span>
                    <span className="font-medium">{formatVnd(it.price)}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-gray-400">— (đã trả ô về trạng thái trống)</p>
            )}
          </div>
          <div className="flex items-center justify-between border-t border-gray-200 pt-2">
            <span className="text-gray-500">Tổng tiền</span>
            <span className="font-bold">{formatVnd(b.totalPrice)}</span>
          </div>
          {b.refundAmount != null && b.status === 'CANCELLED' && (
            <div className="flex items-center justify-between">
              <span className="text-gray-500">Hoàn tiền</span>
              <span className="font-semibold">{formatVnd(b.refundAmount)}</span>
            </div>
          )}
          {cancellable(b.status) && (
            <Button
              variant="gold"
              fullWidth
              className="mt-2"
              disabled={cancelling}
              onClick={() => onCancel(b.id)}
            >
              {cancelling ? 'Đang huỷ…' : 'Huỷ đơn'}
            </Button>
          )}
        </div>
      )}
    </Modal>
  );
}
