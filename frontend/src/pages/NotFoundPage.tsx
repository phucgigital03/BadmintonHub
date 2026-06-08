import { useNavigate } from 'react-router-dom';
import { PageShell } from '../components/layout/PageShell';
import { Button } from '../components/ui/Button';

export default function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <PageShell title="BadmintonHub" onBack={() => navigate('/')}>
      <div className="flex flex-col items-center justify-center py-24 text-center">
        <div className="mb-4 text-5xl" aria-hidden>🚧</div>
        <h2 className="text-2xl font-bold">Trang đang được xây dựng</h2>
        <p className="mt-2 text-white/70">Tính năng này sẽ sớm có mặt.</p>
        <Button className="mt-6" onClick={() => navigate('/')}>Về trang chủ</Button>
      </div>
    </PageShell>
  );
}
