import { useCallback, useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import type { ReactNode } from 'react';
import { chatApi } from '../../api/chat';
import { useChatSocket } from '../../hooks/useChatSocket';
import type { ChatMessage, ChatSenderRole, DeliveryReceipt, ReadReceipt, TypingSignal } from '../../types';

const PAGE = 30;
const ALLOWED = ['image/png', 'image/jpeg', 'image/webp'];

interface Props {
  conversationId: string;
  meId: string;
  myRole: ChatSenderRole;
  senderName: string;
  /** Whether I'm allowed to type here (staff can't write into an unclaimed thread). */
  canSend?: boolean;
  /** Right-side header slot (staff action buttons). */
  headerRight?: ReactNode;
  title?: string;
}

function hhmm(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/** ✓ sent · ✓✓ delivered · ✓✓ (blue) read — shown only on my own messages. */
function Ticks({ m }: { m: ChatMessage }) {
  if (m.readAt) return <span className="text-sky-300">✓✓</span>;
  if (m.deliveredAt) return <span className="text-white/60">✓✓</span>;
  return <span className="text-white/60">✓</span>;
}

/**
 * Shared chat thread — used by the customer popup and the staff console. Loads history (keyset), appends
 * live messages over STOMP, ACKs delivered, marks read, shows typing, and sends text + images. Message
 * content is rendered as React text (auto-escaped) — never dangerouslySetInnerHTML (XSS-safe, §G.3).
 */
export function ChatThread({ conversationId, meId, myRole, senderName, canSend = true, headerRight, title }: Props) {
  const { subscribe, publish, onConnected } = useChatSocket(true);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [peerTyping, setPeerTyping] = useState(false);
  const [uploading, setUploading] = useState(false);

  const listRef = useRef<HTMLDivElement>(null);
  const typingSentAt = useRef(0);
  const typingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const readTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const scrollToBottom = useCallback(() => {
    requestAnimationFrame(() => {
      const el = listRef.current;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }, []);

  const loadHistory = useCallback(async () => {
    const page = await chatApi.messages(conversationId, undefined, PAGE);
    setMessages(page.slice().reverse()); // API returns newest→oldest; display oldest→newest
    setHasMore(page.length === PAGE);
    scrollToBottom();
  }, [conversationId, scrollToBottom]);

  const markReadSoon = useCallback(() => {
    if (readTimer.current) clearTimeout(readTimer.current);
    readTimer.current = setTimeout(() => {
      chatApi.markRead(conversationId).catch(() => {});
    }, 300);
  }, [conversationId]);

  // Initial load + mark read.
  useEffect(() => {
    setMessages([]);
    setPeerTyping(false);
    loadHistory().then(markReadSoon).catch(() => toast.error('Không tải được lịch sử chat'));
  }, [conversationId, loadHistory, markReadSoon]);

  const appendMessage = useCallback((msg: ChatMessage) => {
    setMessages((prev) => {
      if (prev.some((m) => m.id === msg.id || (m.clientMsgId && m.clientMsgId === msg.clientMsgId))) {
        // replace an optimistic/duplicate entry
        return prev.map((m) => (m.clientMsgId === msg.clientMsgId ? msg : m));
      }
      return [...prev, msg];
    });
    scrollToBottom();
  }, [scrollToBottom]);

  // Live subscriptions (per-user queues; filter by this conversation).
  useEffect(() => {
    const unsubs = [
      subscribe('/user/queue/messages', (frame) => {
        const msg = JSON.parse(frame.body) as ChatMessage;
        if (msg.conversationId !== conversationId) return;
        appendMessage(msg);
        publish(`/app/conv/${conversationId}/delivered`, { messageId: msg.id });
        markReadSoon();
      }),
      subscribe('/user/queue/delivery', (frame) => {
        const r = JSON.parse(frame.body) as DeliveryReceipt;
        if (r.conversationId !== conversationId) return;
        setMessages((prev) =>
          prev.map((m) => (m.id === r.messageId && !m.deliveredAt ? { ...m, deliveredAt: new Date().toISOString() } : m)));
      }),
      subscribe('/user/queue/read', (frame) => {
        const r = JSON.parse(frame.body) as ReadReceipt;
        if (r.conversationId !== conversationId) return;
        const now = new Date().toISOString();
        setMessages((prev) => prev.map((m) => (m.senderId === meId && !m.readAt ? { ...m, readAt: now } : m)));
      }),
      subscribe('/user/queue/typing', (frame) => {
        const r = JSON.parse(frame.body) as TypingSignal;
        if (r.conversationId !== conversationId || r.fromUserId === meId) return;
        setPeerTyping(true);
        if (typingTimer.current) clearTimeout(typingTimer.current);
        typingTimer.current = setTimeout(() => setPeerTyping(false), 3000);
      }),
    ];
    const offConn = onConnected(() => {
      loadHistory().catch(() => {}); // history-sync after reconnect
    });
    return () => {
      unsubs.forEach((u) => u());
      offConn();
    };
  }, [conversationId, meId, subscribe, publish, onConnected, appendMessage, markReadSoon, loadHistory]);

  async function onScroll() {
    const el = listRef.current;
    if (!el || el.scrollTop > 40 || loadingOlder || !hasMore) return;
    setLoadingOlder(true);
    const oldest = messages[0]?.id;
    try {
      const older = await chatApi.messages(conversationId, oldest, PAGE);
      setHasMore(older.length === PAGE);
      const prevHeight = el.scrollHeight;
      setMessages((prev) => [...older.slice().reverse(), ...prev]);
      requestAnimationFrame(() => {
        if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight - prevHeight;
      });
    } finally {
      setLoadingOlder(false);
    }
  }

  const [draft, setDraft] = useState('');

  function onDraftChange(v: string) {
    setDraft(v);
    const now = Date.now();
    if (now - typingSentAt.current > 1500) {
      typingSentAt.current = now;
      publish(`/app/conv/${conversationId}/typing`, {});
    }
  }

  async function sendText() {
    const content = draft.trim();
    if (!content) return;
    const clientMsgId = crypto.randomUUID();
    const optimistic: ChatMessage = {
      id: `tmp-${clientMsgId}`, conversationId, senderId: meId, senderRole: myRole, senderName,
      type: 'TEXT', content, imageUrl: null, clientMsgId, deliveredAt: null, readAt: null,
      createdAt: new Date().toISOString(),
    };
    appendMessage(optimistic);
    setDraft('');
    try {
      const saved = await chatApi.send(conversationId, { clientMsgId, content, senderName });
      appendMessage(saved); // swap optimistic → server message (real id + ticks)
    } catch {
      toast.error('Gửi tin thất bại');
    }
  }

  async function onPickImage(file: File) {
    if (!ALLOWED.includes(file.type)) {
      toast.error('Chỉ chấp nhận ảnh PNG, JPEG hoặc WEBP');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      toast.error('Ảnh vượt quá 5MB');
      return;
    }
    setUploading(true);
    try {
      const saved = await chatApi.sendImage(conversationId, file, { clientMsgId: crypto.randomUUID(), senderName });
      appendMessage(saved);
    } catch {
      toast.error('Gửi ảnh thất bại');
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  }

  return (
    <div className="flex h-full flex-col bg-white">
      {(title || headerRight) && (
        <div className="flex items-center justify-between border-b border-black/10 px-4 py-2">
          <span className="font-semibold text-brand-header">{title}</span>
          <div className="flex gap-2">{headerRight}</div>
        </div>
      )}

      <div ref={listRef} onScroll={onScroll} className="flex-1 space-y-2 overflow-y-auto bg-[#f4f6f5] p-3">
        {loadingOlder && <p className="text-center text-xs text-black/40">Đang tải…</p>}
        {messages.map((m) => {
          const mine = m.senderId === meId;
          return (
            <div key={m.id} className={mine ? 'flex justify-end' : 'flex justify-start'}>
              <div
                className={[
                  'max-w-[78%] rounded-2xl px-3 py-2 text-sm shadow-sm',
                  mine ? 'bg-brand-gold text-[#3a2c08]' : 'bg-white text-gray-800',
                ].join(' ')}
              >
                {!mine && <div className="mb-0.5 text-[11px] font-semibold text-brand-panel">{m.senderName}</div>}
                {m.type === 'IMAGE' && m.imageUrl ? (
                  <img
                    src={m.imageUrl}
                    alt="ảnh"
                    className="max-h-56 rounded-lg object-cover"
                    onError={(e) => ((e.target as HTMLImageElement).replaceWith(Object.assign(document.createElement('span'), { textContent: '[Ảnh]' })))}
                  />
                ) : null}
                {m.content ? <div className="whitespace-pre-wrap break-words">{m.content}</div> : null}
                <div className="mt-0.5 flex items-center justify-end gap-1 text-[10px] opacity-70">
                  <span>{hhmm(m.createdAt)}</span>
                  {mine && <Ticks m={m} />}
                </div>
              </div>
            </div>
          );
        })}
        {peerTyping && <p className="pl-2 text-xs italic text-black/40">Đang nhập…</p>}
      </div>

      {canSend ? (
        <div className="flex items-center gap-2 border-t border-black/10 bg-white p-2">
          <input
            ref={fileRef}
            type="file"
            accept="image/png,image/jpeg,image/webp"
            className="hidden"
            onChange={(e) => e.target.files?.[0] && onPickImage(e.target.files[0])}
          />
          <button
            type="button"
            title="Gửi ảnh"
            disabled={uploading}
            onClick={() => fileRef.current?.click()}
            className="rounded-full px-2 py-1 text-lg text-brand-panel hover:bg-black/5 disabled:opacity-50"
          >
            {uploading ? '…' : '📎'}
          </button>
          <input
            value={draft}
            onChange={(e) => onDraftChange(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && (e.preventDefault(), sendText())}
            placeholder="Nhập tin nhắn…"
            className="flex-1 rounded-full border border-black/10 px-3 py-2 text-sm outline-none focus:border-brand-gold"
          />
          <button
            type="button"
            onClick={sendText}
            className="rounded-full bg-brand-gold px-4 py-2 text-sm font-semibold text-[#3a2c08] hover:bg-brand-gold-dark"
          >
            Gửi
          </button>
        </div>
      ) : (
        <div className="border-t border-black/10 bg-white p-3 text-center text-xs text-black/40">
          Nhận hội thoại để trả lời
        </div>
      )}
    </div>
  );
}
