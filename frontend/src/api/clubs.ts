import axiosClient from './axiosClient';
import type { Club, ClubSport, Court, Page, TimeSlot } from '../types';

// ── Raw backend DTO shapes (court-service, via gateway :3000) ────────────────
export type SportEnum = 'PICKLEBALL' | 'BADMINTON';
export type CourtTypeEnum = 'SYNTHETIC' | 'WOOD' | 'CONCRETE';
export type DayTypeEnum = 'WEEKDAY' | 'WEEKEND';
export type CustomerTypeEnum = 'FIXED' | 'WALK_IN';
export type SlotStatusEnum = 'AVAILABLE' | 'RESERVED' | 'BLOCKED' | 'EVENT';

export interface ClubResponse {
  id: string;
  name: string;
  address: string;
  district: string;
  latitude: number | null;
  longitude: number | null;
  images: string[] | null;
  rating: number | null;
  totalReviews: number;
  isActive: boolean;
}

export interface CourtResponse {
  id: string;
  clubId: string;
  courtNumber: string;
  sport: SportEnum;
  type: CourtTypeEnum;
  isActive: boolean;
}

export interface PricingRuleResponse {
  id: string;
  sport: SportEnum;
  dayType: DayTypeEnum;
  startTime: string; // "HH:mm:ss"
  endTime: string; // "HH:mm:ss"
  customerType: CustomerTypeEnum;
  pricePerHour: number;
}

interface SlotResponse {
  id: string;
  date: string;
  startTime: string; // "HH:mm:ss"
  endTime: string;
  status: SlotStatusEnum;
  price: number | null;
  eventId: string | null;
  bookingId: string | null;
  matchId: string | null;
  enrollmentId: string | null;
}

interface CourtSlotsResponse {
  id: string;
  courtNumber: string;
  sport: SportEnum;
  type: CourtTypeEnum;
  slots: SlotResponse[];
}

interface ClubGridResponse {
  date: string;
  dayType: DayTypeEnum;
  courts: CourtSlotsResponse[];
}

// ── sport label <-> enum (FE shows "Pickleball"; backend wants PICKLEBALL) ───
const LABEL_BY_ENUM: Record<SportEnum, string> = {
  PICKLEBALL: 'Pickleball',
  BADMINTON: 'Badminton',
};
export function toSportLabel(e: SportEnum): string {
  return LABEL_BY_ENUM[e] ?? e;
}
export function toSportEnum(label: string): SportEnum {
  return label.trim().toUpperCase() === 'BADMINTON' ? 'BADMINTON' : 'PICKLEBALL';
}

const hhmm = (t: string) => t.slice(0, 5); // "05:00:00" → "05:00"

// ── mutation request bodies ──────────────────────────────────────────────────
export interface CreateClubBody {
  name: string;
  address: string;
  district: string;
  latitude?: number;
  longitude?: number;
  images?: string[];
}
export interface CreateCourtBody {
  courtNumber: string;
  sport: SportEnum;
  type: CourtTypeEnum;
}
export interface CreatePricingBody {
  sport: SportEnum;
  dayType: DayTypeEnum;
  startTime: string; // "HH:mm"
  endTime: string;
  customerType: CustomerTypeEnum;
  pricePerHour: number;
}

export const clubsApi = {
  // ── public GET ──────────────────────────────────────────────────────────
  /** Raw club list (admin club picker). */
  listClubs: () =>
    axiosClient.get<Page<ClubResponse>>('/api/clubs').then((r) => r.data.content),

  /** Assemble the FE `Club` (with `sports[]`) from clubs + courts + pricing. */
  async getClubWithSports(): Promise<Club> {
    const page = (await axiosClient.get<Page<ClubResponse>>('/api/clubs')).data;
    const c = page.content[0];
    if (!c) throw new Error('NO_CLUB');

    const courts = (await axiosClient.get<CourtResponse[]>(`/api/clubs/${c.id}/courts`)).data;

    // group courts by sport (preserve courtNumber order from the API)
    const bySport = new Map<SportEnum, string[]>();
    for (const ct of courts) {
      const names = bySport.get(ct.sport) ?? [];
      names.push(ct.courtNumber);
      bySport.set(ct.sport, names);
    }

    // representative tile price per sport = the cheapest hourly rate ("từ …")
    const sports: ClubSport[] = await Promise.all(
      [...bySport.entries()].map(async ([sp, courtNames]) => {
        let pricePerHour = 0;
        try {
          const rules = (
            await axiosClient.get<PricingRuleResponse[]>(`/api/clubs/${c.id}/pricing`, {
              params: { sport: sp },
            })
          ).data;
          const prices = rules.map((r) => Number(r.pricePerHour)).filter((n) => n > 0);
          if (prices.length) pricePerHour = Math.min(...prices);
        } catch {
          /* no pricing yet → 0 */
        }
        return { sport: toSportLabel(sp), pricePerHour, courts: courtNames };
      }),
    );

    return {
      id: c.id,
      name: c.name,
      address: c.address,
      district: c.district,
      rating: Number(c.rating ?? 0),
      lat: Number(c.latitude ?? 0),
      lng: Number(c.longitude ?? 0),
      sports,
      imageUrl: c.images?.[0],
    };
  },

  /** The visual day-booking grid → flat `TimeSlot[]` (carries the real per-cell price). */
  dayGrid: (clubId: string, date: string, sportLabel: string): Promise<TimeSlot[]> =>
    axiosClient
      .get<ClubGridResponse>(`/api/clubs/${clubId}/slots`, {
        params: { date, sport: toSportEnum(sportLabel) },
      })
      .then((r) =>
        r.data.courts.flatMap((court) =>
          court.slots.map<TimeSlot>((s) => ({
            slotId: s.id,
            courtId: court.id,
            courtName: court.courtNumber,
            start: hhmm(s.startTime),
            end: hhmm(s.endTime),
            status: s.status,
            price: s.price != null ? Number(s.price) : undefined,
          })),
        ),
      ),

  /** Raw pricing rules for a sport (price table). */
  pricing: (clubId: string, sportLabel: string) =>
    axiosClient
      .get<PricingRuleResponse[]>(`/api/clubs/${clubId}/pricing`, {
        params: { sport: toSportEnum(sportLabel) },
      })
      .then((r) => r.data),

  // ── STAFF/ADMIN mutations (Bearer auto-attached by axiosClient) ──────────
  createClub: (body: CreateClubBody) =>
    axiosClient.post<ClubResponse>('/api/clubs', body).then((r) => r.data),

  addCourt: (clubId: string, body: CreateCourtBody) =>
    axiosClient.post<CourtResponse>('/api/courts', body, { params: { clubId } }).then((r) => r.data),

  createPricing: (clubId: string, body: CreatePricingBody) =>
    axiosClient.post<PricingRuleResponse>(`/api/clubs/${clubId}/pricing`, body).then((r) => r.data),

  generateSlots: (clubId: string) =>
    axiosClient
      .post<{ created: number; from: string; to: string }>(`/api/clubs/${clubId}/generate-slots`)
      .then((r) => r.data),

  blockSlot: (slotId: string) =>
    axiosClient.patch<void>(`/api/courts/slots/${slotId}/block`).then((r) => r.data),
};

/** Build the "booking context" (Court = club + a chosen sport) used by the grid + bookingStore. */
export function clubSportToCourt(club: Club, sport: ClubSport): Court {
  return {
    id: club.id,
    name: club.name,
    club: club.name,
    address: club.address,
    district: club.district,
    type: sport.sport,
    pricePerHour: sport.pricePerHour,
    rating: club.rating,
    lat: club.lat,
    lng: club.lng,
    courts: sport.courts,
    imageUrl: club.imageUrl,
  };
}
