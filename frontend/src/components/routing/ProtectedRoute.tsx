import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';

/** Redirects to /login if there is no access token. */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.accessToken);
  const location = useLocation();
  if (!token) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <>{children}</>;
}
