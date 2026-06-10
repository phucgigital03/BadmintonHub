import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { authApi } from '../../api/auth';
import { AuthShell } from '../../components/layout/AuthShell';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';

const schema = z.object({ email: z.string().email('Email không hợp lệ') });
type Form = z.infer<typeof schema>;

export default function ForgotPasswordPage() {
  const { t } = useTranslation();
  const { register, handleSubmit, formState: { errors } } = useForm<Form>({
    resolver: zodResolver(schema),
  });

  const mutation = useMutation({
    mutationFn: (v: Form) => authApi.forgotPassword(v.email),
    // Backend always responds 200 (no user enumeration) — show the same message regardless.
    onSuccess: () => toast.success(t('auth.forgotSent')),
    onError: () => toast.success(t('auth.forgotSent')),
  });

  return (
    <AuthShell title={t('auth.forgotTitle')}>
      {mutation.isSuccess ? (
        <div className="text-center">
          <div className="mb-3 text-4xl" aria-hidden>📧</div>
          <p className="text-white/90">{t('auth.forgotSent')}</p>
          <Link to="/login" className="mt-4 inline-block font-semibold text-brand-accent hover:underline">
            {t('nav.login')} →
          </Link>
        </div>
      ) : (
        <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
          <p className="text-sm text-white/80">{t('auth.forgotDesc')}</p>
          <Input label={t('auth.email')} type="email" autoComplete="email"
            error={errors.email?.message} {...register('email')} />
          <Button type="submit" variant="gold" size="lg" fullWidth disabled={mutation.isPending}>
            {mutation.isPending ? t('common.loading') : t('auth.forgotCta')}
          </Button>
          <p className="text-center text-sm text-white/80">
            <Link to="/login" className="font-semibold text-brand-accent hover:underline">
              ← {t('nav.login')}
            </Link>
          </p>
        </form>
      )}
    </AuthShell>
  );
}
