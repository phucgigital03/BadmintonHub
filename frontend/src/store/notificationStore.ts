import { create } from 'zustand';
import { uuid } from '../lib/uuid';

export interface Notif {
  id: string;
  title: string;
  read: boolean;
}

interface NotificationState {
  items: Notif[];
  unread: () => number;
  add: (title: string) => void;
  markAllRead: () => void;
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  items: [
    { id: 'n1', title: 'Chào mừng đến BadmintonHub!', read: false },
    { id: 'n2', title: 'Xác thực email để đặt sân & tham gia trận.', read: false },
  ],
  unread: () => get().items.filter((n) => !n.read).length,
  add: (title) =>
    set((s) => ({ items: [{ id: uuid(), title, read: false }, ...s.items] })),
  markAllRead: () => set((s) => ({ items: s.items.map((n) => ({ ...n, read: true })) })),
}));
