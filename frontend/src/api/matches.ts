import axiosClient from './axiosClient';
import type { Match } from '../types';

export const matchesApi = {
  list: () => axiosClient.get<Match[]>('/api/matches').then((r) => r.data),
  get: (id: string) => axiosClient.get<Match>(`/api/matches/${id}`).then((r) => r.data),
};
