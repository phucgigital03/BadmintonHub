import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

/** Centered auth card on the brand background. */
export function AuthShell({ title, children }: { title: string; children: ReactNode }) {
  const { t } = useTranslation();
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4">
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
