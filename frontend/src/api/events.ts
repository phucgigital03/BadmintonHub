import axiosClient from './axiosClient';
import type { EventItem } from '../types';

export interface EventSearchParams {
  from?: string;
  to?: string;
}

export const eventsApi = {
  list: (params: EventSearchParams = {}) =>
    axiosClient.get<EventItem[]>('/api/events', { params }).then((r) => r.data),

  get: (id: number) => axiosClient.get<EventItem>(`/api/events/${id}`).then((r) => r.data),
};
