import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';

/** Renders children only if the user has at least one of the given roles. */
export function RoleGuard({ roles, children }: { roles: string[]; children: ReactNode }) {
  const user = useAuthStore((s) => s.user);
  const token = useAuthStore((s) => s.accessToken);
  if (!token) return <Navigate to="/login" replace />;
  const allowed = user?.roles?.some((r) => roles.includes(r));
  if (!allowed) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center text-white/80">
        <div className="mb-3 text-4xl" aria-hidden>🔒</div>
        <p className="text-lg font-semibold">Bạn không có quyền truy cập trang này.</p>
      </div>
    );
  }
  return <>{children}</>;
}
