import { useState } from 'react';
import { useNotificationStore } from '../../store/notificationStore';
import { cn } from '../../lib/cn';

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const { items, unread, markAllRead } = useNotificationStore();
  const count = unread();

  return (
    <div className="relative">
      <button
        onClick={() => {
          setOpen((o) => !o);
          if (!open) markAllRead();
        }}
        className="relative rounded-full p-2 hover:bg-white/10"
        aria-label="notifications"
      >
        <span className="text-xl" aria-hidden>🔔</span>
        {count > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-bold text-white">
            {count}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 z-30 mt-2 w-72 rounded-xl bg-white p-2 text-gray-800 shadow-xl">
          {items.length === 0 ? (
            <p className="p-3 text-sm text-gray-500">Không có thông báo.</p>
          ) : (
            items.map((n) => (
              <div
                key={n.id}
                className={cn('rounded-lg px-3 py-2 text-sm', n.read ? 'text-gray-600' : 'bg-emerald-50 font-medium')}
              >
                {n.title}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
