import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageShell } from '../components/layout/PageShell';
import { PaymentScreen } from '../components/payment/PaymentScreen';
import { mockPayment } from '../api/mockData';
import type { PaymentInfo } from '../types';

export default function PaymentPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  // PaymentInfo passed from the booking/event confirm step; fallback to mock.
  const payment = (location.state as PaymentInfo | null) ?? mockPayment;

  return (
    <PageShell title={t('payment.title')} onBack={() => navigate(-1)}>
      <PaymentScreen payment={payment} onDone={() => navigate('/my-bookings')} />
    </PageShell>
  );
}
