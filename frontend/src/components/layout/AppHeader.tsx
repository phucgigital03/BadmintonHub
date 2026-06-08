import { useNavigate } from 'react-router-dom';

/** Dark-green top bar with a centered title and a back chevron (alobo style). */
export function AppHeader({ title, onBack }: { title: string; onBack?: () => void }) {
  const navigate = useNavigate();
  return (
    <header className="sticky top-0 z-20 flex items-center bg-brand-header px-4 py-4 shadow-md">
      <button
        onClick={() => (onBack ? onBack() : navigate(-1))}
        className="absolute left-3 rounded-full p-1.5 text-white hover:bg-white/10"
        aria-label="back"
      >
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
          <path d="M15 18l-6-6 6-6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      <h1 className="mx-auto text-xl font-bold text-white">{title}</h1>
    </header>
  );
}
