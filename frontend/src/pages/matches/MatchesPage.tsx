import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { matchesApi } from '../../api/matches';
import { mockMatches } from '../../api/mockData';
import { PageShell } from '../../components/layout/PageShell';
import { MockBanner } from '../../components/ui/MockBanner';
import { Spinner } from '../../components/ui/EmptyState';
import { Card } from '../../components/ui/Card';
import { Pill } from '../../components/ui/Pill';
import { Button } from '../../components/ui/Button';
import { CreateMatchModal } from '../../components/match/CreateMatchModal';
import { formatVnd } from '../../lib/cn';
import { hLabel, dmy } from '../../lib/format';

export default function MatchesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);

  const query = useQuery({ queryKey: ['matches'], queryFn: () => matchesApi.list(), retry: 0 });
  const usingMock = query.isError;
  const matches = usingMock ? mockMatches : query.data ?? [];

  return (
    <PageShell title={t('nav.matches')} onBack={() => navigate('/')}>
      {usingMock && <MockBanner />}

      <div className="mb-4 flex justify-end">
        <Button onClick={() => setCreating(true)}>+ Tạo trận</Button>
      </div>

      {query.isPending && !usingMock ? (
        <Spinner label={t('common.loading')} />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {matches.map((m) => (
            <Card key={m.id} className="cursor-pointer hover:bg-brand-panel2" onClick={() => navigate(`/matches/${m.id}`)}>
              <div className="flex items-start justify-between">
                <h3 className="font-bold text-brand-accent">{m.title}</h3>
                <span className="text-sm text-white/80">{dmy(m.date)}</span>
              </div>
              <p className="mt-1 text-white/90">{hLabel(m.startTime)} - {hLabel(m.endTime)} | {m.courtName}</p>
              <div className="mt-3 flex items-center justify-between">
                <Pill variant="skill">{m.skillLevel}</Pill>
                <div className="flex items-center gap-2">
                  <Pill variant="slot">{m.filledSlots}/{m.totalSlots}</Pill>
                  <Pill variant="price">{formatVnd(m.pricePerPerson)}</Pill>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      <CreateMatchModal open={creating} onClose={() => setCreating(false)} />
    </PageShell>
  );
}
