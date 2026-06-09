import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../store/authStore';
import { Button } from '../components/ui/Button';

interface Feature {
  to: string;
  icon: string;
  title: string;
  desc: string;
}

export default function HomePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { user, clearAuth } = useAuthStore();

  const features: Feature[] = [
    { to: '/courts', icon: '🏟️', title: t('nav.courts'), desc: 'Đặt sân theo khung giờ, trực quan.' },
    { to: '/events', icon: '🎉', title: t('nav.events'), desc: 'Sự kiện giao lưu & giải đấu theo trình độ.' },
    { to: '/matches', icon: '🤝', title: t('nav.matches'), desc: 'Ghép trận, tham gia chơi cùng cộng đồng.' },
    { to: '/coaches', icon: '🏅', title: t('nav.coaches'), desc: 'Tìm huấn luyện viên, đăng ký học.' },
    { to: '/dashboard', icon: '📊', title: t('nav.dashboard'), desc: 'Quản lý lịch đặt, trận đấu, thông báo.' },
    { to: '/admin', icon: '🛠️', title: t('nav.admin'), desc: 'Duyệt thanh toán, quản lý (STAFF/ADMIN).' },
  ];

  return (
    <div className="min-h-screen">
      <header className="flex items-center justify-between px-5 py-4">
        <div className="flex items-center gap-2 text-2xl font-extrabold">
          <span className="text-brand-accent">🏸</span> {t('app.name')}
        </div>
        <div className="flex items-center gap-2">
          {user ? (
            <>
              <span className="hidden text-sm text-white/80 sm:inline">{user.fullName}</span>
              <Button variant="outline" size="sm" onClick={() => { clearAuth(); navigate('/'); }}>
                {t('nav.logout')}
              </Button>
            </>
          ) : (
            <>
              <Button variant="ghost" size="sm" onClick={() => navigate('/login')}>
                {t('nav.login')}
              </Button>
              <Button variant="gold" size="sm" onClick={() => navigate('/register')}>
                {t('nav.register')}
              </Button>
            </>
          )}
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl px-5 py-8">
        <section className="mb-10 text-center">
          <h1 className="text-4xl font-extrabold sm:text-5xl">
            Đặt sân <span className="text-brand-accent">Pickleball & Cầu lông</span>
          </h1>
          <p className="mx-auto mt-3 max-w-2xl text-white/80">
            Đặt lịch trực quan, tham gia sự kiện, ghép trận và thanh toán nhanh bằng chuyển khoản QR.
          </p>
        </section>

        <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((f) => (
            <Link
              key={f.to}
              to={f.to}
              className="group rounded-xl bg-brand-panel p-5 shadow-sm transition-transform hover:-translate-y-0.5 hover:bg-brand-panel2"
            >
              <div className="mb-2 text-3xl" aria-hidden>{f.icon}</div>
              <h3 className="text-lg font-bold text-brand-accent">{f.title}</h3>
              <p className="mt-1 text-sm text-white/80">{f.desc}</p>
              <span className="mt-3 inline-block text-white/60 transition-transform group-hover:translate-x-1">→</span>
            </Link>
          ))}
        </section>
      </main>
    </div>
  );
}
