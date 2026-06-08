import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { AxiosError } from 'axios';
import { authApi } from '../../api/auth';
import { useAuthStore } from '../../store/authStore';
import { AuthShell } from '../../components/layout/AuthShell';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import { GoogleButton } from '../../components/auth/GoogleButton';

const schema = z.object({
  email: z.string().email('Email không hợp lệ'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
});
type LoginForm = z.infer<typeof schema>;

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((s) => s.setAuth);
  const from = (location.state as { from?: string } | null)?.from ?? '/dashboard';

  const { register, handleSubmit, formState: { errors } } = useForm<LoginForm>({
    resolver: zodResolver(schema),
  });

  const mutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: (data) => {
      setAuth(data.accessToken, data.user);
      toast.success(`Xin chào ${data.user.fullName}!`);
      navigate(from, { replace: true });
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      toast.error(err.response?.data?.message ?? t('auth.loginError'));
    },
  });

  return (
    <AuthShell title={t('auth.loginTitle')}>
      <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        <Input label={t('auth.email')} type="email" autoComplete="email"
          error={errors.email?.message} {...register('email')} />
        <Input label={t('auth.password')} type="password" autoComplete="current-password"
          error={errors.password?.message} {...register('password')} />
        <Button type="submit" variant="gold" size="lg" fullWidth disabled={mutation.isPending}>
          {mutation.isPending ? t('common.loading') : t('auth.loginCta')}
        </Button>
      </form>

      <div className="my-4 flex items-center gap-3 text-white/40">
        <span className="h-px flex-1 bg-white/20" /> hoặc <span className="h-px flex-1 bg-white/20" />
      </div>
      <GoogleButton />

      <p className="mt-5 text-center text-sm text-white/80">
        {t('auth.noAccount')}{' '}
        <Link to="/register" className="font-semibold text-brand-accent hover:underline">
          {t('nav.register')}
        </Link>
      </p>
    </AuthShell>
  );
}
