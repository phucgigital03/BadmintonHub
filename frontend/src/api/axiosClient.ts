import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '../store/authStore';

const BASE_URL = import.meta.env.VITE_API_URL as string;

/**
 * Single axios instance for all backend calls (through the API gateway :3000).
 * - Request interceptor attaches the Bearer access token from authStore.
 * - Response interceptor performs a silent refresh on 401 (cookie-based
 *   POST /api/auth/refresh) then retries the original request once.
 */
const axiosClient = axios.create({
  baseURL: BASE_URL,
  withCredentials: true, // send the HttpOnly refresh cookie
});

axiosClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Coalesce concurrent refreshes into one in-flight promise.
let refreshing: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  try {
    const res = await axios.post(
      `${BASE_URL}/api/auth/refresh`,
      {},
      { withCredentials: true },
    );
    const token: string = res.data.accessToken;
    useAuthStore.getState().setAccessToken(token);
    return token;
  } catch {
    useAuthStore.getState().clearAuth();
    return null;
  }
}

axiosClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const isAuthCall = original?.url?.includes('/api/auth/');

    if (error.response?.status === 401 && original && !original._retry && !isAuthCall) {
      original._retry = true;
      refreshing = refreshing ?? refreshAccessToken();
      const token = await refreshing;
      refreshing = null;
      if (token) {
        original.headers.Authorization = `Bearer ${token}`;
        return axiosClient(original);
      }
    }
    return Promise.reject(error);
  },
);

export default axiosClient;
