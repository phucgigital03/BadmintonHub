import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate } from 'react-router-dom';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { useAuthStore } from '../../store/authStore';
import type { PaymentInfo } from '../../types';

const schema = z.object({
  title: z.string().min(1, 'Nhập tên trận'),
  date: z.string().min(1, 'Chọn ngày'),
  totalSlots: z
    .number()
    .int()
    .min(2, 'Tối thiểu 2')
    .max(16, 'Tối đa 16')
    .refine((n) => n % 2 === 0, 'Phải là số chẵn'),
  pricePerPerson: z.number().min(0, 'Giá không hợp lệ'),
});
type CreateMatchForm = z.infer<typeof schema>;

export function CreateMatchModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const { register, handleSubmit, formState: { errors } } = useForm<CreateMatchForm>({
    resolver: zodResolver(schema),
    defaultValues: { totalSlots: 4, pricePerPerson: 50000 },
  });

  const submit = (v: CreateMatchForm) => {
    // Host pays full court price upfront (Prepay model). Mock court price.
    const courtPrice = v.totalSlots * v.pricePerPerson;
    const payment: PaymentInfo = {
      paymentId: 'pay-' + Date.now(),
      orderCode: Math.floor(100 + Math.random() * 900),
      paymentType: 'MATCH_HOST',
      bankName: 'Ngân hàng Shinhan Việt Nam',
      accountNumber: '0962728894',
      accountName: 'Trần Quốc Phú',
      amount: courtPrice,
      expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      customerName: user?.fullName,
      detail: `Tạo trận: ${v.title} (${v.totalSlots} người)`,
      date: v.date,
    };
    onClose();
    navigate('/payment', { state: payment });
  };

  return (
    <Modal open={open} onClose={onClose} title="Tạo trận">
      <form onSubmit={handleSubmit(submit)} className="space-y-4 text-gray-800">
        <div>
          <label className="mb-1 block text-sm font-semibold">Tên trận</label>
          <input className="w-full rounded-xl border border-gray-300 px-4 py-3 outline-none focus:ring-2 focus:ring-emerald-500" {...register('title')} />
          {errors.title && <p className="mt-1 text-sm text-red-500">{errors.title.message}</p>}
        </div>
        <div>
          <label className="mb-1 block text-sm font-semibold">Ngày</label>
          <input type="date" className="w-full rounded-xl border border-gray-300 px-4 py-3 outline-none focus:ring-2 focus:ring-emerald-500" {...register('date')} />
          {errors.date && <p className="mt-1 text-sm text-red-500">{errors.date.message}</p>}
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-sm font-semibold">Số người (chẵn)</label>
            <input type="number" className="w-full rounded-xl border border-gray-300 px-4 py-3 outline-none focus:ring-2 focus:ring-emerald-500" {...register('totalSlots', { valueAsNumber: true })} />
            {errors.totalSlots && <p className="mt-1 text-sm text-red-500">{errors.totalSlots.message}</p>}
          </div>
          <div>
            <label className="mb-1 block text-sm font-semibold">Giá / người</label>
            <input type="number" className="w-full rounded-xl border border-gray-300 px-4 py-3 outline-none focus:ring-2 focus:ring-emerald-500" {...register('pricePerPerson', { valueAsNumber: true })} />
            {errors.pricePerPerson && <p className="mt-1 text-sm text-red-500">{errors.pricePerPerson.message}</p>}
          </div>
        </div>
        <Button type="submit" variant="gold" size="lg" fullWidth>Tạo & thanh toán (Host)</Button>
      </form>
    </Modal>
  );
}
