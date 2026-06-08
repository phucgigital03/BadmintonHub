import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { AxiosError } from 'axios';
import { authApi } from '../../api/auth';
import { AuthShell } from '../../components/layout/AuthShell';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';

const schema = z.object({
  fullName: z.string().min(1, 'Vui lòng nhập họ tên'),
  email: z.string().email('Email không hợp lệ'),
  password: z.string().min(6, 'Mật khẩu tối thiểu 6 ký tự'),
  phone: z.string().optional(),
});
type RegisterForm = z.infer<typeof schema>;

export default function RegisterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const { register, handleSubmit, formState: { errors } } = useForm<RegisterForm>({
    resolver: zodResolver(schema),
  });

  const mutation = useMutation({
    mutationFn: authApi.register,
    onSuccess: () => {
      toast.success(t('auth.registerSuccess'));
      navigate('/login');
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      toast.error(err.response?.data?.message ?? 'Đăng ký thất bại');
    },
  });

  return (
    <AuthShell title={t('auth.registerTitle')}>
      <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        <Input label={t('auth.fullName')} error={errors.fullName?.message} {...register('fullName')} />
        <Input label={t('auth.email')} type="email" autoComplete="email"
          error={errors.email?.message} {...register('email')} />
        <Input label={t('auth.password')} type="password" autoComplete="new-password"
          error={errors.password?.message} {...register('password')} />
        <Input label={t('auth.phone')} type="tel" error={errors.phone?.message} {...register('phone')} />
        <Button type="submit" variant="gold" size="lg" fullWidth disabled={mutation.isPending}>
          {mutation.isPending ? t('common.loading') : t('auth.registerCta')}
        </Button>
      </form>

      <p className="mt-5 text-center text-sm text-white/80">
        {t('auth.haveAccount')}{' '}
        <Link to="/login" className="font-semibold text-brand-accent hover:underline">
          {t('nav.login')}
        </Link>
      </p>
    </AuthShell>
  );
}
