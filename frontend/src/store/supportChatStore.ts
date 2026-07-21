import { create } from 'zustand';

/**
 * A tiny event channel from the AI booking widget → the STAFF support widget (CustomerChatWidget).
 * The two widgets stay separate components/UIs; this store is only the "please open the staff chat,
 * optionally with this first message" signal used when the AI agent escalates (UC-CS-07, FE-driven).
 * `openTick` is a monotonic counter so repeated escalations always re-trigger the effect.
 */
interface SupportChatState {
  openTick: number;
  prefill: string | null;
  requestOpen: (prefill?: string | null) => void;
  consumed: () => void;
}

export const useSupportChatStore = create<SupportChatState>((set) => ({
  openTick: 0,
  prefill: null,
  requestOpen: (prefill = null) => set((s) => ({ openTick: s.openTick + 1, prefill })),
  consumed: () => set({ prefill: null }),
}));
