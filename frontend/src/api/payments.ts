import axiosClient from './axiosClient';
import type { Page, PaymentType } from '../types';

// ── payment-service DTO shapes (via gateway :3000) ───────────────────────────
export type PaymentStatus = 'PENDING' | 'PROOF_SUBMITTED' | 'CONFIRMED' | 'EXPIRED' | 'REFUNDED';

/** Mirrors payment-service PaymentResponse. `orderCode` already includes the leading "#" (e.g. "#184"). */
export interface PaymentResponse {
  id: string;
  orderCode: string;
  paymentType: PaymentType;
  status: PaymentStatus;
  amount: number;
  refundAmount: number | null;
  bookingId: string | null;
  matchId: string | null;
  enrollmentId: string | null;
  userId: string;
  expiresAt: string; // ISO
  bankName: string;
  accountNumber: string;
  accountName: string;
  qrImageUrl: string | null;
  createdAt: string; // ISO
  refundRequired: boolean;
  refundRequiredAmount: number | null;
}

export interface InitiatePaymentBody {
  paymentType: PaymentType; // 'BOOKING' for court bookings
  bookingId?: string;
  matchId?: string;
  enrollmentId?: string;
  amount?: number; // ignored by the server for BOOKING (it uses booking.totalPrice) — kept for other types
}

export interface RefundBody {
  amount: number;
  toBankName: string;
  toAccountNumber: string;
  toAccountName: string;
  refundNote?: string;
}

const STATUS_LABEL: Record<PaymentStatus, string> = {
  PENDING: 'Chờ chuyển khoản',
  PROOF_SUBMITTED: 'Đã nộp chứng từ — chờ duyệt',
  CONFIRMED: 'Đã xác nhận',
  EXPIRED: 'Hết hạn / từ chối',
  REFUNDED: 'Đã hoàn tiền',
};
export const paymentStatusLabel = (s: PaymentStatus) => STATUS_LABEL[s];

/** Mirrors payment-service PaymentProofResponse — a transfer screenshot uploaded against a payment. */
export interface PaymentProofResponse {
  imageUrl: string;
  uploadedAt: string; // ISO
  reviewedBy: string | null;
  reviewedAt: string | null; // ISO
  reviewNote: string | null;
}

export const paymentsApi = {
  /** Open (or reuse — idempotent) the active payment for a booking. Returns real bank/QR/orderCode/expiresAt. */
  initiate: (body: InitiatePaymentBody) =>
    axiosClient.post<PaymentResponse>('/api/payments/initiate', body).then((r) => r.data),

  getById: (id: string) =>
    axiosClient.get<PaymentResponse>(`/api/payments/${id}`).then((r) => r.data),

  /** Proof screenshots for a payment (newest first) — owner or STAFF/ADMIN. */
  getProofs: (id: string) =>
    axiosClient.get<PaymentProofResponse[]>(`/api/payments/${id}/proofs`).then((r) => r.data),

  /** Caller's own payments, paged. */
  listMine: (page = 0, size = 20) =>
    axiosClient
      .get<Page<PaymentResponse>>('/api/payments', { params: { page, size } })
      .then((r) => r.data),

  /** Upload the bank-transfer screenshot (multipart field name = "file"). */
  submitProof: (id: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return axiosClient
      .post<PaymentResponse>(`/api/payments/${id}/proof`, form)
      .then((r) => r.data);
  },

  // ── STAFF/ADMIN ──
  confirm: (id: string) =>
    axiosClient.post<PaymentResponse>(`/api/payments/${id}/confirm`).then((r) => r.data),

  reject: (id: string, reason?: string) =>
    axiosClient
      .post<PaymentResponse>(`/api/payments/${id}/reject`, reason ? { reason } : {})
      .then((r) => r.data),

  refund: (id: string, body: RefundBody) =>
    axiosClient.post<PaymentResponse>(`/api/payments/${id}/refund`, body).then((r) => r.data),

  /** PROOF_SUBMITTED queue (oldest first) — the confirm/reject queue. */
  listPendingReview: (page = 0, size = 50) =>
    axiosClient
      .get<Page<PaymentResponse>>('/api/payments/pending-review', { params: { page, size } })
      .then((r) => r.data),

  /** Payments flagged for a manual refund. */
  listRefundRequired: (page = 0, size = 50) =>
    axiosClient
      .get<Page<PaymentResponse>>('/api/payments/refund-required', { params: { page, size } })
      .then((r) => r.data),
};
