import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { authApi } from '../../api/auth';
import { AuthShell } from '../../components/layout/AuthShell';
import { Spinner } from '../../components/ui/EmptyState';

export default function VerifyEmailPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';

  const { isPending, isError, isSuccess } = useQuery({
    queryKey: ['verify-email', token],
    queryFn: () => authApi.verifyEmail(token),
    enabled: !!token,
    retry: false,
  });

  return (
    <AuthShell title={t('auth.verifyTitle')}>
      {!token && <p className="text-center text-red-300">Thiếu token xác thực.</p>}
      {token && isPending && <Spinner label={t('common.loading')} />}
      {isSuccess && (
        <div className="text-center">
          <div className="mb-3 text-4xl" aria-hidden>✅</div>
          <p className="text-white/90">{t('auth.verifySuccess')}</p>
          <Link to="/login" className="mt-4 inline-block font-semibold text-brand-accent hover:underline">
            {t('nav.login')} →
          </Link>
        </div>
      )}
      {isError && (
        <div className="text-center">
          <div className="mb-3 text-4xl" aria-hidden>⚠️</div>
          <p className="text-red-300">Token không hợp lệ hoặc đã hết hạn.</p>
        </div>
      )}
    </AuthShell>
  );
}
