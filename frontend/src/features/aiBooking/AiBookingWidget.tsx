import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import type { AxiosError } from 'axios';
import { useAuthStore } from '../../store/authStore';
import { useSupportChatStore } from '../../store/supportChatStore';
import {
  aiAssistantApi,
  streamMessage,
  type AiCard,
  type SessionSnapshot,
} from '../../api/aiAssistant';
import { bookingItemLabel } from '../../api/bookings';
import type { PaymentSummary } from '../../components/payment/PaymentScreen';
import { ProposalCard } from './ProposalCard';

interface AiChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  cards?: AiCard[];
}

const WELCOME =
  'Chào bạn 👋 Mình là trợ lý đặt sân. Bạn muốn đặt môn gì, ngày nào, khung giờ và ngân sách khoảng bao nhiêu?';
const WELCOME_SUGGESTIONS = ['Đặt sân pickleball tối mai 18-20h dưới 200k', 'Chính sách hủy sân?'];

const NODE_LABEL: Record<string, string> = {
  route: 'Đang phân loại yêu cầu…',
  perceive: 'Đang hiểu yêu cầu…',
  memory_load: 'Đang xem lịch sử của bạn…',
  ask_clarify: 'Đang soạn câu hỏi…',
  knowledge: 'Đang tra cứu thông tin…',
  agent: 'Đang tìm sân…',
  rank_propose: 'Đang xếp hạng phương án…',
  human_review: 'Đang chuẩn bị đề xuất…',
  guardrail: 'Đang kiểm tra điều kiện…',
  hold: 'Đang giữ chỗ…',
  payment: 'Đang mở thanh toán…',
};
const nodeLabel = (node: string) => NODE_LABEL[node] ?? 'Đang xử lý…';

/**
 * AI booking concierge — a floating popup SEPARATE from the STAFF support widget (CustomerChatWidget).
 * Talks to ai-service over REST + SSE, renders proposal cards, hands a confirmed booking to the existing
 * /payment page, and can escalate to the STAFF widget. Renders only for a logged-in USER/COACH.
 */
export function AiBookingWidget() {
  const user = useAuthStore((s) => s.user);
  const isCustomer =
    !!user &&
    (user.roles.includes('ROLE_USER') || user.roles.includes('ROLE_COACH')) &&
    !user.roles.includes('ROLE_STAFF') &&
    !user.roles.includes('ROLE_ADMIN');

  const navigate = useNavigate();
  const requestOpenSupport = useSupportChatStore((s) => s.requestOpen);

  const [open, setOpen] = useState(false);
  const [booting, setBooting] = useState(false);
  const [bootError, setBootError] = useState(false);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<AiChatMessage[]>([]);
  const [thinking, setThinking] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [awaitingConfirm, setAwaitingConfirm] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [escalating, setEscalating] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [draft, setDraft] = useState('');

  const abortRef = useRef<AbortController | null>(null);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages, thinking]);

  // Abort any in-flight stream when the widget unmounts.
  useEffect(() => () => abortRef.current?.abort(), []);

  const lastCardMsgId = useMemo(() => {
    for (let i = messages.length - 1; i >= 0; i--) if (messages[i].cards?.length) return messages[i].id;
    return null;
  }, [messages]);

  if (!isCustomer) return null;

  const storageKey = `bh-ai-session:${user!.id}`;
  const persist = (id: string) => {
    try {
      localStorage.setItem(storageKey, id);
    } catch {
      /* private mode / quota — session still works in-memory */
    }
  };

  function hydrate(snap: SessionSnapshot) {
    const msgs: AiChatMessage[] = snap.transcript.map((t) => ({
      id: crypto.randomUUID(),
      role: t.role === 'user' ? 'user' : 'assistant',
      content: t.content,
    }));
    if (snap.awaitingConfirm && snap.proposal) {
      msgs.push({
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '',
        cards: [{ type: 'proposal', proposal: snap.proposal, option: null }],
      });
    }
    setMessages(msgs.length ? msgs : [{ id: crypto.randomUUID(), role: 'assistant', content: WELCOME }]);
    setAwaitingConfirm(snap.awaitingConfirm);
    setSuggestions([]);
  }

  function startFresh(id: string) {
    setSessionId(id);
    persist(id);
    setMessages([{ id: crypto.randomUUID(), role: 'assistant', content: WELCOME }]);
    setSuggestions(WELCOME_SUGGESTIONS);
    setAwaitingConfirm(false);
  }

  // Session lifecycle A (resume) + B (fresh) — resolved lazily on first open.
  async function boot() {
    setBooting(true);
    setBootError(false);
    let stored: string | null = null;
    try {
      stored = localStorage.getItem(storageKey);
    } catch {
      /* localStorage unavailable (private mode) — treat as no stored session */
    }
    if (stored) {
      try {
        const snap = await aiAssistantApi.getSession(stored); // A
        setSessionId(stored);
        hydrate(snap);
        setBooting(false);
        return;
      } catch {
        // 404/410 (or a stale id) → drop it and open a fresh session (B). L2 personalization is by userId, still applies.
        try {
          localStorage.removeItem(storageKey);
        } catch {
          /* ignore */
        }
      }
    }
    try {
      const { sessionId: id } = await aiAssistantApi.createSession(); // B
      startFresh(id);
    } catch {
      setBootError(true);
    } finally {
      setBooting(false);
    }
  }

  async function toggle() {
    if (open) {
      setOpen(false);
      return;
    }
    setOpen(true);
    if (!sessionId && !booting) await boot();
  }

  function send(text: string) {
    const trimmed = text.trim();
    if (!trimmed || !sessionId || sending) return;
    setDraft('');
    setSuggestions([]);
    setMessages((m) => [...m, { id: crypto.randomUUID(), role: 'user', content: trimmed }]);
    setSending(true);
    setThinking('Đang xử lý…');

    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;
    const isCurrent = () => abortRef.current === ac;

    void streamMessage(
      sessionId,
      trimmed,
      {
        onNode: (node) => {
          if (isCurrent()) setThinking(nodeLabel(node));
        },
        onTurn: (payload) => {
          if (!isCurrent()) return;
          setThinking(null);
          setAwaitingConfirm(payload.awaitingConfirm);
          const turn = payload.turn;
          if (turn) {
            setMessages((m) => [
              ...m,
              {
                id: crypto.randomUUID(),
                role: 'assistant',
                content: turn.content,
                cards: turn.cards?.length ? turn.cards : undefined,
              },
            ]);
            setSuggestions(turn.suggestedActions ?? []);
          }
        },
        onError: (e) => {
          if (!isCurrent()) return;
          setThinking(null);
          toast.error(e.message || 'Trợ lý gặp sự cố. Thử lại.');
        },
        onDone: () => {
          if (!isCurrent()) return;
          setThinking(null);
          setSending(false);
        },
      },
      ac.signal,
    );
  }

  async function onConfirm() {
    if (!sessionId || confirming) return;
    setConfirming(true);
    try {
      const payload = await aiAssistantApi.confirm(sessionId, { confirmed: true });
      if (payload.booking && payload.payment && payload.stage === 'payment') {
        const booking = payload.booking;
        const summary: PaymentSummary = {
          customerName: booking.customerName,
          customerPhone: booking.customerPhone,
          detail: booking.items.map(bookingItemLabel).join(', '),
          date: booking.bookingDate,
        };
        setAwaitingConfirm(false);
        setOpen(false);
        // 100% reuse of the existing payment flow — idempotent initiate reuses the payment just created.
        navigate('/payment', { state: { bookingId: booking.id, summary } });
        return;
      }
      // Guardrail still needs something (contact / budget) — show the message, let the user reply by typing.
      setAwaitingConfirm(payload.awaitingConfirm);
      const turn = payload.turn;
      if (turn) {
        setMessages((m) => [
          ...m,
          {
            id: crypto.randomUUID(),
            role: 'assistant',
            content: turn.content,
            cards: turn.cards?.length ? turn.cards : undefined,
          },
        ]);
        setSuggestions(turn.suggestedActions ?? []);
      }
    } catch (err) {
      const ax = err as AxiosError<{ message?: string }>;
      if (ax.response?.status === 409) {
        setAwaitingConfirm(false);
        toast.error('Đề xuất không còn hiệu lực — hãy thử lại.');
      } else {
        toast.error(ax.response?.data?.message ?? 'Không giữ chỗ được. Thử lại.');
      }
    } finally {
      setConfirming(false);
    }
  }

  async function onEscalate() {
    if (!sessionId || escalating) return;
    setEscalating(true);
    try {
      const { summary } = await aiAssistantApi.escalate(sessionId);
      requestOpenSupport(summary);
      setOpen(false);
      toast.success('Đã chuyển bạn sang nhân viên hỗ trợ.');
    } catch {
      requestOpenSupport(null); // still open staff chat so the user isn't stuck
      setOpen(false);
    } finally {
      setEscalating(false);
    }
  }

  return (
    <>
      {open && (
        <div className="fixed bottom-24 right-6 z-[1500] flex h-[540px] w-[370px] max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-2xl bg-emerald-50 shadow-2xl">
          {/* Header */}
          <div className="flex items-center justify-between bg-brand-header px-4 py-3 text-white">
            <div>
              <div className="text-sm font-semibold">🤖 Trợ lý đặt sân</div>
              <div className="text-[11px] text-white/60">Đặt sân bằng trò chuyện</div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={onEscalate}
                disabled={!sessionId || escalating}
                className="rounded-full bg-white/10 px-2.5 py-1 text-[11px] text-white/90 hover:bg-white/20 disabled:opacity-50"
              >
                {escalating ? '…' : '🧑‍💼 Gặp nhân viên'}
              </button>
              <button onClick={() => setOpen(false)} className="text-white/70 hover:text-white" aria-label="Đóng">
                ✕
              </button>
            </div>
          </div>

          {/* Body */}
          <div ref={listRef} className="min-h-0 flex-1 space-y-2 overflow-y-auto px-3 py-3">
            {booting ? (
              <div className="mt-6 text-center text-sm text-gray-500">Đang mở trợ lý…</div>
            ) : bootError ? (
              <div className="mt-6 text-center text-sm text-gray-600">
                Không mở được trợ lý.
                <button onClick={boot} className="ml-1 font-semibold text-emerald-600 hover:underline">
                  Thử lại
                </button>
              </div>
            ) : (
              <>
                {messages.map((m) => (
                  <div key={m.id}>
                    {m.content.trim() && (
                      <div className={m.role === 'user' ? 'flex justify-end' : 'flex justify-start'}>
                        <div
                          className={
                            m.role === 'user'
                              ? 'max-w-[80%] whitespace-pre-wrap rounded-2xl bg-brand-gold px-3 py-2 text-sm text-[#3a2c08]'
                              : 'max-w-[85%] whitespace-pre-wrap rounded-2xl bg-white px-3 py-2 text-sm text-gray-800 shadow-sm'
                          }
                        >
                          {m.content}
                        </div>
                      </div>
                    )}
                    {m.cards && (
                      <div className="mt-1.5 space-y-1.5">
                        {m.cards.map((card, i) =>
                          card.type === 'proposal' || card.type === 'alternative' ? (
                            <ProposalCard
                              key={i}
                              card={card}
                              active={awaitingConfirm && m.id === lastCardMsgId && card.type === 'proposal'}
                              confirming={confirming}
                              onConfirm={onConfirm}
                            />
                          ) : null,
                        )}
                      </div>
                    )}
                  </div>
                ))}
                {thinking && (
                  <div className="flex justify-start">
                    <div className="rounded-2xl bg-white px-3 py-2 text-sm text-gray-400 shadow-sm">
                      {thinking}
                      <span className="ml-1 animate-pulse">•••</span>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>

          {/* Suggestions */}
          {suggestions.length > 0 && !sending && (
            <div className="flex flex-wrap gap-1.5 border-t border-emerald-100 bg-emerald-50 px-3 py-2">
              {suggestions.map((s) => (
                <button
                  key={s}
                  onClick={() => send(s)}
                  className="rounded-full border border-emerald-300 bg-white px-2.5 py-1 text-[12px] text-emerald-700 hover:bg-emerald-100"
                >
                  {s}
                </button>
              ))}
            </div>
          )}

          {/* Input */}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              send(draft);
            }}
            className="flex items-center gap-2 border-t border-emerald-100 bg-white px-3 py-2"
          >
            <input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              disabled={!sessionId || sending || booting}
              placeholder="Nhập yêu cầu đặt sân…"
              className="min-w-0 flex-1 rounded-full border border-gray-200 px-3 py-2 text-sm text-gray-900 placeholder:text-gray-400 outline-none focus:border-brand-gold disabled:bg-gray-100"
            />
            <button
              type="submit"
              disabled={!sessionId || sending || booting || !draft.trim()}
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-gold text-lg text-[#3a2c08] transition hover:bg-brand-gold-dark disabled:opacity-50"
              aria-label="Gửi"
            >
              ➤
            </button>
          </form>
        </div>
      )}

      {/* Bubble — offset left of the STAFF bubble (right-6), distinct icon + colour */}
      <button
        onClick={toggle}
        className="fixed bottom-6 right-24 z-[1500] flex h-14 w-14 items-center justify-center rounded-full bg-brand-panel text-2xl text-white shadow-xl ring-2 ring-brand-gold/60 transition hover:bg-brand-panel2"
        aria-label="Trợ lý đặt sân"
      >
        {open ? '✕' : '🤖'}
      </button>
    </>
  );
}
