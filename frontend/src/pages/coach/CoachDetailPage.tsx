import { useNavigate, useParams } from 'react-router-dom';
import { mockCoaches } from '../../api/mockData';
import { PageShell } from '../../components/layout/PageShell';
import { MockBanner } from '../../components/ui/MockBanner';
import { Card } from '../../components/ui/Card';
import { Pill } from '../../components/ui/Pill';
import { Button } from '../../components/ui/Button';
import { useAuthStore } from '../../store/authStore';
import { formatVnd } from '../../lib/cn';
import type { PaymentInfo } from '../../types';

export default function CoachDetailPage() {
  const navigate = useNavigate();
  const { coachId } = useParams();
  const user = useAuthStore((s) => s.user);
  const coach = mockCoaches.find((c) => c.id === coachId) ?? mockCoaches[0];

  const enroll = () => {
    const payment: PaymentInfo = {
      paymentId: 'pay-' + Date.now(),
      orderCode: Math.floor(100 + Math.random() * 900),
      paymentType: 'COACH_ENROLLMENT',
      bankName: 'Ngân hàng Shinhan Việt Nam',
      accountNumber: '0962728894',
      accountName: 'Trần Quốc Phú',
      amount: coach.hourlyRate,
      expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      customerName: user?.fullName,
      detail: `Đăng ký học với ${coach.fullName}`,
      date: new Date().toISOString().slice(0, 10),
    };
    navigate('/payment', { state: payment });
  };

  return (
    <PageShell title={coach.fullName} onBack={() => navigate(-1)} maxWidth="max-w-2xl">
      <MockBanner />
      <Card>
        <div className="flex items-start justify-between">
          <h2 className="text-lg font-bold text-brand-accent">{coach.fullName}</h2>
          <span className="text-sm text-amber-200">★ {coach.rating}</span>
        </div>
        <p className="mt-1 text-white/90">{coach.specialty}</p>
        <p className="mt-2 text-sm text-white/80">{coach.bio}</p>
        <Pill variant="price" className="mt-3 self-start">{formatVnd(coach.hourlyRate)}/giờ</Pill>
      </Card>

      <Button variant="gold" size="lg" fullWidth className="mt-6" onClick={enroll}>
        Đăng ký & thanh toán
      </Button>
    </PageShell>
  );
}
