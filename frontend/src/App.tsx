import { Routes, Route } from 'react-router-dom';
import HomePage from './pages/HomePage';
import NotFoundPage from './pages/NotFoundPage';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import VerifyEmailPage from './pages/auth/VerifyEmailPage';
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage';
import ResetPasswordPage from './pages/auth/ResetPasswordPage';
import DashboardPage from './pages/DashboardPage';
import ProfilePage from './pages/ProfilePage';
import CourtsPage from './pages/booking/CourtsPage';
import CourtDayBookingPage from './pages/booking/CourtDayBookingPage';
import PriceTablePage from './pages/booking/PriceTablePage';
import BookingConfirmPage from './pages/booking/BookingConfirmPage';
import EventsPage from './pages/events/EventsPage';
import EventDetailPage from './pages/events/EventDetailPage';
import PaymentPage from './pages/PaymentPage';
import MatchesPage from './pages/matches/MatchesPage';
import MatchDetailPage from './pages/matches/MatchDetailPage';
import CoachesPage from './pages/coach/CoachesPage';
import CoachDetailPage from './pages/coach/CoachDetailPage';
import AdminPage from './pages/admin/AdminPage';
import { ProtectedRoute } from './components/routing/ProtectedRoute';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />

      {/* Auth (live against gateway :3000) */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/profile"
        element={
          <ProtectedRoute>
            <ProfilePage />
          </ProtectedRoute>
        }
      />

      {/* Booking flow (mock data until court/booking backends exist) */}
      <Route path="/courts" element={<CourtsPage />} />
      <Route path="/courts/:courtId/booking" element={<CourtDayBookingPage />} />
      <Route path="/courts/:courtId/pricing" element={<PriceTablePage />} />
      <Route path="/booking/confirm" element={<BookingConfirmPage />} />

      {/* Events + payment (mock) */}
      <Route path="/events" element={<EventsPage />} />
      <Route path="/events/:eventId" element={<EventDetailPage />} />
      <Route path="/payment" element={<PaymentPage />} />

      {/* Matchmaking + realtime (mock) */}
      <Route path="/matches" element={<MatchesPage />} />
      <Route path="/matches/:matchId" element={<MatchDetailPage />} />

      {/* Coaches (mock) */}
      <Route path="/coaches" element={<CoachesPage />} />
      <Route path="/coaches/:coachId" element={<CoachDetailPage />} />

      {/* Admin (RoleGuard STAFF/ADMIN) */}
      <Route path="/admin" element={<AdminPage />} />

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
