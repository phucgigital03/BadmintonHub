import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { AxiosError } from 'axios';
import { authApi } from '../../api/auth';
import { AuthShell } from '../../components/layout/AuthShell';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';

const schema = z
  .object({
    newPassword: z.string().min(6, 'Mật khẩu tối thiểu 6 ký tự'),
    confirm: z.string(),
  })
  .refine((d) => d.newPassword === d.confirm, {
    path: ['confirm'],
    message: 'Mật khẩu nhập lại không khớp',
  });
type Form = z.infer<typeof schema>;

export default function ResetPasswordPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';

  const { register, handleSubmit, formState: { errors } } = useForm<Form>({
    resolver: zodResolver(schema),
  });

  const mutation = useMutation({
    mutationFn: (v: Form) => authApi.resetPassword(token, v.newPassword),
    onSuccess: () => {
      toast.success(t('auth.resetSuccess'));
      navigate('/login', { replace: true });
    },
    onError: (err: AxiosError<{ message?: string }>) =>
      toast.error(err.response?.data?.message ?? 'Token không hợp lệ hoặc đã hết hạn'),
  });

  return (
    <AuthShell title={t('auth.resetTitle')}>
      {!token ? (
        <p className="text-center text-red-300">Thiếu token đặt lại mật khẩu.</p>
      ) : (
        <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
          <Input label={t('auth.newPassword')} type="password" autoComplete="new-password"
            error={errors.newPassword?.message} {...register('newPassword')} />
          <Input label={t('auth.confirmPassword')} type="password" autoComplete="new-password"
            error={errors.confirm?.message} {...register('confirm')} />
          <Button type="submit" variant="gold" size="lg" fullWidth disabled={mutation.isPending}>
            {mutation.isPending ? t('common.loading') : t('auth.resetCta')}
          </Button>
        </form>
      )}
    </AuthShell>
  );
}
