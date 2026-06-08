import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { mockEvents } from '../../api/mockData';
import { PageShell } from '../../components/layout/PageShell';
import { MockBanner } from '../../components/ui/MockBanner';
import { Card } from '../../components/ui/Card';
import { Pill } from '../../components/ui/Pill';
import { Button } from '../../components/ui/Button';
import { useAuthStore } from '../../store/authStore';
import { formatVnd } from '../../lib/cn';
import { hLabel, dmy } from '../../lib/format';
import type { PaymentInfo } from '../../types';

export default function EventDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { eventId } = useParams();
  const user = useAuthStore((s) => s.user);

  // Backend not ready → resolve from mock.
  const event = mockEvents.find((e) => String(e.id) === eventId) ?? mockEvents[0];

  const buyTicket = () => {
    const payment: PaymentInfo = {
      paymentId: 'pay-' + Date.now(),
      orderCode: event.id,
      paymentType: 'EVENT_TICKET',
      bankName: 'Ngân hàng Shinhan Việt Nam',
      accountNumber: '0962728894',
      accountName: 'Trần Quốc Phú',
      amount: event.ticketPrice,
      expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      customerName: user?.fullName,
      detail: `Sự kiện #${event.id} ${event.sport} ${hLabel(event.startTime)}-${hLabel(event.endTime)}`,
      date: event.date,
    };
    navigate('/payment', { state: payment });
  };

  return (
    <PageShell title={`Sự kiện #${event.id}`} onBack={() => navigate(-1)} maxWidth="max-w-2xl">
      <MockBanner />
      <Card>
        <div className="flex items-start justify-between">
          <h2 className="text-lg font-bold text-brand-accent">[Xé vé] - {event.type}</h2>
          <span className="text-sm text-white/80">{dmy(event.date)}</span>
        </div>
        <p className="mt-1 text-white/90">
          {hLabel(event.startTime)} - {hLabel(event.endTime)} | {event.courts.join(' - ')}
        </p>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Pill variant="skill">{event.sport} {event.skillFrom} → {event.skillTo}</Pill>
          <Pill variant="slot">{event.filledSlots}/{event.totalSlots}</Pill>
          <Pill variant="price">{formatVnd(event.ticketPrice)}/Vé</Pill>
        </div>
      </Card>

      <Button variant="gold" size="lg" fullWidth className="mt-6" onClick={buyTicket}>
        {t('payment.confirmBooking')}
      </Button>
    </PageShell>
  );
}
