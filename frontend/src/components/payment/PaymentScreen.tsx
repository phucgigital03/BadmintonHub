import { useRef, useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import type { AxiosError } from 'axios';
import { paymentsApi, paymentStatusLabel, type PaymentResponse } from '../../api/payments';
import { useCountdown } from '../../hooks/useCountdown';
import { Button } from '../ui/Button';
import { formatVnd } from '../../lib/cn';
import { dmy } from '../../lib/format';

export interface PaymentSummary {
  customerName?: string;
  customerPhone?: string;
  detail?: string; // "Sân 2 10h00-10h30, …"
  date?: string; // "yyyy-MM-dd"
}

const isHttpUrl = (u: string | null): u is string => !!u && /^https?:\/\//.test(u);

/** Bank-QR payment screen wired to payment-service: upload proof → poll until STAFF confirm/reject. */
export function PaymentScreen({
  payment: initial,
  summary,
  onDone,
}: {
  payment: PaymentResponse;
  summary?: PaymentSummary;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  // Live payment: seeded with the initiate result, polled while it can still change (PENDING / awaiting STAFF).
  const { data: payment = initial } = useQuery({
    queryKey: ['payment', initial.id],
    queryFn: () => paymentsApi.getById(initial.id),
    initialData: initial,
    refetchInterval: (query) => {
      const s = query.state.data?.status;
      return s === 'PENDING' || s === 'PROOF_SUBMITTED' ? 5000 : false;
    },
  });

  const { mmss, expired } = useCountdown(payment.expiresAt);

  const qrValue = `${payment.bankName}|${payment.accountNumber}|${payment.amount}|${payment.orderCode}`;

  const copy = () => {
    navigator.clipboard?.writeText(payment.accountNumber);
    toast.success('Đã copy số tài khoản');
  };

  const onPickFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f) {
      setFile(f);
      setPreview(URL.createObjectURL(f));
    }
  };

  const proofMut = useMutation({
    mutationFn: () => paymentsApi.submitProof(payment.id, file as File),
    onSuccess: (updated) => {
      qc.setQueryData(['payment', payment.id], updated);
      qc.invalidateQueries({ queryKey: ['my-bookings'] });
      toast.success('Đã nộp chứng từ — chờ STAFF duyệt');
    },
    onError: (err: AxiosError<{ message?: string }>) =>
      toast.error(err.response?.data?.message ?? 'Nộp chứng từ thất bại'),
  });

  // ── Terminal states: short status panels instead of the QR form ──
  if (payment.status === 'CONFIRMED') {
    return (
      <ResultPanel icon="✅" tone="ok" title="Đã xác nhận — đặt sân thành công!" onDone={onDone}>
        Thanh toán <b>{payment.orderCode}</b> đã được STAFF xác nhận. Đơn của bạn đã <b>CONFIRMED</b>.
      </ResultPanel>
    );
  }
  if (payment.status === 'REFUNDED') {
    return (
      <ResultPanel icon="↩️" tone="ok" title="Đã hoàn tiền" onDone={onDone}>
        Thanh toán {payment.orderCode} đã được hoàn{' '}
        {payment.refundAmount != null ? <b>{formatVnd(payment.refundAmount)}</b> : null}.
      </ResultPanel>
    );
  }
  if (payment.status === 'EXPIRED') {
    return (
      <ResultPanel icon="⌛" tone="bad" title="Thanh toán đã hết hạn / bị từ chối" onDone={onDone}>
        {payment.refundRequired
          ? 'Hệ thống ghi nhận bạn có thể đã chuyển khoản — đơn đã được đánh dấu chờ STAFF hoàn tiền.'
          : 'Đơn đã được trả ô. Vui lòng đặt lại nếu cần.'}
      </ResultPanel>
    );
  }

  const submitted = payment.status === 'PROOF_SUBMITTED';

  return (
    <div className="rounded-2xl bg-emerald-50 p-4 text-gray-800 sm:p-6">
      <div className="grid gap-5 lg:grid-cols-2">
        {/* Left — bank + QR + upload */}
        <div>
          <div className="flex items-start justify-between gap-4 rounded-xl bg-white p-4 shadow-sm">
            <div>
              <h2 className="mb-2 font-bold">1. {t('payment.bankAccount')}</h2>
              <p className="text-sm"><span className="text-gray-500">{t('payment.accountName')}: </span><b>{payment.accountName}</b></p>
              <p className="text-sm">
                <span className="text-gray-500">{t('payment.accountNumber')}: </span>
                <b>{payment.accountNumber}</b>
                <button onClick={copy} className="ml-2 text-emerald-600 hover:underline" aria-label="copy">⧉</button>
              </p>
              <p className="text-sm"><span className="text-gray-500">{t('payment.bank')}: </span><b>{payment.bankName}</b></p>
            </div>
            <div className="shrink-0 rounded-lg bg-white p-1">
              {isHttpUrl(payment.qrImageUrl) ? (
                <img src={payment.qrImageUrl} alt="QR" className="h-[104px] w-[104px] object-contain" />
              ) : (
                <QRCodeSVG value={qrValue} size={104} />
              )}
            </div>
          </div>

          <div className="mt-3 rounded-lg bg-emerald-500/90 px-3 py-2.5 text-center text-sm font-medium text-white">
            ⚠️ {t('payment.transferNote', { amount: formatVnd(payment.amount) })}
          </div>
          <p className="mt-2 text-center text-sm font-medium text-gray-700">
            Nội dung chuyển khoản: <b>{payment.orderCode}</b>
          </p>

          <p className="mt-3 text-center text-sm">{t('payment.heldFor')}</p>
          <p className={`text-center text-2xl font-bold ${expired ? 'text-red-500' : 'text-gray-800'}`}>
            {expired ? '00:00' : mmss}
          </p>

          <button
            onClick={() => !submitted && fileRef.current?.click()}
            disabled={submitted}
            className="mx-auto mt-3 flex h-44 w-full max-w-xs flex-col items-center justify-center rounded-xl border border-gray-200 bg-white text-gray-400 hover:border-emerald-400 disabled:cursor-not-allowed disabled:opacity-70"
          >
            {preview ? (
              <img src={preview} alt="proof" className="h-full w-full rounded-xl object-contain" />
            ) : (
              <>
                <span className="text-3xl" aria-hidden>🖼️</span>
                <span className="mt-2 text-sm">{t('payment.uploadProof')}</span>
              </>
            )}
          </button>
          <input ref={fileRef} type="file" accept="image/*" className="hidden" onChange={onPickFile} />
        </div>

        {/* Right — booking info */}
        <div className="rounded-xl bg-white p-4 shadow-sm">
          <h2 className="mb-3 font-bold">{t('payment.orderInfo')}</h2>
          <dl className="space-y-3 text-sm">
            <Row icon="👤" label="Tên" value={summary?.customerName ?? '-'} />
            <Row icon="📞" label="SĐT" value={summary?.customerPhone ?? '-'} />
            <Row icon="🏆" label={t('payment.orderCode')} value={payment.orderCode} />
            <Row
              icon="📅"
              label={t('payment.orderDetail')}
              value={`${summary?.date ? dmy(summary.date) : ''}${summary?.detail ? ' — ' + summary.detail : ''}`}
            />
            <Row icon="🪙" label={t('payment.amountDue')} value={formatVnd(payment.amount)} bold />
            <Row icon="📌" label="Trạng thái" value={paymentStatusLabel(payment.status)} />
          </dl>
        </div>
      </div>

      {submitted ? (
        <div className="mt-6 rounded-xl bg-amber-100 px-4 py-3 text-center text-sm font-medium text-amber-800">
          ✔️ Đã nộp chứng từ. Đang chờ STAFF duyệt — màn hình sẽ tự cập nhật khi có kết quả.
        </div>
      ) : (
        <Button
          variant="gold"
          size="lg"
          fullWidth
          className="mt-6"
          disabled={!file || proofMut.isPending}
          onClick={() => proofMut.mutate()}
        >
          {proofMut.isPending ? 'Đang gửi…' : 'Đã chuyển khoản — Nộp chứng từ'}
        </Button>
      )}
      <button onClick={onDone} className="mt-3 w-full text-center text-xs text-gray-500 hover:underline">
        Để sau — xem ở "Lịch đặt của tôi"
      </button>
    </div>
  );
}

function ResultPanel({
  icon,
  tone,
  title,
  children,
  onDone,
}: {
  icon: string;
  tone: 'ok' | 'bad';
  title: string;
  children: React.ReactNode;
  onDone: () => void;
}) {
  return (
    <div className="rounded-2xl bg-white p-8 text-center text-gray-800 shadow-sm">
      <div className="text-5xl" aria-hidden>{icon}</div>
      <h2 className={`mt-3 text-xl font-bold ${tone === 'ok' ? 'text-emerald-600' : 'text-red-500'}`}>{title}</h2>
      <p className="mx-auto mt-2 max-w-md text-sm text-gray-600">{children}</p>
      <Button variant="gold" size="lg" className="mt-6" onClick={onDone}>
        Về "Lịch đặt của tôi"
      </Button>
    </div>
  );
}

function Row({ icon, label, value, bold }: { icon: string; label: string; value: string; bold?: boolean }) {
  return (
    <div className="flex items-start gap-3">
      <span className="mt-0.5 flex h-7 w-7 items-center justify-center rounded-md bg-emerald-100" aria-hidden>{icon}</span>
      <div>
        <dt className="text-xs text-gray-500">{label}</dt>
        <dd className={bold ? 'font-bold text-gray-900' : 'text-gray-800'}>{value}</dd>
      </div>
    </div>
  );
}
