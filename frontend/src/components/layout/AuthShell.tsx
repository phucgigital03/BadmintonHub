import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

/** Centered auth card on the brand background, with a back-to-home button. */
export function AuthShell({ title, children }: { title: string; children: ReactNode }) {
  const { t } = useTranslation();
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center px-4">
      <Link
        to="/"
        className="absolute left-4 top-4 inline-flex items-center gap-1 rounded-full px-3 py-2 text-sm text-white/90 hover:bg-white/10"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
          <path d="M15 18l-6-6 6-6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        {t('common.back')}
      </Link>

      <Link to="/" className="mb-6 flex items-center gap-2 text-2xl font-extrabold">
        <span className="text-brand-accent">🏸</span> {t('app.name')}
      </Link>
      <div className="w-full max-w-md rounded-2xl bg-brand-panel p-6 shadow-lg">
        <h1 className="mb-5 text-center text-2xl font-bold">{title}</h1>
        {children}
      </div>
    </div>
  );
}
