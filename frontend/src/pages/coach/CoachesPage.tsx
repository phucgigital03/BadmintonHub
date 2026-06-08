import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { coachesApi } from '../../api/coaches';
import { mockCoaches } from '../../api/mockData';
import { PageShell } from '../../components/layout/PageShell';
import { MockBanner } from '../../components/ui/MockBanner';
import { Spinner } from '../../components/ui/EmptyState';
import { Card } from '../../components/ui/Card';
import { Pill } from '../../components/ui/Pill';
import { Button } from '../../components/ui/Button';
import { formatVnd } from '../../lib/cn';

export default function CoachesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [q, setQ] = useState('');

  const query = useQuery({ queryKey: ['coaches'], queryFn: () => coachesApi.list(), retry: 0 });
  const usingMock = query.isError;
  const coaches = usingMock ? mockCoaches : query.data ?? [];

  const filtered = useMemo(
    () => coaches.filter((c) => !q || c.fullName.toLowerCase().includes(q.toLowerCase()) || c.specialty.toLowerCase().includes(q.toLowerCase())),
    [coaches, q],
  );

  return (
    <PageShell title={t('nav.coaches')} onBack={() => navigate('/')}>
      {usingMock && <MockBanner />}
      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Tìm HLV theo tên hoặc chuyên môn..."
        className="mb-4 w-full rounded-xl bg-white px-4 py-3 text-gray-800 outline-none focus:ring-2 focus:ring-brand-gold"
      />

      {query.isPending && !usingMock ? (
        <Spinner label={t('common.loading')} />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {filtered.map((c) => (
            <Card key={c.id} className="flex flex-col gap-2">
              <div className="flex items-start justify-between">
                <h3 className="text-lg font-bold text-brand-accent">{c.fullName}</h3>
                <span className="text-sm text-amber-200">★ {c.rating}</span>
              </div>
              <p className="text-sm text-white/80">{c.specialty}</p>
              <Pill variant="price" className="self-start">{formatVnd(c.hourlyRate)}/giờ</Pill>
              <Button size="sm" className="mt-1 self-start" onClick={() => navigate(`/coaches/${c.id}`)}>
                Xem & đăng ký
              </Button>
            </Card>
          ))}
        </div>
      )}
    </PageShell>
  );
}
