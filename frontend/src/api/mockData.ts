// Mock fallback data — shown ONLY in the React Query `isError` branch while the
// backend for a feature does not exist yet (see frontend.md graceful-degradation
// rule). Each list has 2–3 items, matching the real response shape. Mutations are
// NEVER mocked — they still surface real errors.

import type { Coach, EventItem, Match, PaymentInfo } from '../types';

// NOTE: court/club data is NO LONGER mocked — CourtsPage/CourtDayBookingPage/PriceTablePage
// call the real court-service (see api/clubs.ts). Only events/matches/coaches/payment below
// remain mock until their backends exist.

export const mockEvents: EventItem[] = [
  {
    id: 2748,
    type: 'SOCIAL',
    date: '2026-06-04',
    startTime: '19:00',
    endTime: '22:00',
    courts: ['Sân 1', 'Sân 2'],
    sport: 'Pickleball',
    skillFrom: '1.0',
    skillTo: '2.5',
    filledSlots: 0,
    totalSlots: 16,
    ticketPrice: 60000,
  },
  {
    id: 2749,
    type: 'SOCIAL',
    date: '2026-06-04',
    startTime: '20:00',
    endTime: '22:00',
    courts: ['Sân 3', 'Sân 4', 'Sân 5'],
    sport: 'Pickleball',
    skillFrom: '1.0',
    skillTo: '2.5',
    filledSlots: 0,
    totalSlots: 24,
    ticketPrice: 40000,
  },
  {
    id: 2746,
    type: 'SOCIAL',
    date: '2026-06-06',
    startTime: '05:00',
    endTime: '08:00',
    courts: ['Sân 1', 'Sân 2', 'Sân 3', 'Sân 4', 'Sân 5'],
    sport: 'Pickleball',
    skillFrom: '1.0',
    skillTo: '2.5',
    filledSlots: 0,
    totalSlots: 40,
    ticketPrice: 50000,
  },
];

export const mockMatches: Match[] = [
  {
    id: 'm1',
    title: 'Giao lưu Pickleball tối T6',
    date: '2026-06-12',
    startTime: '19:00',
    endTime: '21:00',
    courtName: 'An Bình - Sân 2',
    skillLevel: '2.0 → 3.0',
    filledSlots: 3,
    totalSlots: 8,
    pricePerPerson: 50000,
    status: 'OPEN',
  },
  {
    id: 'm2',
    title: 'Cầu lông sáng cuối tuần',
    date: '2026-06-13',
    startTime: '06:00',
    endTime: '08:00',
    courtName: 'An Bình - Sân 1',
    skillLevel: 'Mọi trình độ',
    filledSlots: 6,
    totalSlots: 6,
    pricePerPerson: 40000,
    status: 'FULL',
  },
];

export const mockCoaches: Coach[] = [
  {
    id: 'co1',
    fullName: 'HLV Trần Quốc Phú',
    specialty: 'Pickleball cơ bản → nâng cao',
    hourlyRate: 250000,
    rating: 4.9,
    bio: '8 năm kinh nghiệm, chứng chỉ huấn luyện viên quốc gia.',
  },
  {
    id: 'co2',
    fullName: 'HLV Nguyễn Thị Mai',
    specialty: 'Cầu lông thiếu nhi',
    hourlyRate: 200000,
    rating: 4.7,
    bio: 'Chuyên đào tạo lứa tuổi 6–15, phương pháp vui học.',
  },
];

export const mockPayment: PaymentInfo = {
  paymentId: 'pay-184',
  orderCode: 184,
  paymentType: 'BOOKING',
  bankName: 'Ngân hàng Shinhan Việt Nam',
  accountNumber: '0962728894',
  accountName: 'Trần Quốc Phú',
  amount: 100000,
  expiresAt: new Date(Date.now() + 6 * 60 * 1000).toISOString(),
  customerName: 'Phúc',
  customerPhone: '0399158632',
  detail: 'Pickleball 2: 6h00 - 7h00',
  date: '2026-06-06',
};
