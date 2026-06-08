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
  totalAmount: () => get().selected.length * 0.5 * (get().court?.pricePerHour ?? 0),
}));
