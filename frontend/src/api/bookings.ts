import axiosClient from './axiosClient';
import type { Page } from '../types';

// ── booking-service DTO shapes (via gateway :3000) ───────────────────────────
export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';

export interface BookingItemResponse {
  id: string;
  courtId: string;
  slotId: string;
  courtName: string;
  startTime: string; // "HH:mm:ss"
  endTime: string;
  price: number;
}

export interface BookingResponse {
  id: string;
  userId: string;
  clubId: string;
  customerName: string;
  customerPhone: string;
  note: string | null;
  customerType: 'WALK_IN' | 'FIXED';
  bookingDate: string; // "yyyy-MM-dd"
  totalPrice: number;
  refundAmount: number | null;
  status: BookingStatus;
  earliestStartTime: string; // ISO
  holdExpiresAt: string | null; // ISO — PENDING hold deadline (for countdown)
  cancelReason: string | null;
  createdAt: string; // ISO
  items: BookingItemResponse[];
}

export interface CreateBookingBody {
  clubId: string;
  date: string; // "yyyy-MM-dd"
  customerName: string;
  customerPhone: string;
  note?: string;
  items: { courtId: string; slotId: string }[];
}

/** "10:00:00" → "10:00" */
const hhmm = (t: string) => (t ? t.slice(0, 5) : t);
/** Display label for one item, e.g. "Sân 2 10:00-10:30". */
export const bookingItemLabel = (it: BookingItemResponse) =>
  `${it.courtName} ${hhmm(it.startTime)}-${hhmm(it.endTime)}`;

export const bookingsApi = {
  /** Create a PENDING order (needs login + verified email). */
  create: (body: CreateBookingBody) =>
    axiosClient.post<BookingResponse>('/api/bookings', body).then((r) => r.data),

  getById: (id: string) =>
    axiosClient.get<BookingResponse>(`/api/bookings/${id}`).then((r) => r.data),

  /** Caller's own bookings (STAFF/ADMIN: all), paged. */
  listMine: (page = 0, size = 20) =>
    axiosClient
      .get<Page<BookingResponse>>('/api/bookings', { params: { page, size } })
      .then((r) => r.data),

  cancel: (id: string, reason?: string) =>
    axiosClient
      .post<BookingResponse>(`/api/bookings/${id}/cancel`, reason ? { reason } : {})
      .then((r) => r.data),
};
