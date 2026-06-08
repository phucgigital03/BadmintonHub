import { Routes, Route } from 'react-router-dom';
import HomePage from './pages/HomePage';
import NotFoundPage from './pages/NotFoundPage';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import VerifyEmailPage from './pages/auth/VerifyEmailPage';
import DashboardPage from './pages/DashboardPage';
import CourtsPage from './pages/booking/CourtsPage';
import CourtDayBookingPage from './pages/booking/CourtDayBookingPage';
import PriceTablePage from './pages/booking/PriceTablePage';
import BookingConfirmPage from './pages/booking/BookingConfirmPage';
import { ProtectedRoute } from './components/routing/ProtectedRoute';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />

      {/* Auth (live against gateway :3000) */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />

      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />

      {/* Booking flow (mock data until court/booking backends exist) */}
      <Route path="/courts" element={<CourtsPage />} />
      <Route path="/courts/:courtId/booking" element={<CourtDayBookingPage />} />
      <Route path="/courts/:courtId/pricing" element={<PriceTablePage />} />
      <Route path="/booking/confirm" element={<BookingConfirmPage />} />

      {/* Events, payment, matches, admin, coaches added in later phases. */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
