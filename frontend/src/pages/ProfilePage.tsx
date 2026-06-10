import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { AxiosError } from 'axios';
import { usersApi } from '../api/users';
import { useAuthStore } from '../store/authStore';
import { PageShell } from '../components/layout/PageShell';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Spinner } from '../components/ui/EmptyState';

const schema = z.object({
  fullName: z.string().min(1, 'Vui lòng nhập họ tên').max(255),
  phone: z.string().max(20).optional().or(z.literal('')),
});
type Form = z.infer<typeof schema>;

export default function ProfilePage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { user, setAuth, accessToken } = useAuthStore();
  const userId = user?.id ?? '';

  // GET /api/users/{id} — own-resource (@PreAuthorize: id == authentication.name).
  const { data, isPending } = useQuery({
    queryKey: ['user', userId],
    queryFn: () => usersApi.getById(userId),
    enabled: !!userId,
  });

  const { register, handleSubmit, reset, formState: { errors } } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', phone: '' },
  });

  useEffect(() => {
    // UserResponse doesn't echo phone, so leave it blank for the user to (re)enter.
    if (data) reset({ fullName: data.fullName, phone: '' });
  }, [data, reset]);

  const mutation = useMutation({
    mutationFn: (v: Form) =>
      usersApi.update(userId, { fullName: v.fullName, phone: v.phone || undefined }),
    onSuccess: (updated) => {
      qc.setQueryData(['user', userId], updated);
      if (accessToken) setAuth(accessToken, updated); // keep the header/greeting name fresh
      toast.success('Đã cập nhật hồ sơ');
    },
    onError: (err: AxiosError<{ message?: string }>) =>
      toast.error(err.response?.data?.message ?? 'Lỗi cập nhật hồ sơ'),
  });

  return (
    <PageShell title="Hồ sơ" onBack={() => navigate('/dashboard')}>
      {isPending ? (
        <Spinner />
      ) : (
        <Card>
          <div className="mb-4">
            <p className="text-sm text-white/70">Email</p>
            <p className="font-semibold">{data?.email}</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {data?.roles?.map((r) => (
                <span key={r} className="rounded-full bg-white/15 px-2.5 py-1 text-xs">{r}</span>
              ))}
              <span
                className={`rounded-full px-2.5 py-1 text-xs ${
                  data?.isEmailVerified
                    ? 'bg-emerald-400/20 text-emerald-200'
                    : 'bg-amber-300/20 text-amber-200'
                }`}
              >
                {data?.isEmailVerified ? 'Email đã xác thực' : 'Email chưa xác thực'}
              </span>
            </div>
          </div>

          <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
            <Input label="Họ và tên" error={errors.fullName?.message} {...register('fullName')} />
            <Input label="Số điện thoại" error={errors.phone?.message} {...register('phone')} />
            <Button type="submit" variant="gold" size="lg" fullWidth disabled={mutation.isPending}>
              {mutation.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}
            </Button>
          </form>
        </Card>
      )}
    </PageShell>
  );
}
