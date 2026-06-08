import axiosClient from './axiosClient';
import type { Court, TimeSlot } from '../types';

export interface CourtSearchParams {
  district?: string;
  type?: string;
  date?: string;
}

export const courtsApi = {
  list: (params: CourtSearchParams = {}) =>
    axiosClient.get<Court[]>('/api/courts', { params }).then((r) => r.data),

  dayGrid: (courtId: string, date: string) =>
    axiosClient
      .get<TimeSlot[]>(`/api/courts/${courtId}/slots`, { params: { date } })
      .then((r) => r.data),
};
