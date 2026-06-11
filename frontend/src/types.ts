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

// Spring Data Page<T> JSON shape (GET /api/users etc.)
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export type SlotStatus = 'AVAILABLE' | 'RESERVED' | 'BLOCKED' | 'EVENT';

// A single sport offered by the club, with its own price + list of physical courts (Sân).
export interface ClubSport {
  sport: string; // "Pickleball" | "Badminton"
  pricePerHour: number;
  courts: string[]; // Sân names for this sport, e.g. ["Sân 1", "Sân 2", "Sân 3"]
}

// The (single) club / venue. Holds N sports, each with its own courts + price.
export interface Club {
  id: string;
  name: string;
  address: string;
  district: string;
  rating: number;
  lat: number;
  lng: number;
  sports: ClubSport[];
  imageUrl?: string;
}

// "Booking context" = club + a chosen sport. Carries the sport's price + court list
// downstream (bookingStore, grid, confirm). Built from Club + ClubSport on sport pick.
export interface Court {
  id: string;
  name: string;
  club: string;
  address: string;
  district: string;
  type: string; // the chosen sport, e.g. "Pickleball"
  pricePerHour: number; // price of the chosen sport
  rating: number;
  lat: number;
  lng: number;
  courts?: string[]; // Sân names of the chosen sport (grid rows)
  imageUrl?: string;
}

export interface TimeSlot {
  slotId?: string; // time_slots.id — needed to build booking items; absent on mock grids
  courtId: string;
  courtName: string;
  start: string; // "HH:mm"
  end: string;
  status: SlotStatus;
  price?: number; // real per-30min-cell price from court-service; absent on mock grids
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
