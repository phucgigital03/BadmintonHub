import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { PageShell } from '../../components/layout/PageShell';
import { Card } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { PhoneInput } from '../../components/ui/PhoneInput';
import { Button } from '../../components/ui/Button';
import { useBookingStore } from '../../store/bookingStore';
import { useAuthStore } from '../../store/authStore';
import { formatVnd } from '../../lib/cn';
import type { PaymentInfo, TimeSlot } from '../../types';

const hm = (s: string) => s.replace(':', 'h');

interface BookedRange {
  courtId: string;
  courtName: string;
  start: string;
  end: string;
  price: number;
}

/** Collapse contiguous 30-min cells of the same court into one range; price = sum of real cell prices. */
function mergeRanges(slots: TimeSlot[], fallbackCellPrice: number): BookedRange[] {
  const sorted = slots
    .slice()
    .sort(
      (a, b) =>
        a.courtName.localeCompare(b.courtName, 'vi', { numeric: true }) || a.start.localeCompare(b.start),
    );
  const out: BookedRange[] = [];
  for (const s of sorted) {
    const price = typeof s.price === 'number' ? s.price : fallbackCellPrice;
    const last = out[out.length - 1];
    if (last && last.courtId === s.courtId && last.end === s.start) {
      last.end = s.end; // extend the range
      last.price += price;
    } else {
      out.push({ courtId: s.courtId, courtName: s.courtName, start: s.start, end: s.end, price });
    }
  }
  return out;
}

export default function BookingConfirmPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const { court, date, selected, totalHours, totalAmount, setCustomer } = useBookingStore();

  const [name, setName] = useState(user?.fullName ?? '');
  const [phone, setPhone] = useState('');
  const [note, setNote] = useState('');

  if (!court || selected.length === 0) {
    return (
      <PageShell title="Đặt lịch ngày trực quan" onBack={() => navigate('/courts')}>
        <p className="py-16 text-center text-white/70">Chưa có lịch nào được chọn.</p>
      </PageShell>
    );
  }

  const ranges = mergeRanges(selected, 0.5 * court.pricePerHour);

  const handleConfirm = () => {
    if (!name.trim() || !phone.trim()) {
      toast.error('Vui lòng nhập tên và số điện thoại');
      return;
    }
    setCustomer(name, phone, note);
    const payment: PaymentInfo = {
      paymentId: 'pay-' + Date.now(),
      orderCode: Math.floor(100 + Math.random() * 900),
      paymentType: 'BOOKING',
      bankName: 'Ngân hàng Shinhan Việt Nam',
      accountNumber: '0962728894',
      accountName: 'Trần Quốc Phú',
      amount: totalAmount(),
      expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      customerName: name,
      customerPhone: phone,
      detail: ranges.map((r) => `${r.courtName} ${hm(r.start)}-${hm(r.end)}`).join(', '),
      date,
    };
    navigate('/payment', { state: payment });
  };

  return (
    <PageShell title="Đặt lịch ngày trực quan" onBack={() => navigate(-1)} maxWidth="max-w-3xl">
      <Card className="mb-4">
        <h2 className="mb-1 font-bold text-brand-accent">📑 {t('booking.courtInfo')}</h2>
        <p>Tên CLB: <span className="font-semibold">{court.club}</span></p>
        <p className="text-white/80">Địa chỉ: {court.address}</p>
      </Card>

      <Card className="mb-4">
        <h2 className="mb-2 font-bold text-brand-accent">🎟️ {t('booking.bookingInfo')}</h2>
        <p>Ngày: <span className="font-semibold">{date.split('-').reverse().join('/')}</span></p>
        <ul className="my-1 space-y-0.5">
          {ranges.map((r, i) => (
            <li key={i}>
              - {r.courtName}: {hm(r.start)} - {hm(r.end)} |{' '}
              <span className="text-brand-accent">{formatVnd(r.price)}</span>
            </li>
          ))}
        </ul>
        <p>Đối tượng: <span className="font-semibold">{court.type}</span></p>
        <p>Tổng giờ: <span className="font-semibold">{totalHours()}h00</span></p>
        <p>Tổng tiền: <span className="font-bold text-brand-accent">{formatVnd(totalAmount())}</span></p>
      </Card>

      <Button variant="outline" fullWidth className="mb-5">{t('booking.addService')}</Button>

      <div className="space-y-4">
        <Input label={t('booking.yourName')} value={name} onChange={(e) => setName(e.target.value)} />
        <PhoneInput label={t('booking.yourPhone')} value={phone} onChange={(e) => setPhone(e.target.value)} />
        <div>
          <label className="mb-1.5 block text-sm font-semibold uppercase tracking-wide text-white/90">
            {t('booking.noteToOwner')}
          </label>
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={2}
            className="w-full rounded-xl bg-white px-4 py-3 text-gray-800 outline-none focus:ring-2 focus:ring-brand-gold"
          />
        </div>
      </div>

      <Button variant="gold" size="lg" fullWidth className="mt-6" onClick={handleConfirm}>
        {t('booking.confirmAndPay')}
      </Button>
    </PageShell>
  );
}
