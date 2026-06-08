// ── Domain types (aligned with backend contracts + mock shapes) ─────────────

export type Role = 'ROLE_USER' | 'ROLE_COACH' | 'ROLE_STAFF' | 'ROLE_ADMIN';

export interface User {
  id: string;
  email: string;
  fullName: string;
  roles: string[];
  isEmailVerified: boolean;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export type SlotStatus = 'AVAILABLE' | 'RESERVED' | 'BLOCKED' | 'EVENT';

export interface Court {
  id: string;
  name: string;
  club: string;
  address: string;
  district: string;
  type: string; // e.g. "Pickleball"
  pricePerHour: number;
  rating: number;
  lat: number;
  lng: number;
  imageUrl?: string;
}

export interface TimeSlot {
  courtId: string;
  courtName: string;
  start: string; // "HH:mm"
  end: string;
  status: SlotStatus;
}

export type EventType = 'SOCIAL' | 'COMPETITIVE';

export interface EventItem {
  id: number;
  type: EventType;
  date: string; // ISO date
  startTime: string; // "HH:mm"
  endTime: string;
  courts: string[]; // ["Sân 1", "Sân 2"]
  sport: string; // "Pickleball"
  skillFrom: string; // "1.0"
  skillTo: string; // "2.5"
  filledSlots: number;
  totalSlots: number;
  ticketPrice: number;
}

export type MatchStatus = 'PENDING_PAYMENT' | 'OPEN' | 'FULL' | 'COMPLETED' | 'CANCELLED';

export interface Match {
  id: string;
  title: string;
  date: string;
  startTime: string;
  endTime: string;
  courtName: string;
  skillLevel: string;
  filledSlots: number;
  totalSlots: number;
  pricePerPerson: number;
  status: MatchStatus;
}

export interface Coach {
  id: string;
  fullName: string;
  specialty: string;
  hourlyRate: number;
  rating: number;
  bio: string;
  avatarUrl?: string;
}

export type PaymentType =
  | 'BOOKING'
  | 'MATCH_HOST'
  | 'MATCH_PLAYER'
  | 'COACH_ENROLLMENT'
  | 'EVENT_TICKET';

export interface PaymentInfo {
  paymentId: string;
  orderCode: number;
  paymentType: PaymentType;
  bankName: string;
  accountNumber: string;
  accountName: string;
  amount: number;
  expiresAt: string; // ISO timestamp
  // booking summary shown on the right card
  customerName?: string;
  customerPhone?: string;
  detail?: string; // "Pickleball 2: 6h00 - 7h00"
  date?: string;
}
