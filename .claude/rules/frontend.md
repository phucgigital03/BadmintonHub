---
description: React 18 + TypeScript + Tailwind CSS conventions for BadmintonHub frontend — API client, state management, Socket.io, forms, and routing patterns.
globs: frontend/src/**/*.ts, frontend/src/**/*.tsx
alwaysApply: false
---

# Frontend Conventions

Stack: React 18 · Vite · TypeScript · Tailwind CSS · Zustand · React Query · Axios · Socket.io · React Router v6

## Single-club model

Hệ thống quản lý **đúng 1 CLB** (venue), CLB chứa **nhiều môn** (Pickleball / Badminton). KHÔNG có directory/search nhiều CLB:
- `/courts` = trang của **chính CLB đó** — header (tên/địa chỉ/rating) + map 1 marker + **các tile môn**
  (`mockClub.sports`): mỗi môn có **giá riêng** + **danh sách Sân riêng**.
- **Chọn môn** → `setCourt(clubSportToCourt(club, sport))` → `BookingTypeModal` (trực quan / sự kiện):
  trực quan → `/courts/:id/booking?sport=X` (grid **chỉ Sân của môn đó** + giá môn đó); sự kiện → `/events`.
- Mô hình dữ liệu: **clubs (venue) ──< courts (Sân, có `sport`) ──< time_slots (ô 30')**; giá theo `court_pricing_rules`
  (per sport). Type FE: `Club { sports: ClubSport[] }`, `ClubSport { sport, pricePerHour, courts[] }`,
  `Court` = booking-context (club + môn đã chọn, mang `type`/`pricePerHour`/`courts[]`).
- ⚠️ FE tạm gọi endpoint `/api/courts` (mock 1 CLB qua `mockClub`). Khi court-service backend xong sẽ đổi sang
  `/api/clubs` (+ `?sport=`) và tách `Club`/`Court(Sân)` đúng ERD — việc tương lai, chưa làm.

## API Client

All HTTP calls go through `axiosClient.ts` — never raw `fetch` or a new Axios instance:

```ts
// src/api/axiosClient.ts
const axiosClient = axios.create({ baseURL: import.meta.env.VITE_API_URL });

// Request interceptor: attach JWT access token
axiosClient.interceptors.request.use(config => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Response interceptor: silent refresh on 401
axiosClient.interceptors.response.use(null, async error => {
  if (error.response?.status === 401 && !error.config._retry) {
    error.config._retry = true;
    await refreshAccessToken();  // POST /api/auth/refresh (cookie-based)
    return axiosClient(error.config);
  }
  return Promise.reject(error);
});
```

WebSocket base URL from `import.meta.env.VITE_WS_URL` (matchmaking-service, port 3004).

## State Management

| Type | Tool | Stores |
|---|---|---|
| Server state (async) | React Query (`useQuery`, `useMutation`) | All API data |
| Client/UI state | Zustand | `authStore`, `matchStore`, `notificationStore` |
| Form state | React Hook Form + Zod | All forms |

```ts
// authStore.ts
interface AuthStore {
  accessToken: string | null;
  user: User | null;
  setAuth: (token: string, user: User) => void;
  clearAuth: () => void;
}
```

Never put server data in Zustand — use React Query for anything fetched from the API.

## React Query Patterns

```tsx
// Query
const { data: match, isLoading } = useQuery({
  queryKey: ['match', matchId],
  queryFn: () => axiosClient.get(`/api/matches/${matchId}`).then(r => r.data),
  staleTime: 30_000,
});

// Mutation
const joinMutation = useMutation({
  mutationFn: (matchId: string) =>
    axiosClient.post(`/api/matches/${matchId}/join`).then(r => r.data),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['match', matchId] });
    toast.success('Đã tham gia trận thành công!');
  },
  onError: (err) => toast.error(err.response?.data?.message ?? 'Lỗi hệ thống'),
});
```

## Socket.io (Real-time Slot Counter)

Never use Socket.io directly in components. Always via the `useMatchSocket` hook:

```ts
// src/hooks/useMatchSocket.ts
export function useMatchSocket(matchId: string) {
  useEffect(() => {
    const socket = io(import.meta.env.VITE_WS_URL);
    socket.emit('join-match-room', matchId);
    socket.on('slot-updated', (data: { filledSlots: number; totalSlots: number; status: string }) => {
      queryClient.setQueryData(['match', matchId], (old: Match) => ({ ...old, ...data }));
    });
    return () => { socket.disconnect(); };
  }, [matchId]);
}
```

## Forms

```tsx
// React Hook Form + Zod
const schema = z.object({
  totalSlots: z.number().int().min(2).max(16).refine(n => n % 2 === 0, 'Phải là số chẵn'),
  pricePerPerson: z.number().min(0),
  date: z.date().min(new Date(), 'Phải là ngày tương lai'),
});

const { register, handleSubmit, formState: { errors } } = useForm<CreateMatchForm>({
  resolver: zodResolver(schema),
});
```

## Routing (React Router v6)

```tsx
// src/App.tsx — protected routes
const router = createBrowserRouter([
  { path: '/', element: <HomePage /> },
  { path: '/matches', element: <MatchesPage /> },
  { path: '/matches/:id', element: <MatchDetailPage /> },
  { path: '/dashboard', element: <ProtectedRoute><DashboardPage /></ProtectedRoute> },
  { path: '/admin/*', element: <RoleGuard roles={['STAFF','ADMIN']}><AdminPage /></RoleGuard> },
]);
```

## Payment Screen Component Pattern

```tsx
interface PaymentScreenProps {
  paymentId: string;
  orderCode: string;
  bankName: string;
  accountNumber: string;
  accountName: string;
  qrImageUrl: string;
  amount: number;
  expiresAt: string;  // ISO timestamp
}
// Shows: bank info + QR image + countdown timer + proof upload zone
// Upload: multipart POST /api/payments/{id}/proof
// Disable confirm button until image is selected
```

## Slot Color Convention (Visual Booking Grid)

```ts
const slotColors = {
  AVAILABLE: 'bg-white hover:bg-green-100 cursor-pointer',
  RESERVED:  'bg-red-200 cursor-not-allowed',
  BLOCKED:   'bg-gray-300 cursor-not-allowed',
  EVENT:     'bg-purple-200 cursor-pointer',  // show tooltip on hover
} as const;
```

## i18n

Use `react-i18next`. All user-facing strings via `t('key')` — never hardcode Vietnamese strings outside of translation files. Language files: `src/i18n/vi.json`, `src/i18n/en.json`.

## Toast Notifications

Use `react-hot-toast` for all user feedback:
- `toast.success(...)` — confirmed actions
- `toast.error(...)` — API errors
- `toast.loading(...)` — async mutations in progress

## File Structure

```
frontend/src/
├── api/            # axiosClient.ts + per-resource API functions
├── components/     # Reusable UI (Button, Modal, SlotGrid, PaymentScreen, etc.)
├── pages/          # One file per route
├── store/          # Zustand stores
├── hooks/          # useSocket.ts, useAuth.ts, useNotifications.ts
├── i18n/           # vi.json, en.json
└── App.tsx         # Router config
```
