import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageShell } from '../components/layout/PageShell';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { NotificationBell } from '../components/layout/NotificationBell';
import { useAuthStore } from '../store/authStore';

export default function DashboardPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { user, clearAuth } = useAuthStore();

  return (
    <PageShell title={t('nav.dashboard')} onBack={() => navigate('/')}>
      <div className="mb-3 flex justify-end">
        <NotificationBell />
      </div>
      <Card className="mb-4">
        <h2 className="text-lg font-bold text-brand-accent">Xin chào, {user?.fullName ?? 'bạn'} 👋</h2>
        <p className="mt-1 text-sm text-white/80">{user?.email}</p>
        <div className="mt-2 flex flex-wrap gap-2">
          {user?.roles?.map((r) => (
            <span key={r} className="rounded-full bg-white/15 px-2.5 py-1 text-xs">{r}</span>
          ))}
          {!user?.isEmailVerified && (
            <span className="rounded-full bg-amber-300/20 px-2.5 py-1 text-xs text-amber-200">
              Email chưa xác thực
            </span>
          )}
        </div>
      </Card>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[
          { label: t('nav.courts'), to: '/courts', icon: '🏟️' },
          { label: t('nav.events'), to: '/events', icon: '🎉' },
          { label: t('nav.matches'), to: '/matches', icon: '🤝' },
          { label: t('nav.coaches'), to: '/coaches', icon: '🏅' },
        ].map((q) => (
          <button
            key={q.to}
            onClick={() => navigate(q.to)}
            className="rounded-xl bg-brand-panel p-4 text-center transition-colors hover:bg-brand-panel2"
          >
            <div className="text-2xl" aria-hidden>{q.icon}</div>
            <div className="mt-1 text-sm font-semibold">{q.label}</div>
          </button>
        ))}
      </div>

      <Button variant="outline" className="mt-6" onClick={() => { clearAuth(); navigate('/'); }}>
        {t('nav.logout')}
      </Button>
    </PageShell>
  );
}
