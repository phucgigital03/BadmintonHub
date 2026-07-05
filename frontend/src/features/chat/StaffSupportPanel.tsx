import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { useAuthStore } from '../../store/authStore';
import { useChatSocket } from '../../hooks/useChatSocket';
import { chatApi } from '../../api/chat';
import { ChatThread } from './ChatThread';
import { Button } from '../../components/ui/Button';
import type { ChatConversation } from '../../types';

function timeLabel(iso: string | null): string {
  if (!iso) return '';
  return new Date(iso).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' });
}

function ConvRow({
  conv,
  active,
  onClick,
  action,
}: {
  conv: ChatConversation;
  active?: boolean;
  onClick?: () => void;
  action?: React.ReactNode;
}) {
  return (
    <div
      onClick={onClick}
      className={[
        'flex cursor-pointer items-center gap-2 border-b border-black/5 px-3 py-2 hover:bg-black/5',
        active ? 'bg-brand-gold/15' : '',
      ].join(' ')}
    >
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between">
          <span className="truncate text-sm font-semibold text-brand-header">{conv.customerName}</span>
          <span className="text-[10px] text-black/40">{timeLabel(conv.lastMessageAt)}</span>
        </div>
        <div className="truncate text-xs text-black/50">{conv.lastMessagePreview ?? 'Chưa có tin nhắn'}</div>
      </div>
      {conv.staffUnread > 0 && (
        <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-[11px] font-bold text-white">
          {conv.staffUnread}
        </span>
      )}
      {action}
    </div>
  );
}

/** Enterprise two-pane support console (STAFF/ADMIN) — rendered from the admin "Hỗ trợ" tab. */
export function StaffSupportPanel() {
  const me = useAuthStore((s) => s.user)!;
  const qc = useQueryClient();
  const { subscribe } = useChatSocket(true);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const inbox = useQuery({ queryKey: ['chat', 'inbox'], queryFn: () => chatApi.list() });
  const queue = useQuery({ queryKey: ['chat', 'queue'], queryFn: () => chatApi.list({ queue: 'unassigned' }) });

  const refetchLists = () => {
    qc.invalidateQueries({ queryKey: ['chat', 'inbox'] });
    qc.invalidateQueries({ queryKey: ['chat', 'queue'] });
  };

  useEffect(() => {
    const off1 = subscribe('/topic/staff.queue', () => refetchLists());
    const off2 = subscribe('/user/queue/inbox', () => refetchLists());
    return () => {
      off1();
      off2();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subscribe]);

  const inboxItems = inbox.data?.content ?? [];
  const queueItems = queue.data?.content ?? [];
  const selected = inboxItems.find((c) => c.id === selectedId) ?? null;
  const totalUnread = inboxItems.reduce((sum, c) => sum + c.staffUnread, 0);

  async function claim(id: string) {
    try {
      await chatApi.claim(id);
      refetchLists();
      setSelectedId(id);
      toast.success('Đã nhận hội thoại');
    } catch (e) {
      toast.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Không nhận được');
    }
  }

  async function transfer() {
    if (!selected) return;
    const to = window.prompt('Nhập ID nhân viên muốn chuyển tới:');
    if (!to) return;
    try {
      await chatApi.transfer(selected.id, to.trim());
      refetchLists();
      setSelectedId(null);
      toast.success('Đã chuyển hội thoại');
    } catch (e) {
      toast.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Không chuyển được');
    }
  }

  async function act(fn: (id: string) => Promise<unknown>, okMsg: string, clearSelection: boolean) {
    if (!selected) return;
    try {
      await fn(selected.id);
      refetchLists();
      if (clearSelection) setSelectedId(null);
      toast.success(okMsg);
    } catch (e) {
      toast.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Thao tác thất bại');
    }
  }

  return (
    <div className="grid h-[72vh] grid-cols-1 overflow-hidden rounded-xl border border-white/10 bg-white md:grid-cols-[320px_1fr]">
      {/* Left: my inbox + unassigned queue */}
      <div className="flex flex-col overflow-hidden border-r border-black/10">
        <div className="flex items-center justify-between bg-brand-header px-3 py-2 text-white">
          <span className="text-sm font-semibold">Của tôi</span>
          {totalUnread > 0 && (
            <span className="rounded-full bg-red-500 px-2 text-xs font-bold">{totalUnread}</span>
          )}
        </div>
        <div className="flex-1 overflow-y-auto">
          {inboxItems.length === 0 && <p className="p-3 text-xs text-black/40">Chưa có hội thoại nào.</p>}
          {inboxItems.map((c) => (
            <ConvRow key={c.id} conv={c} active={c.id === selectedId} onClick={() => setSelectedId(c.id)} />
          ))}

          <div className="bg-brand-panel/10 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-brand-panel">
            Hàng đợi chưa gán
          </div>
          {queueItems.length === 0 && <p className="p-3 text-xs text-black/40">Không có hội thoại chờ.</p>}
          {queueItems.map((c) => (
            <ConvRow
              key={c.id}
              conv={c}
              action={
                <Button size="sm" onClick={() => claim(c.id)}>
                  Nhận
                </Button>
              }
            />
          ))}
        </div>
      </div>

      {/* Right: selected thread */}
      <div className="min-h-0">
        {selected ? (
          <ChatThread
            conversationId={selected.id}
            meId={me.id}
            myRole="STAFF"
            senderName={me.fullName}
            title={`${selected.customerName} · ${selected.status}`}
            headerRight={
              <>
                <Button size="sm" variant="outline" onClick={transfer}>
                  Chuyển
                </Button>
                <Button size="sm" variant="outline" onClick={() => act(chatApi.release, 'Đã nhả về hàng đợi', true)}>
                  Nhả
                </Button>
                <Button size="sm" variant="danger" onClick={() => act(chatApi.close, 'Đã đóng hội thoại', true)}>
                  Đóng
                </Button>
              </>
            }
          />
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-black/40">
            Chọn một hội thoại để trả lời
          </div>
        )}
      </div>
    </div>
  );
}
