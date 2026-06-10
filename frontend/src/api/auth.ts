import axiosClient from './axiosClient';
import type { AuthResponse, User } from '../types';

export interface RegisterPayload {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export const authApi = {
  register: (p: RegisterPayload) =>
    axiosClient.post<User>('/api/auth/register', p).then((r) => r.data),

  login: (p: LoginPayload) =>
    axiosClient.post<AuthResponse>('/api/auth/login', p).then((r) => r.data),

  googleLogin: (idToken: string) =>
    axiosClient.post<AuthResponse>('/api/auth/google', { idToken }).then((r) => r.data),

  verifyEmail: (token: string) =>
    axiosClient.get('/api/auth/verify-email', { params: { token } }).then((r) => r.data),

  forgotPassword: (email: string) =>
    axiosClient.post('/api/auth/forgot-password', { email }).then((r) => r.data),

  resetPassword: (token: string, newPassword: string) =>
    axiosClient.post('/api/auth/reset-password', { token, newPassword }).then((r) => r.data),

  logout: () => axiosClient.post('/api/auth/logout').then((r) => r.data),
};
