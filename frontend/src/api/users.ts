import axiosClient from './axiosClient';
import type { User, Page } from '../types';

export interface UpdateUserPayload {
  fullName?: string;
  phone?: string;
}

/** user-service /api/users — all calls require a Bearer token (defense-in-depth re-validation). */
export const usersApi = {
  getById: (id: string) =>
    axiosClient.get<User>(`/api/users/${id}`).then((r) => r.data),

  update: (id: string, p: UpdateUserPayload) =>
    axiosClient.patch<User>(`/api/users/${id}`, p).then((r) => r.data),

  // STAFF/ADMIN only — returns a Spring Data page.
  list: (page = 0, size = 20) =>
    axiosClient
      .get<Page<User>>('/api/users', { params: { page, size } })
      .then((r) => r.data),

  // ADMIN only — soft delete (sets deletedAt).
  remove: (id: string) => axiosClient.delete(`/api/users/${id}`).then((r) => r.data),
};
