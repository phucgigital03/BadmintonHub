import type { ReactNode } from 'react';
import { AppHeader } from './AppHeader';

/** Header + centered content container used by most screens. */
export function PageShell({
  title,
  onBack,
  children,
  maxWidth = 'max-w-5xl',
}: {
  title: string;
  onBack?: () => void;
  children: ReactNode;
  maxWidth?: string;
}) {
  return (
    <div className="min-h-screen">
      <AppHeader title={title} onBack={onBack} />
      <main className={`mx-auto w-full ${maxWidth} px-4 py-5`}>{children}</main>
    </div>
  );
}
