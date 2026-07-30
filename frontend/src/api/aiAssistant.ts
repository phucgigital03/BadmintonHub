import axiosClient, { refreshAccessToken } from './axiosClient';
import { useAuthStore } from '../store/authStore';
import type { BookingResponse } from './bookings';
import type { PaymentResponse } from './payments';

/**
 * ai-service assistant (court-booking concierge) — REST + SSE, through the gateway :3000.
 * Base path `/api/ai/assistant`. Every call carries `Authorization: Bearer` (act-as-user;
 * ai-service forwards it to booking/payment). This is a DIFFERENT channel from chat.ts (STAFF/STOMP).
 *
 * Money boundary: this client only talks to the assistant endpoints. It never confirms money —
 * `confirm()` asks the agent to create the hold + open the payment; the QR/proof/STAFF-confirm flow
 * stays on the existing hardened path (PaymentScreen).
 */

// Same-origin by default (nginx proxies /api to the gateway); VITE_API_URL is only for
// `npm run dev`. See axiosClient.ts.
const BASE_URL = (import.meta.env.VITE_API_URL as string | undefined) ?? '';

// ── Agent DTO shapes ─────────────────────────────────────────────────────────
// AgentTurn is camelCase; the card contents (CourtOption / ProposedBooking) are snake_case.

export interface CourtOption {
  club_id: string;
  court_id: string;
  court_number: string;
  sport: string | null;
  slot_ids: string[];
  start_time: string; // "HH:mm:ss"
  end_time: string; // "HH:mm:ss"
  total_price: number;
  within_budget: boolean;
  rationale?: string;
}

export interface ProposedBooking {
  club_id: string;
  date: string; // "yyyy-MM-dd"
  items: { court_id: string; slot_id: string }[];
  total_price: number;
  customer_name?: string | null;
  customer_phone?: string | null;
  hold_minutes?: number;
  summary: string;
}

export type AiCard =
  | { type: 'proposal'; proposal: ProposedBooking; option?: CourtOption | null }
  | { type: 'alternative'; option: CourtOption }
  | { type: 'payment'; booking?: unknown; payment?: unknown };

export interface AgentTurn {
  role: 'assistant' | string;
  content: string;
  cards: AiCard[];
  suggestedActions: string[];
}

/** Payload of the SSE `turn` event and the `/confirm` response. */
export interface TurnPayload {
  turn: AgentTurn | null;
  stage: string;
  awaitingConfirm: boolean;
  booking?: BookingResponse; // present on a successful confirm (stage === 'payment')
  payment?: PaymentResponse; // present on a successful confirm
}

/** GET /{id} — 200 body (session resume snapshot). */
export interface SessionSnapshot {
  sessionId: string;
  transcript: { role: 'user' | 'assistant' | string; content: string }[];
  intent: Record<string, unknown> | null;
  proposal: ProposedBooking | null;
  stage: string;
  awaitingConfirm: boolean;
}

export interface ConfirmBody {
  confirmed: boolean;
  edits?: string;
  customerName?: string;
  customerPhone?: string;
}

// ── JSON endpoints (via axiosClient — JWT + silent refresh for free) ──────────

export const aiAssistantApi = {
  createSession: () =>
    axiosClient.post<{ sessionId: string }>('/api/ai/assistant/sessions').then((r) => r.data),

  /** 404 SESSION_NOT_FOUND / 410 SESSION_EXPIRED surface as axios errors — the caller falls back to a fresh session. */
  getSession: (id: string) =>
    axiosClient.get<SessionSnapshot>(`/api/ai/assistant/${id}`).then((r) => r.data),

  confirm: (id: string, body: ConfirmBody) =>
    axiosClient.post<TurnPayload>(`/api/ai/assistant/${id}/confirm`, body).then((r) => r.data),

  escalate: (id: string) =>
    axiosClient.post<{ summary: string }>(`/api/ai/assistant/${id}/escalate`).then((r) => r.data),
};

// ── SSE endpoint (fetch + ReadableStream — axios can't stream) ────────────────

export interface StreamHandlers {
  onNode?: (node: string) => void;
  onTurn?: (payload: TurnPayload) => void;
  onError?: (err: { code: string; message: string }) => void;
  onDone?: () => void;
}

/**
 * POST /{id}/messages → text/event-stream. Emits `node` (progress) events then one `turn` event
 * (the whole reply — the backend does NOT stream tokens), then `done`. First SSE consumer in the app.
 * The token is pulled from authStore (like the STOMP client); on a 401 it silently refreshes once.
 */
export async function streamMessage(
  sessionId: string,
  text: string,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const url = `${BASE_URL}/api/ai/assistant/${sessionId}/messages`;

  const doFetch = (token: string | null) =>
    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ text }),
      credentials: 'include',
      signal,
    });

  let res: Response;
  try {
    res = await doFetch(useAuthStore.getState().accessToken);
    if (res.status === 401) {
      const token = await refreshAccessToken();
      if (token) res = await doFetch(token);
    }
  } catch (err) {
    if ((err as Error)?.name === 'AbortError') return;
    handlers.onError?.({ code: 'NETWORK', message: 'Không kết nối được trợ lý. Thử lại sau.' });
    handlers.onDone?.();
    return;
  }

  if (!res.ok || !res.body) {
    let code = 'AGENT_ERROR';
    let message = `Lỗi máy chủ (${res.status})`;
    try {
      const body = (await res.json()) as { code?: string; message?: string };
      code = body.code ?? code;
      message = body.message ?? message;
    } catch {
      /* non-JSON error body — keep defaults */
    }
    handlers.onError?.({ code, message });
    handlers.onDone?.();
    return;
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let finished = false;

  const fireDone = () => {
    if (!finished) {
      finished = true;
      handlers.onDone?.();
    }
  };

  const dispatch = (rawEvent: string) => {
    let event = 'message';
    const dataLines: string[] = [];
    for (const line of rawEvent.split('\n')) {
      if (!line || line.startsWith(':')) continue; // keep-alive comment / blank
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''));
    }
    if (dataLines.length === 0) return;
    const data = dataLines.join('\n');
    try {
      if (event === 'node') handlers.onNode?.((JSON.parse(data) as { node: string }).node);
      else if (event === 'turn') handlers.onTurn?.(JSON.parse(data) as TurnPayload);
      else if (event === 'error') handlers.onError?.(JSON.parse(data) as { code: string; message: string });
      else if (event === 'done') fireDone();
    } catch {
      if (event === 'error') handlers.onError?.({ code: 'AGENT_ERROR', message: data });
    }
  };

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n');
      let idx: number;
      while ((idx = buffer.indexOf('\n\n')) !== -1) {
        const frame = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 2);
        if (frame.trim()) dispatch(frame);
      }
    }
    if (buffer.trim()) dispatch(buffer);
  } catch (err) {
    if ((err as Error)?.name !== 'AbortError') {
      handlers.onError?.({ code: 'STREAM_ERROR', message: 'Kết nối bị gián đoạn.' });
    }
  } finally {
    fireDone();
  }
}
