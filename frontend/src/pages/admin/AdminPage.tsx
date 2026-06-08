import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { PageShell } from '../../components/layout/PageShell';
import { RoleGuard } from '../../components/routing/RoleGuard';
import { MockBanner } from '../../components/ui/MockBanner';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Pill } from '../../components/ui/Pill';
import { mockMatches } from '../../api/mockData';
import { cn, formatVnd } from '../../lib/cn';

type Tab = 'proofs' | 'matches' | 'bookings' | 'refunds';
const TABS: { key: Tab; label: string }[] = [
  { key: 'proofs', label: 'Duyệt thanh toán' },
  { key: 'matches', label: 'Trận đấu' },
  { key: 'bookings', label: 'Đặt sân' },
  { key: 'refunds', label: 'Hoàn tiền' },
];

function AdminInner() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('proofs');

  return (
    <PageShell title="Quản trị" onBack={() => navigate('/')}>
      <MockBanner />
      <div className="mb-4 flex flex-wrap gap-2">
        {TABS.map((tb) => (
          <button
            key={tb.key}
            onClick={() => setTab(tb.key)}
            className={cn(
              'rounded-full px-4 py-2 text-sm font-semibold',
              tab === tb.key ? 'bg-brand-gold text-[#3a2c08]' : 'bg-white/10 text-white/80 hover:bg-white/20',
            )}
          >
            {tb.label}
          </button>
        ))}
      </div>

      {tab === 'proofs' && (
        <div className="space-y-3">
          {[184, 185].map((code) => (
            <Card key={code} className="flex items-center justify-between gap-3">
              <div>
                <p className="font-semibold">Đơn #{code}</p>
                <p className="text-sm text-white/80">EVENT_TICKET · {formatVnd(60000)}</p>
              </div>
              <div className="flex gap-2">
                <Button size="sm" onClick={() => toast.success(`Đã xác nhận #${code}`)}>Xác nhận</Button>
                <Button size="sm" variant="danger" onClick={() => toast.error(`Đã từ chối #${code}`)}>Từ chối</Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {tab === 'matches' && (
        <div className="space-y-3">
          {mockMatches.map((m) => (
            <Card key={m.id} className="flex items-center justify-between">
              <div>
                <p className="font-semibold">{m.title}</p>
                <p className="text-sm text-white/80">{m.courtName}</p>
              </div>
              <div className="flex items-center gap-2">
                <Pill variant="status">{m.status}</Pill>
                <Button size="sm" variant="danger" onClick={() => toast.success('Đã huỷ trận')}>Huỷ</Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {tab === 'bookings' && (
        <Card><p className="text-white/80">Danh sách đặt sân (mock) — sẽ nối API booking-service.</p></Card>
      )}

      {tab === 'refunds' && <RefundForm />}
    </PageShell>
  );
}

function RefundForm() {
  const [form, setForm] = useState({ toBankName: '', toAccountNumber: '', toAccountName: '', amount: '', refundNote: '' });
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));
  return (
    <Card>
      <h3 className="mb-3 font-bold text-brand-accent">Hoàn tiền thủ công</h3>
      <div className="grid gap-3 sm:grid-cols-2">
        <input placeholder="Ngân hàng" value={form.toBankName} onChange={set('toBankName')} className="rounded-lg bg-white px-3 py-2 text-gray-800 outline-none" />
        <input placeholder="Số tài khoản" value={form.toAccountNumber} onChange={set('toAccountNumber')} className="rounded-lg bg-white px-3 py-2 text-gray-800 outline-none" />
        <input placeholder="Tên tài khoản" value={form.toAccountName} onChange={set('toAccountName')} className="rounded-lg bg-white px-3 py-2 text-gray-800 outline-none" />
        <input placeholder="Số tiền" value={form.amount} onChange={set('amount')} className="rounded-lg bg-white px-3 py-2 text-gray-800 outline-none" />
      </div>
      <textarea placeholder="Ghi chú hoàn tiền" value={form.refundNote} onChange={set('refundNote')} rows={2} className="mt-3 w-full rounded-lg bg-white px-3 py-2 text-gray-800 outline-none" />
      <Button className="mt-3" onClick={() => toast.success('Đã ghi nhận yêu cầu hoàn tiền')}>Ghi nhận hoàn tiền</Button>
    </Card>
  );
}

export default function AdminPage() {
  return (
    <RoleGuard roles={['ROLE_STAFF', 'ROLE_ADMIN']}>
      <AdminInner />
    </RoleGuard>
  );
}
