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
import { usersApi } from '../../api/users';
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

// Tabs wired to a real backend (no mock banner): user-service + court-service.
const REAL_TABS: Tab[] = ['users', 'court'];

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
