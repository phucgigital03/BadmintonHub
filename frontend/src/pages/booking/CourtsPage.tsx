import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { MapContainer, TileLayer, CircleMarker, Popup } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import { courtsApi } from '../../api/courts';
import { mockClub, clubSportToCourt } from '../../api/mockData';
import type { ClubSport } from '../../types';
import { PageShell } from '../../components/layout/PageShell';
import { Card } from '../../components/ui/Card';
import { Pill } from '../../components/ui/Pill';
import { MockBanner } from '../../components/ui/MockBanner';
import { BookingTypeModal } from '../../components/booking/BookingTypeModal';
import { useBookingStore } from '../../store/bookingStore';
import { formatVnd } from '../../lib/cn';

// Single-club model: 1 CLB (venue) chứa NHIỀU môn (Pickleball / Badminton).
// Chọn môn → modal hình thức đặt → grid Sân CỦA MÔN đó.
const SPORT_ICON: Record<string, string> = { Pickleball: '🏓', Badminton: '🏸' };

export default function CourtsPage() {
  const navigate = useNavigate();
  const setCourt = useBookingStore((s) => s.setCourt);
  const [picked, setPicked] = useState<ClubSport | null>(null);

  // Query chỉ để phát hiện API sẵn sàng / hiện MockBanner. Hình + môn lấy từ mockClub
  // (court-service backend chưa có; sẽ đổi sang /api/clubs khi Day 6 xong — xem frontend.md).
  const query = useQuery({ queryKey: ['courts'], queryFn: () => courtsApi.list(), retry: 0 });
  const usingMock = query.isError || !query.data?.length;
  const club = mockClub;

  return (
    <PageShell title={club.name} onBack={() => navigate('/')}>
      {usingMock && <MockBanner />}

      <div className="grid gap-4 lg:grid-cols-2">
        <Card className="flex flex-col gap-2">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 className="text-2xl font-bold text-brand-accent">{club.name}</h2>
              <p className="mt-1 text-sm text-white/80">{club.address}</p>
            </div>
            <span className="shrink-0 text-sm text-amber-200">★ {club.rating}</span>
          </div>
        </Card>

        <div className="h-[300px] overflow-hidden rounded-xl lg:h-auto lg:min-h-[220px]">
          <MapContainer center={[club.lat, club.lng]} zoom={15} className="h-full min-h-[300px] w-full">
            <TileLayer
              attribution='&copy; OpenStreetMap'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <CircleMarker
              center={[club.lat, club.lng]}
              radius={10}
              pathOptions={{ color: '#d9a93e', fillColor: '#2e7a51', fillOpacity: 0.9 }}
            >
              <Popup>
                <strong>{club.name}</strong>
                <br />
                {club.address}
              </Popup>
            </CircleMarker>
          </MapContainer>
        </div>
      </div>

      <h3 className="mb-3 mt-6 text-lg font-bold">Chọn môn để đặt sân</h3>
      <div className="grid gap-3 sm:grid-cols-2">
        {club.sports.map((s) => (
          <button
            key={s.sport}
            onClick={() => {
              setCourt(clubSportToCourt(club, s));
              setPicked(s);
            }}
            className="rounded-2xl bg-brand-panel p-5 text-left transition-colors hover:bg-brand-panel2"
          >
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <span className="text-3xl" aria-hidden>{SPORT_ICON[s.sport] ?? '🎾'}</span>
                <div>
                  <h4 className="text-xl font-bold text-brand-accent">{s.sport}</h4>
                  <p className="mt-0.5 text-sm text-white/70">{s.courts.length} sân</p>
                </div>
              </div>
              <Pill variant="price">{formatVnd(s.pricePerHour)}/giờ</Pill>
            </div>
          </button>
        ))}
      </div>

      <BookingTypeModal
        open={!!picked}
        onClose={() => setPicked(null)}
        onPickVisualDay={() =>
          picked && navigate(`/courts/${club.id}/booking?sport=${encodeURIComponent(picked.sport)}`)
        }
        onPickEvent={() => navigate('/events')}
      />
    </PageShell>
  );
}
