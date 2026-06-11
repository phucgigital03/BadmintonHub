import { create } from 'zustand';
import type { Court, TimeSlot } from '../types';

function key(s: TimeSlot) {
  return `${s.courtId}|${s.start}`;
}

interface BookingState {
  court?: Court;
  date: string; // yyyy-mm-dd
  selected: TimeSlot[];
  customerName: string;
  customerPhone: string;
  note: string;

  setCourt: (court: Court) => void;
  setDate: (date: string) => void;
  toggleSlot: (slot: TimeSlot) => void;
  isSelected: (slot: TimeSlot) => boolean;
  clearSelection: () => void;
  setCustomer: (name: string, phone: string, note: string) => void;

  totalHours: () => number;
  totalAmount: () => number;
}

export const useBookingStore = create<BookingState>((set, get) => ({
  court: undefined,
  date: new Date().toISOString().slice(0, 10),
  selected: [],
  customerName: '',
  customerPhone: '',
  note: '',

  setCourt: (court) => set({ court }),
  setDate: (date) => set({ date, selected: [] }),
  toggleSlot: (slot) =>
    set((st) => {
      const exists = st.selected.some((s) => key(s) === key(slot));
      return {
        selected: exists
          ? st.selected.filter((s) => key(s) !== key(slot))
          : [...st.selected, slot],
      };
    }),
  isSelected: (slot) => get().selected.some((s) => key(s) === key(slot)),
  clearSelection: () => set({ selected: [] }),
  setCustomer: (customerName, customerPhone, note) => set({ customerName, customerPhone, note }),

  totalHours: () => get().selected.length * 0.5,
  // Sum the real per-cell price from court-service; fall back per-slot to half the
  // hourly rate when a slot carries no price (e.g. a mock grid or an unpriced cell).
  totalAmount: () => {
    const pph = get().court?.pricePerHour ?? 0;
    return get().selected.reduce(
      (sum, s) => sum + (typeof s.price === 'number' ? s.price : 0.5 * pph),
      0,
    );
  },
}));
