import { Routes, Route } from 'react-router-dom';
import HomePage from './pages/HomePage';
import NotFoundPage from './pages/NotFoundPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      {/* Routes for auth, booking, events, payment, etc. are added per build phase. */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
