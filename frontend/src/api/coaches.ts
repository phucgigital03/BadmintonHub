import axiosClient from './axiosClient';
import type { Coach } from '../types';

export const coachesApi = {
  list: (params: { specialty?: string } = {}) =>
    axiosClient.get<Coach[]>('/api/coaches', { params }).then((r) => r.data),
  get: (id: string) => axiosClient.get<Coach>(`/api/coaches/${id}`).then((r) => r.data),
};
