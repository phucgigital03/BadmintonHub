import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { eventsApi } from '../../api/events';
import { mockEvents } from '../../api/mockData';
import { PageShell } from '../../components/layout/PageShell';
import { MockBanner } from '../../components/ui/MockBanner';
import { Spinner, EmptyState } from '../../components/ui/EmptyState';
import { EventCard } from '../../components/event/EventCard';

export default function EventsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const query = useQuery({ queryKey: ['events'], queryFn: () => eventsApi.list(), retry: 0 });
  const usingMock = query.isError;
  const events = usingMock ? mockEvents : query.data ?? [];

  return (
    <PageShell title="Đặt lịch sự kiện" onBack={() => navigate('/')}>
      {usingMock && <MockBanner />}

      <div className="mb-4 flex justify-end">
        <span className="inline-flex items-center gap-2 rounded-lg bg-brand-panel2 px-4 py-2 text-sm">
          04/06 - 10/06 <span aria-hidden>📅</span>
        </span>
      </div>

      {query.isPending && !usingMock ? (
        <Spinner label={t('common.loading')} />
      ) : events.length === 0 ? (
        <EmptyState title="Chưa có sự kiện nào" />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {events.map((e) => (
            <EventCard key={e.id} event={e} onOpen={() => navigate(`/events/${e.id}`)} />
          ))}
        </div>
      )}
    </PageShell>
  );
}
