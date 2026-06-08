import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { matchesApi } from '../../api/matches';
import { mockMatches } from '../../api/mockData';
import { useMatchSocket } from '../../hooks/useMatchSocket';
import { PageShell } from '../../components/layout/PageShell';
import { MockBanner } from '../../components/ui/MockBanner';
import { Card } from '../../components/ui/Card';
import { Pill } from '../../components/ui/Pill';
import { Button } from '../../components/ui/Button';
import { useAuthStore } from '../../store/authStore';
import { formatVnd } from '../../lib/cn';
import { hLabel, dmy } from '../../lib/format';
import type { PaymentInfo } from '../../types';

export default function MatchDetailPage() {
  const navigate = useNavigate();
  const { matchId } = useParams();
  const user = useAuthStore((s) => s.user);
  useMatchSocket(matchId);

  const query = useQuery({
    queryKey: ['match', matchId],
    queryFn: () => matchesApi.get(matchId!),
    enabled: !!matchId,
    retry: 0,
  });
  const usingMock = query.isError;
  const match = (usingMock ? mockMatches.find((m) => m.id === matchId) ?? mockMatches[0] : query.data)!;

  if (!match) return null;
  const full = match.filledSlots >= match.totalSlots;

  const join = () => {
    const payment: PaymentInfo = {
      paymentId: 'pay-' + Date.now(),
      orderCode: Math.floor(100 + Math.random() * 900),
      paymentType: 'MATCH_PLAYER',
      bankName: 'Ngân hàng Shinhan Việt Nam',
      accountNumber: '0962728894',
      accountName: 'Trần Quốc Phú',
      amount: match.pricePerPerson,
      expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      customerName: user?.fullName,
      detail: `Tham gia: ${match.title}`,
      date: match.date,
    };
    navigate('/payment', { state: payment });
  };

  return (
    <PageShell title={match.title} onBack={() => navigate(-1)} maxWidth="max-w-2xl">
      {usingMock && <MockBanner />}
      <Card>
        <div className="flex items-start justify-between">
          <h2 className="text-lg font-bold text-brand-accent">{match.courtName}</h2>
          <span className="text-sm text-white/80">{dmy(match.date)}</span>
        </div>
        <p className="mt-1 text-white/90">{hLabel(match.startTime)} - {hLabel(match.endTime)}</p>
        <div className="mt-4 flex items-center gap-3">
          <Pill variant="skill">{match.skillLevel}</Pill>
          <Pill variant="slot">{match.filledSlots}/{match.totalSlots} người</Pill>
          <Pill variant="price">{formatVnd(match.pricePerPerson)}/người</Pill>
        </div>
      </Card>

      <Button variant="gold" size="lg" fullWidth className="mt-6" disabled={full} onClick={join}>
        {full ? 'Đã đầy' : 'Tham gia & thanh toán'}
      </Button>
    </PageShell>
  );
}
