import { useTranslation } from 'react-i18next';

/** Small banner shown when a page falls back to mock data (API not ready). */
export function MockBanner() {
  const { t } = useTranslation();
  return (
    <div className="mb-4 flex items-center gap-2 rounded-lg border border-amber-300/60 bg-amber-300/15 px-3 py-2 text-sm text-amber-100">
      <span aria-hidden>⚠️</span>
      <span>{t('common.mockBanner')}</span>
    </div>
  );
}
