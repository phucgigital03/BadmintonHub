import { useRef, useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import type { PaymentInfo } from '../../types';
import { useCountdown } from '../../hooks/useCountdown';
import { Button } from '../ui/Button';
import { formatVnd } from '../../lib/cn';
import { dmy } from '../../lib/format';

/** Bank-QR payment screen — matches alobo "Thanh toán" + payment.md flow. */
export function PaymentScreen({ payment, onDone }: { payment: PaymentInfo; onDone: () => void }) {
  const { t } = useTranslation();
  const { mmss, expired } = useCountdown(payment.expiresAt);
  const [proof, setProof] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const qrValue = `${payment.bankName}|${payment.accountNumber}|${payment.amount}|#${payment.orderCode}`;

  const copy = () => {
    navigator.clipboard?.writeText(payment.accountNumber);
    toast.success('Đã copy số tài khoản');
  };

  const onPickFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f) setProof(URL.createObjectURL(f));
  };

  const confirm = () => {
    // payment-service is Day 8 — this screen is a DEMO. The booking is already created (PENDING) and
    // is holding the slots; here we just continue to "Lịch đặt của tôi".
    toast.success('Demo thanh toán — đơn đã được giữ chỗ. Xem ở "Lịch đặt của tôi".');
    onDone();
  };

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
              <QRCodeSVG value={qrValue} size={104} />
            </div>
          </div>

          <div className="mt-3 rounded-lg bg-emerald-500/90 px-3 py-2.5 text-center text-sm font-medium text-white">
            ⚠️ {t('payment.transferNote', { amount: formatVnd(payment.amount) })}
          </div>
          <p className="mt-2 text-center text-sm font-medium text-gray-700">{t('payment.holdNote')}</p>

          <p className="mt-3 text-center text-sm">{t('payment.heldFor')}</p>
          <p className={`text-center text-2xl font-bold ${expired ? 'text-red-500' : 'text-gray-800'}`}>
            {expired ? '00:00' : mmss}
          </p>

          <button
            onClick={() => fileRef.current?.click()}
            className="mx-auto mt-3 flex h-44 w-full max-w-xs flex-col items-center justify-center rounded-xl border border-gray-200 bg-white text-gray-400 hover:border-emerald-400"
          >
            {proof ? (
              <img src={proof} alt="proof" className="h-full w-full rounded-xl object-contain" />
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
            <Row icon="👤" label="Tên" value={payment.customerName ?? '-'} />
            <Row icon="📞" label="SĐT" value={payment.customerPhone ?? '-'} />
            <Row icon="🏆" label={t('payment.orderCode')} value={`#${payment.orderCode}`} />
            <Row
              icon="📅"
              label={t('payment.orderDetail')}
              value={`${payment.date ? dmy(payment.date) : ''}${payment.detail ? ' — ' + payment.detail : ''}`}
            />
            <Row icon="💵" label={t('payment.orderTotal')} value={formatVnd(payment.amount)} />
            <Row icon="🪙" label={t('payment.amountDue')} value={formatVnd(payment.amount)} bold />
          </dl>
        </div>
      </div>

      <Button variant="gold" size="lg" fullWidth className="mt-6" onClick={confirm}>
        {t('payment.confirmBooking')}
      </Button>
      <p className="mt-2 text-center text-xs text-gray-500">
        ⓘ Thanh toán Bank QR đang phát triển (Day 8). Đơn đã được <b>giữ chỗ</b> ngay khi tạo — đây là màn demo.
      </p>
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
