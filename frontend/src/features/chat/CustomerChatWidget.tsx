import { useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { useAuthStore } from '../../store/authStore';
import { useSupportChatStore } from '../../store/supportChatStore';
import { useChatSocket } from '../../hooks/useChatSocket';
import { chatApi } from '../../api/chat';
import { ChatThread } from './ChatThread';

/**
 * Customer support chat as a floating popup (messenger-style). Mounted globally in App; renders only for a
 * logged-in USER/COACH (hidden for STAFF/ADMIN + guests). The thread/socket is created only after the first
 * open (so we don't pollute the staff queue with empty threads for every login); once created, the socket
 * stays live while mounted so the unread badge updates even when the panel is closed.
 */
export function CustomerChatWidget() {
  const user = useAuthStore((s) => s.user);
  const isCustomer =
    !!user &&
    (user.roles.includes('ROLE_USER') || user.roles.includes('ROLE_COACH')) &&
    !user.roles.includes('ROLE_STAFF') &&
    !user.roles.includes('ROLE_ADMIN');

  const [open, setOpen] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [unread, setUnread] = useState(0);
  const [opening, setOpening] = useState(false);
  const openRef = useRef(open);
  openRef.current = open;
  const convRef = useRef(conversationId);
  convRef.current = conversationId;

  // Escalation signal from the (separate) AI booking widget — open this staff chat + optional first message.
  const openTick = useSupportChatStore((s) => s.openTick);
  const prefill = useSupportChatStore((s) => s.prefill);
  const consumed = useSupportChatStore((s) => s.consumed);

  // Socket lives once a conversation exists (i.e. after first open).
  const { subscribe } = useChatSocket(isCustomer && !!conversationId);

  useEffect(() => {
    if (!conversationId) return;
    const off = subscribe('/user/queue/messages', () => {
      if (!openRef.current) setUnread((u) => u + 1); // count only while closed; open thread marks read
    });
    return off;
  }, [conversationId, subscribe]);

  // React to an escalation request (find-or-create the thread, post the AI summary as the first message).
  useEffect(() => {
    if (openTick === 0 || !isCustomer) return;
    let cancelled = false;
    (async () => {
      setUnread(0);
      setOpening(true);
      try {
        const id = await ensureConversation();
        if (prefill && id) {
          await chatApi.send(id, {
            clientMsgId: crypto.randomUUID(),
            content: prefill,
            senderName: user!.fullName,
          });
        }
      } catch {
        toast.error('Không mở được hỗ trợ, thử lại sau');
      } finally {
        if (!cancelled) {
          setOpening(false);
          setOpen(true);
          consumed();
        }
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [openTick]);

  if (!isCustomer) return null;

  /** Find-or-create my open thread (idempotent) and remember its id. Returns the conversation id. */
  async function ensureConversation(): Promise<string> {
    if (convRef.current) return convRef.current;
    const conv = await chatApi.open(user!.fullName);
    setConversationId(conv.id);
    convRef.current = conv.id;
    return conv.id;
  }

  async function toggle() {
    if (open) {
      setOpen(false);
      return;
    }
    setUnread(0);
    if (!conversationId) {
      setOpening(true);
      try {
        await ensureConversation();
      } catch {
        toast.error('Không mở được hỗ trợ, thử lại sau');
        setOpening(false);
        return;
      }
      setOpening(false);
    }
    setOpen(true);
  }

  return (
    <>
      {open && conversationId && (
        <div className="fixed bottom-24 right-6 z-[1500] flex h-[520px] w-[360px] max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-2xl shadow-2xl">
          <div className="flex items-center justify-between bg-brand-header px-4 py-3 text-white">
            <div>
              <div className="text-sm font-semibold">Hỗ trợ khách hàng</div>
              <div className="text-[11px] text-white/60">Chúng tôi thường phản hồi trong ít phút</div>
            </div>
            <button onClick={() => setOpen(false)} className="text-white/70 hover:text-white" aria-label="Đóng">
              ✕
            </button>
          </div>
          <div className="min-h-0 flex-1">
            <ChatThread
              conversationId={conversationId}
              meId={user!.id}
              myRole="CUSTOMER"
              senderName={user!.fullName}
            />
          </div>
        </div>
      )}

      <button
        onClick={toggle}
        disabled={opening}
        className="fixed bottom-6 right-6 z-[1500] flex h-14 w-14 items-center justify-center rounded-full bg-brand-gold text-2xl shadow-xl transition hover:bg-brand-gold-dark disabled:opacity-60"
        aria-label="Hỗ trợ"
      >
        {open ? '✕' : '💬'}
        {!open && unread > 0 && (
          <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-bold text-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>
    </>
  );
}
