import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { AxiosError } from 'axios';
import { PageShell } from '../../components/layout/PageShell';
import { RoleGuard } from '../../components/routing/RoleGuard';
import { MockBanner } from '../../components/ui/MockBanner';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Pill } from '../../components/ui/Pill';
import { Spinner } from '../../components/ui/EmptyState';
import { Modal } from '../../components/ui/Modal';
import { usersApi } from '../../api/users';
import { paymentsApi, paymentStatusLabel, type PaymentResponse } from '../../api/payments';
import {
  clubsApi,
  type SportEnum,
  type CourtTypeEnum,
  type DayTypeEnum,
  type CustomerTypeEnum,
} from '../../api/clubs';
import { useAuthStore } from '../../store/authStore';
import { mockMatches } from '../../api/mockData';
import { cn, formatVnd } from '../../lib/cn';

type Tab = 'users' | 'court' | 'proofs' | 'matches' | 'bookings' | 'refunds';
const TABS: { key: Tab; label: string }[] = [
  { key: 'users', label: 'Người dùng' },
  { key: 'court', label: 'Sân & giá' },
  { key: 'proofs', label: 'Duyệt thanh toán' },
  { key: 'matches', label: 'Trận đấu' },
  { key: 'bookings', label: 'Đặt sân' },
  { key: 'refunds', label: 'Hoàn tiền' },
];

// Tabs wired to a real backend (no mock banner): user-service + court-service + payment-service.
const REAL_TABS: Tab[] = ['users', 'court', 'proofs', 'refunds'];

function AdminInner() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('users');

  return (
    <PageShell title="Quản trị" onBack={() => navigate('/')}>
      {/* Users + Sân&giá tabs are wired to real backends; the rest are still mock. */}
      {!REAL_TABS.includes(tab) && <MockBanner />}
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

      {tab === 'users' && <UsersTab />}

      {tab === 'court' && <CourtAdminTab />}

      {tab === 'proofs' && <ProofsTab />}

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

      {tab === 'refunds' && <RefundsTab />}
    </PageShell>
  );
}

function UsersTab() {
  const qc = useQueryClient();
  const currentUserId = useAuthStore((s) => s.user?.id);
  const isAdmin = useAuthStore((s) => s.hasRole('ROLE_ADMIN'));

  // GET /api/users — STAFF/ADMIN only (real user-service call).
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['admin-users'],
    queryFn: () => usersApi.list(0, 50),
    retry: false,
  });

  const del = useMutation({
    mutationFn: (id: string) => usersApi.remove(id), // DELETE — ADMIN only, soft delete
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-users'] });
      toast.success('Đã xoá (soft) người dùng');
    },
    onError: (err: AxiosError<{ message?: string }>) =>
      toast.error(err.response?.data?.message ?? 'Không xoá được'),
  });

  if (isPending) return <Spinner label="Đang tải người dùng..." />;
  if (isError) {
    const status = (error as AxiosError)?.response?.status;
    return (
      <Card>
        <p className="text-white/80">
          Không tải được danh sách người dùng{status ? ` (HTTP ${status})` : ''}. Cần đăng nhập bằng
          tài khoản <b>STAFF/ADMIN</b> và user-service đang chạy.
        </p>
      </Card>
    );
  }

  return (
    <div className="space-y-3">
      {data.content.length === 0 && (
        <Card><p className="text-white/80">Chưa có người dùng.</p></Card>
      )}
      {data.content.map((u) => (
        <Card key={u.id} className="flex items-center justify-between gap-3">
          <div>
            <p className="font-semibold">{u.fullName}</p>
            <p className="text-sm text-white/80">{u.email}</p>
            <div className="mt-1 flex flex-wrap gap-1">
              {u.roles.map((r) => (
                <span key={r} className="rounded-full bg-white/15 px-2 py-0.5 text-[11px]">{r}</span>
              ))}
              {!u.isEmailVerified && (
                <span className="rounded-full bg-amber-300/20 px-2 py-0.5 text-[11px] text-amber-200">
                  chưa xác thực
                </span>
              )}
            </div>
          </div>
          {isAdmin && u.id !== currentUserId && (
            <Button size="sm" variant="danger" onClick={() => del.mutate(u.id)} disabled={del.isPending}>
              Xoá
            </Button>
          )}
        </Card>
      ))}
      <p className="px-1 text-xs text-white/50">
        Tổng {data.totalElements} người dùng · trang {data.number + 1}/{data.totalPages}
      </p>
    </div>
  );
}

// ── Court admin: drives all 5 court-service STAFF/ADMIN mutations from the UI ──
const inputCls = 'rounded-lg bg-white px-3 py-2 text-gray-800 outline-none';
const SPORTS: SportEnum[] = ['PICKLEBALL', 'BADMINTON'];
const COURT_TYPES: CourtTypeEnum[] = ['SYNTHETIC', 'WOOD', 'CONCRETE'];
const DAY_TYPES: DayTypeEnum[] = ['WEEKDAY', 'WEEKEND'];
const CUSTOMER_TYPES: CustomerTypeEnum[] = ['WALK_IN', 'FIXED'];

function CourtAdminTab() {
  const qc = useQueryClient();
  const clubs = useQuery({ queryKey: ['admin-clubs'], queryFn: clubsApi.listClubs, retry: false });
  const [clubId, setClubId] = useState('');
  useEffect(() => {
    if (!clubId && clubs.data?.length) setClubId(clubs.data[0].id);
  }, [clubId, clubs.data]);

  const onErr = (e: AxiosError<{ message?: string }>) =>
    toast.error(e.response?.data?.message ?? `Lỗi (HTTP ${e.response?.status ?? '?'})`);
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['admin-clubs'] });
    qc.invalidateQueries({ queryKey: ['club'] });
  };

  // create club
  const [club, setClub] = useState({ name: '', address: '', district: '', latitude: '', longitude: '' });
  const createClub = useMutation({
    mutationFn: () =>
      clubsApi.createClub({
        name: club.name,
        address: club.address,
        district: club.district,
        latitude: club.latitude ? Number(club.latitude) : undefined,
        longitude: club.longitude ? Number(club.longitude) : undefined,
      }),
    onSuccess: (c) => {
      toast.success(`Đã tạo CLB "${c.name}"`);
      setClub({ name: '', address: '', district: '', latitude: '', longitude: '' });
      refresh();
    },
    onError: onErr,
  });

  // add court
  const [court, setCourt] = useState<{ courtNumber: string; sport: SportEnum; type: CourtTypeEnum }>({
    courtNumber: '',
    sport: 'PICKLEBALL',
    type: 'SYNTHETIC',
  });
  const addCourt = useMutation({
    mutationFn: () => clubsApi.addCourt(clubId, court),
    onSuccess: (c) => {
      toast.success(`Đã thêm ${c.courtNumber}`);
      setCourt({ courtNumber: '', sport: 'PICKLEBALL', type: 'SYNTHETIC' });
      refresh();
    },
    onError: onErr,
  });

  // create pricing rule
  const [price, setPrice] = useState<{
    sport: SportEnum;
    dayType: DayTypeEnum;
    customerType: CustomerTypeEnum;
    startTime: string;
    endTime: string;
    pricePerHour: string;
  }>({ sport: 'PICKLEBALL', dayType: 'WEEKDAY', customerType: 'WALK_IN', startTime: '05:00', endTime: '10:00', pricePerHour: '' });
  const createPricing = useMutation({
    mutationFn: () =>
      clubsApi.createPricing(clubId, {
        sport: price.sport,
        dayType: price.dayType,
        customerType: price.customerType,
        startTime: price.startTime,
        endTime: price.endTime,
        pricePerHour: Number(price.pricePerHour),
      }),
    onSuccess: () => {
      toast.success('Đã tạo bảng giá');
      refresh();
    },
    onError: onErr,
  });

  // generate 30 days of slots
  const genSlots = useMutation({
    mutationFn: () => clubsApi.generateSlots(clubId),
    onSuccess: (r) => toast.success(`Đã sinh ${r.created} slot (${r.from} → ${r.to})`),
    onError: onErr,
  });

  // block a slot
  const [slotId, setSlotId] = useState('');
  const blockSlot = useMutation({
    mutationFn: () => clubsApi.blockSlot(slotId.trim()),
    onSuccess: () => {
      toast.success('Đã khoá slot');
      setSlotId('');
      qc.invalidateQueries({ queryKey: ['day-grid'] });
    },
    onError: onErr,
  });

  const noClub = !clubId;

  return (
    <div className="space-y-4">
      {/* Club picker (drives court / pricing / generate-slots) */}
      <Card>
        <h3 className="mb-2 font-bold text-brand-accent">Chọn CLB</h3>
        {clubs.isPending ? (
          <Spinner label="Đang tải danh sách CLB..." />
        ) : clubs.isError ? (
          <p className="text-sm text-white/80">
            Không tải được danh sách CLB (cần court-service :3002 + gateway :3000). Bạn vẫn có thể tạo CLB mới bên dưới.
          </p>
        ) : (
          <select value={clubId} onChange={(e) => setClubId(e.target.value)} className={cn(inputCls, 'w-full')}>
            {clubs.data?.length === 0 && <option value="">— Chưa có CLB, hãy tạo mới —</option>}
            {clubs.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} · {c.district}
              </option>
            ))}
          </select>
        )}
        {clubId && <p className="mt-2 break-all text-xs text-white/50">clubId: {clubId}</p>}
      </Card>

      {/* 1) Create club — POST /api/clubs */}
      <Card>
        <h3 className="mb-3 font-bold text-brand-accent">Tạo CLB · POST /api/clubs</h3>
        <div className="grid gap-3 sm:grid-cols-2">
          <input placeholder="Tên CLB" value={club.name} onChange={(e) => setClub((f) => ({ ...f, name: e.target.value }))} className={inputCls} />
          <input placeholder="Quận/Huyện" value={club.district} onChange={(e) => setClub((f) => ({ ...f, district: e.target.value }))} className={inputCls} />
          <input placeholder="Địa chỉ" value={club.address} onChange={(e) => setClub((f) => ({ ...f, address: e.target.value }))} className={cn(inputCls, 'sm:col-span-2')} />
          <input placeholder="Vĩ độ (lat)" value={club.latitude} onChange={(e) => setClub((f) => ({ ...f, latitude: e.target.value }))} className={inputCls} />
          <input placeholder="Kinh độ (lng)" value={club.longitude} onChange={(e) => setClub((f) => ({ ...f, longitude: e.target.value }))} className={inputCls} />
        </div>
        <Button
          className="mt-3"
          disabled={createClub.isPending || !club.name || !club.address || !club.district}
          onClick={() => createClub.mutate()}
        >
          Tạo CLB
        </Button>
      </Card>

      {/* 2) Add court — POST /api/courts?clubId= */}
      <Card>
        <h3 className="mb-3 font-bold text-brand-accent">Thêm sân · POST /api/courts</h3>
        <div className="grid gap-3 sm:grid-cols-3">
          <input placeholder='Tên sân (vd "Sân 6")' value={court.courtNumber} onChange={(e) => setCourt((f) => ({ ...f, courtNumber: e.target.value }))} className={inputCls} />
          <select value={court.sport} onChange={(e) => setCourt((f) => ({ ...f, sport: e.target.value as SportEnum }))} className={inputCls}>
            {SPORTS.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          <select value={court.type} onChange={(e) => setCourt((f) => ({ ...f, type: e.target.value as CourtTypeEnum }))} className={inputCls}>
            {COURT_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        <Button className="mt-3" disabled={addCourt.isPending || noClub || !court.courtNumber} onClick={() => addCourt.mutate()}>
          Thêm sân
        </Button>
      </Card>

      {/* 3) Create pricing rule — POST /api/clubs/{id}/pricing */}
      <Card>
        <h3 className="mb-3 font-bold text-brand-accent">Tạo bảng giá · POST /api/clubs/{'{id}'}/pricing</h3>
        <div className="grid gap-3 sm:grid-cols-3">
          <select value={price.sport} onChange={(e) => setPrice((f) => ({ ...f, sport: e.target.value as SportEnum }))} className={inputCls}>
            {SPORTS.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          <select value={price.dayType} onChange={(e) => setPrice((f) => ({ ...f, dayType: e.target.value as DayTypeEnum }))} className={inputCls}>
            {DAY_TYPES.map((d) => <option key={d} value={d}>{d}</option>)}
          </select>
          <select value={price.customerType} onChange={(e) => setPrice((f) => ({ ...f, customerType: e.target.value as CustomerTypeEnum }))} className={inputCls}>
            {CUSTOMER_TYPES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          <input type="time" value={price.startTime} onChange={(e) => setPrice((f) => ({ ...f, startTime: e.target.value }))} className={inputCls} />
          <input type="time" value={price.endTime} onChange={(e) => setPrice((f) => ({ ...f, endTime: e.target.value }))} className={inputCls} />
          <input placeholder="Giá / giờ (vd 80000)" value={price.pricePerHour} onChange={(e) => setPrice((f) => ({ ...f, pricePerHour: e.target.value }))} className={inputCls} />
        </div>
        <Button className="mt-3" disabled={createPricing.isPending || noClub || !price.pricePerHour} onClick={() => createPricing.mutate()}>
          Tạo bảng giá
        </Button>
      </Card>

      {/* 4) Generate slots — POST /api/clubs/{id}/generate-slots */}
      <Card className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="font-bold text-brand-accent">Sinh slot 30 ngày · POST /generate-slots</h3>
          <p className="text-sm text-white/70">Tạo ô 30' (05:00–22:00) cho tất cả sân, từ ngày mai đến +30 ngày.</p>
        </div>
        <Button disabled={genSlots.isPending || noClub} onClick={() => genSlots.mutate()}>
          {genSlots.isPending ? 'Đang sinh...' : 'Sinh slot'}
        </Button>
      </Card>

      {/* 5) Block slot — PATCH /api/courts/slots/{slotId}/block */}
      <Card>
        <h3 className="mb-3 font-bold text-brand-accent">Khoá slot · PATCH /api/courts/slots/{'{slotId}'}/block</h3>
        <div className="flex flex-wrap gap-3">
          <input placeholder="slotId (UUID)" value={slotId} onChange={(e) => setSlotId(e.target.value)} className={cn(inputCls, 'flex-1')} />
          <Button variant="danger" disabled={blockSlot.isPending || !slotId.trim()} onClick={() => blockSlot.mutate()}>
            Khoá slot
          </Button>
        </div>
      </Card>
    </div>
  );
}

// ── Duyệt thanh toán: GET /pending-review → confirm / reject (real payment-service) ──
function ProofsTab() {
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ['admin-pending-review'],
    queryFn: () => paymentsApi.listPendingReview(0, 50),
    retry: false,
  });
  const onErr = (e: AxiosError<{ message?: string }>) =>
    toast.error(e.response?.data?.message ?? `Lỗi (HTTP ${e.response?.status ?? '?'})`);
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['admin-pending-review'] });
    qc.invalidateQueries({ queryKey: ['admin-refund-required'] });
  };

  const confirm = useMutation({
    mutationFn: (id: string) => paymentsApi.confirm(id),
    onSuccess: (p) => { toast.success(`Đã xác nhận ${p.orderCode}`); invalidate(); },
    onError: onErr,
  });
  const reject = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) => paymentsApi.reject(id, reason),
    onSuccess: (p) => { toast.success(`Đã từ chối ${p.orderCode}`); invalidate(); },
    onError: onErr,
  });

  if (q.isPending) return <Spinner label="Đang tải hàng chờ duyệt..." />;
  if (q.isError) {
    const status = (q.error as AxiosError)?.response?.status;
    return (
      <Card>
        <p className="text-white/80">
          Không tải được hàng chờ duyệt{status ? ` (HTTP ${status})` : ''}. Cần STAFF/ADMIN + payment-service :3006.
        </p>
      </Card>
    );
  }
  if (q.data.content.length === 0)
    return <Card><p className="text-white/80">Không có chứng từ nào đang chờ duyệt. 🎉</p></Card>;

  const busy = confirm.isPending || reject.isPending;
  return (
    <div className="space-y-3">
      {q.data.content.map((p) => (
        <Card key={p.id} className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="font-semibold">{p.orderCode} · {p.paymentType}</p>
            <p className="text-sm text-white/80">
              {formatVnd(p.amount)} · {new Date(p.createdAt).toLocaleString('vi-VN')}
            </p>
            {p.bookingId && <p className="break-all text-xs text-white/50">booking: {p.bookingId}</p>}
          </div>
          <div className="flex gap-2">
            <Button size="sm" disabled={busy} onClick={() => confirm.mutate(p.id)}>Xác nhận</Button>
            <Button
              size="sm"
              variant="danger"
              disabled={busy}
              onClick={() => {
                const reason = window.prompt('Lý do từ chối (tuỳ chọn):');
                if (reason === null) return; // cancelled prompt → abort
                reject.mutate({ id: p.id, reason: reason.trim() || undefined });
              }}
            >
              Từ chối
            </Button>
          </div>
        </Card>
      ))}
    </div>
  );
}

// ── Hoàn tiền: GET /refund-required → refund modal (real, amount capped at paid) ──
function RefundsTab() {
  const qc = useQueryClient();
  const [target, setTarget] = useState<PaymentResponse | null>(null);
  const q = useQuery({
    queryKey: ['admin-refund-required'],
    queryFn: () => paymentsApi.listRefundRequired(0, 50),
    retry: false,
  });

  if (q.isPending) return <Spinner label="Đang tải đơn cần hoàn..." />;
  if (q.isError) {
    const status = (q.error as AxiosError)?.response?.status;
    return (
      <Card>
        <p className="text-white/80">
          Không tải được danh sách hoàn tiền{status ? ` (HTTP ${status})` : ''}. Cần STAFF/ADMIN + payment-service :3006.
        </p>
      </Card>
    );
  }
  if (q.data.content.length === 0)
    return <Card><p className="text-white/80">Không có đơn nào cần hoàn tiền. 🎉</p></Card>;

  return (
    <div className="space-y-3">
      {q.data.content.map((p) => (
        <Card key={p.id} className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="font-semibold">{p.orderCode} · {paymentStatusLabel(p.status)}</p>
            <p className="text-sm text-white/80">
              Đã thu {formatVnd(p.amount)}
              {p.refundRequiredAmount != null && (
                <> · gợi ý hoàn <span className="font-semibold text-brand-accent">{formatVnd(p.refundRequiredAmount)}</span></>
              )}
            </p>
            {p.bookingId && <p className="break-all text-xs text-white/50">booking: {p.bookingId}</p>}
          </div>
          <Button size="sm" onClick={() => setTarget(p)}>Hoàn tiền</Button>
        </Card>
      ))}
      <RefundModal
        payment={target}
        onClose={() => setTarget(null)}
        onDone={() => {
          setTarget(null);
          qc.invalidateQueries({ queryKey: ['admin-refund-required'] });
        }}
      />
    </div>
  );
}

function RefundModal({
  payment,
  onClose,
  onDone,
}: {
  payment: PaymentResponse | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const [form, setForm] = useState({ toBankName: '', toAccountNumber: '', toAccountName: '', amount: '', refundNote: '' });
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  // Prefill amount with the suggested refund (or the full paid amount) whenever a new target opens.
  useEffect(() => {
    if (payment) {
      const suggested = payment.refundRequiredAmount ?? payment.amount;
      setForm({ toBankName: '', toAccountNumber: '', toAccountName: '', amount: String(suggested), refundNote: '' });
    }
  }, [payment]);

  const refund = useMutation({
    mutationFn: () =>
      paymentsApi.refund(payment!.id, {
        amount: Number(form.amount),
        toBankName: form.toBankName,
        toAccountNumber: form.toAccountNumber,
        toAccountName: form.toAccountName,
        refundNote: form.refundNote || undefined,
      }),
    onSuccess: (p) => { toast.success(`Đã hoàn ${formatVnd(p.refundAmount ?? 0)} cho ${p.orderCode}`); onDone(); },
    onError: (e: AxiosError<{ message?: string }>) =>
      toast.error(e.response?.data?.message ?? 'Hoàn tiền thất bại'),
  });

  const fieldCls = 'rounded-lg bg-white px-3 py-2 text-gray-800 outline-none border border-gray-200';
  const valid = form.toBankName && form.toAccountNumber && form.toAccountName && Number(form.amount) > 0;

  return (
    <Modal open={!!payment} onClose={onClose} title={payment ? `Hoàn tiền ${payment.orderCode}` : 'Hoàn tiền'}>
      {payment && (
        <div className="space-y-3 text-sm text-gray-800">
          <p className="text-gray-500">
            Đã thu <b>{formatVnd(payment.amount)}</b> — không thể hoàn quá số này.
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            <input placeholder="Ngân hàng" value={form.toBankName} onChange={set('toBankName')} className={fieldCls} />
            <input placeholder="Số tài khoản" value={form.toAccountNumber} onChange={set('toAccountNumber')} className={fieldCls} />
            <input placeholder="Tên tài khoản" value={form.toAccountName} onChange={set('toAccountName')} className={fieldCls} />
            <input placeholder="Số tiền" value={form.amount} onChange={set('amount')} className={fieldCls} />
          </div>
          <textarea placeholder="Ghi chú hoàn tiền" value={form.refundNote} onChange={set('refundNote')} rows={2} className={`${fieldCls} w-full`} />
          <Button fullWidth disabled={!valid || refund.isPending} onClick={() => refund.mutate()}>
            {refund.isPending ? 'Đang hoàn…' : 'Xác nhận đã chuyển khoản hoàn tiền'}
          </Button>
        </div>
      )}
    </Modal>
  );
}

export default function AdminPage() {
  return (
    <RoleGuard roles={['ROLE_STAFF', 'ROLE_ADMIN']}>
      <AdminInner />
    </RoleGuard>
  );
}
