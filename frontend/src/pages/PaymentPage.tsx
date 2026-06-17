import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { PageShell } from '../components/layout/PageShell';
import { PaymentScreen, type PaymentSummary } from '../components/payment/PaymentScreen';
import { EmptyState, Spinner } from '../components/ui/EmptyState';
import { Button } from '../components/ui/Button';
import { paymentsApi } from '../api/payments';

interface PaymentNavState {
  bookingId?: string;
  summary?: PaymentSummary;
}

export default function PaymentPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state as PaymentNavState | null) ?? {};
  const bookingId = state.bookingId;

  // initiate is idempotent: first visit opens the payment, re-entry (from "Lịch đặt của tôi") reuses it.
  const initiate = useQuery({
    queryKey: ['payment-initiate', bookingId],
    queryFn: () => paymentsApi.initiate({ paymentType: 'BOOKING', bookingId: bookingId! }),
    enabled: !!bookingId,
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    gcTime: 0,
  });

  return (
    <PageShell title={t('payment.title')} onBack={() => navigate(-1)}>
      {!bookingId ? (
        <EmptyState icon="🧾" title="Không có đơn để thanh toán">
          <Button variant="gold" className="mt-4" onClick={() => navigate('/my-bookings')}>
            Tới "Lịch đặt của tôi"
          </Button>
        </EmptyState>
      ) : initiate.isPending ? (
        <Spinner label="Đang mở thanh toán…" />
      ) : initiate.isError ? (
        <EmptyState icon="⚠️" title="Không mở được thanh toán">
          {(initiate.error as AxiosError<{ message?: string }>)?.response?.data?.message ??
            'Đơn không thể thanh toán (đã huỷ/hết hạn) hoặc dịch vụ thanh toán chưa sẵn sàng.'}
          <div>
            <Button variant="gold" className="mt-4" onClick={() => navigate('/my-bookings')}>
              Về "Lịch đặt của tôi"
            </Button>
          </div>
        </EmptyState>
      ) : (
        <PaymentScreen
          payment={initiate.data}
          summary={state.summary}
          onDone={() => navigate('/my-bookings')}
        />
      )}
    </PageShell>
  );
}
