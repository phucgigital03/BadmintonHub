import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { MapContainer, TileLayer, CircleMarker, Popup } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import { courtsApi } from '../../api/courts';
import { mockCourts } from '../../api/mockData';
import type { Court } from '../../types';
import { PageShell } from '../../components/layout/PageShell';
import { Card } from '../../components/ui/Card';
import { Pill } from '../../components/ui/Pill';
import { Button } from '../../components/ui/Button';
import { MockBanner } from '../../components/ui/MockBanner';
import { Spinner } from '../../components/ui/EmptyState';
import { BookingTypeModal } from '../../components/booking/BookingTypeModal';
import { useBookingStore } from '../../store/bookingStore';
import { formatVnd } from '../../lib/cn';

export default function CourtsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setCourt = useBookingStore((s) => s.setCourt);
  const [q, setQ] = useState('');
  const [picked, setPicked] = useState<Court | null>(null);

  const query = useQuery({ queryKey: ['courts'], queryFn: () => courtsApi.list(), retry: 0 });
  const usingMock = query.isError;
  const courts = usingMock ? mockCourts : query.data ?? [];

  const filtered = useMemo(
    () =>
      courts.filter(
        (c) =>
          !q ||
          c.name.toLowerCase().includes(q.toLowerCase()) ||
          c.district.toLowerCase().includes(q.toLowerCase()),
      ),
    [courts, q],
  );

  const center: [number, number] = filtered[0]
    ? [filtered[0].lat, filtered[0].lng]
    : [10.8231, 106.6297];

  return (
    <PageShell title={t('nav.courts')} onBack={() => navigate('/')}>
      {usingMock && <MockBanner />}

      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Tìm theo tên CLB hoặc quận..."
        className="mb-4 w-full rounded-xl bg-white px-4 py-3 text-gray-800 outline-none focus:ring-2 focus:ring-brand-gold"
      />

      {query.isPending && !usingMock ? (
        <Spinner label={t('common.loading')} />
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="space-y-3">
            {filtered.map((c) => (
              <Card key={c.id} className="flex flex-col gap-2">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h3 className="text-lg font-bold text-brand-accent">{c.name}</h3>
                    <p className="text-sm text-white/80">{c.address}</p>
                  </div>
                  <span className="shrink-0 text-sm text-amber-200">★ {c.rating}</span>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <Pill variant="skill">{c.type}</Pill>
                  <Pill variant="price">{formatVnd(c.pricePerHour)}/giờ</Pill>
                </div>
                <Button
                  className="mt-1 self-start"
                  size="sm"
                  onClick={() => {
                    setCourt(c);
                    setPicked(c);
                  }}
                >
                  Đặt lịch
                </Button>
              </Card>
            ))}
          </div>

          <div className="h-[360px] overflow-hidden rounded-xl lg:h-auto lg:min-h-[420px]">
            <MapContainer center={center} zoom={12} className="h-full min-h-[360px] w-full">
              <TileLayer
                attribution='&copy; OpenStreetMap'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              {filtered.map((c) => (
                <CircleMarker
                  key={c.id}
                  center={[c.lat, c.lng]}
                  radius={9}
                  pathOptions={{ color: '#d9a93e', fillColor: '#2e7a51', fillOpacity: 0.9 }}
                >
                  <Popup>
                    <strong>{c.name}</strong>
                    <br />
                    {c.address}
                  </Popup>
                </CircleMarker>
              ))}
            </MapContainer>
          </div>
        </div>
      )}

      <BookingTypeModal
        open={!!picked}
        onClose={() => setPicked(null)}
        onPickVisualDay={() => picked && navigate(`/courts/${picked.id}/booking`)}
        onPickEvent={() => navigate('/events')}
      />
    </PageShell>
  );
}
