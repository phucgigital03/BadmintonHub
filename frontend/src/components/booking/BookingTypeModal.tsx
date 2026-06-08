import { useTranslation } from 'react-i18next';
import { Modal } from '../ui/Modal';

interface Props {
  open: boolean;
  onClose: () => void;
  onPickVisualDay: () => void;
  onPickEvent: () => void;
}

/** "Chọn hình thức đặt" — visual-day (green) vs event (pink + New). */
export function BookingTypeModal({ open, onClose, onPickVisualDay, onPickEvent }: Props) {
  const { t } = useTranslation();
  return (
    <Modal open={open} onClose={onClose} title={t('booking.chooseType')}>
      <div className="space-y-4">
        <button
          onClick={onPickVisualDay}
          className="group relative w-full rounded-2xl bg-green-100 p-5 text-left transition-colors hover:bg-green-200"
        >
          <h3 className="text-xl font-bold text-green-800">{t('booking.visualDay')}</h3>
          <p className="mt-1 pr-12 text-green-900/80">{t('booking.visualDayDesc')}</p>
          <span className="absolute bottom-4 right-4 flex h-9 w-12 items-center justify-center rounded-lg bg-green-600 text-white transition-transform group-hover:translate-x-1">
            →
          </span>
        </button>

        <button
          onClick={onPickEvent}
          className="group relative w-full rounded-2xl bg-pink-100 p-5 text-left transition-colors hover:bg-pink-200"
        >
          <span className="absolute right-4 top-3 rounded-full bg-pink-500 px-2 py-0.5 text-xs font-bold text-white">
            New
          </span>
          <h3 className="text-xl font-bold text-pink-700">{t('booking.event')}</h3>
          <p className="mt-1 pr-12 text-pink-900/80">{t('booking.eventDesc')}</p>
          <span className="absolute bottom-4 right-4 flex h-9 w-12 items-center justify-center rounded-lg bg-pink-500 text-white transition-transform group-hover:translate-x-1">
            →
          </span>
        </button>
      </div>
    </Modal>
  );
}
