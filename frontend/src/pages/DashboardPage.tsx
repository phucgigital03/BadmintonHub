import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageShell } from '../components/layout/PageShell';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { NotificationBell } from '../components/layout/NotificationBell';
import { useAuthStore } from '../store/authStore';
import { authApi } from '../api/auth';

export default function DashboardPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { user, clearAuth, hasRole } = useAuthStore();
  const isStaff = hasRole('ROLE_STAFF') || hasRole('ROLE_ADMIN');

  const logout = async () => {
    // POST /api/auth/logout blacklists the jti; idempotent if it fails.
    try {
      await authApi.logout();
    } catch {
      /* ignore — clear locally regardless */
    }
    clearAuth();
    navigate('/');
  };

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

      <div className="mt-6 flex flex-wrap gap-3">
        <Button variant="outline" onClick={() => navigate('/my-bookings')}>
          📅 {t('nav.myBookings')}
        </Button>
        <Button variant="outline" onClick={() => navigate('/profile')}>
          👤 Hồ sơ
        </Button>
        {isStaff && (
          <Button variant="outline" onClick={() => navigate('/admin')}>
            🛠️ {t('nav.admin')}
          </Button>
        )}
        <Button variant="outline" onClick={logout}>
          {t('nav.logout')}
        </Button>
      </div>
    </PageShell>
  );
}
