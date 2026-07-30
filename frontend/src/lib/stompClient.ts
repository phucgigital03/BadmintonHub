import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { useAuthStore } from '../store/authStore';
import { refreshAccessToken } from '../api/axiosClient';

/**
 * Single STOMP client for the whole app (customer widget + staff console). Connects to the gateway `/ws`
 * (auth is in the CONNECT frame, not the handshake). Keeps behaviour aligned with the backend (PHASE 3):
 * - `beforeConnect` puts a fresh `Authorization: Bearer` on the CONNECT frame; if the socket was closed with
 *   code 1008 (server-side token expiry, §G.2) it silent-refreshes first (reuses axiosClient's refresh).
 * - stompjs auto-reconnects (`reconnectDelay`); it does NOT auto-resubscribe, so `onConnect` re-subscribes the
 *   registry and fires connect listeners (components use that to history-sync).
 * - ref-counted activation: the socket is up only while at least one chat surface is open.
 */
// Precedence: explicit VITE_CHAT_WS_URL -> derived from VITE_API_URL (`npm run dev`)
// -> same-origin (nginx proxies /ws to the gateway). The same-origin branch is what
// makes one frontend image work behind any host, and it upgrades itself to `wss`
// automatically once TLS is terminated at the load balancer.
const apiUrl = import.meta.env.VITE_API_URL as string | undefined;
const WS_URL =
  (import.meta.env.VITE_CHAT_WS_URL as string | undefined) ??
  (apiUrl
    ? apiUrl.replace(/^http/, 'ws') + '/ws'
    : `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`);

type Handler = (msg: IMessage) => void;
interface Registered {
  dest: string;
  handler: Handler;
  sub?: StompSubscription;
}

let client: Client | null = null;
let refCount = 0;
let needsRefresh = false;
const registry = new Map<symbol, Registered>();
const connectListeners = new Set<() => void>();

function build(): Client {
  const c = new Client({
    brokerURL: WS_URL,
    reconnectDelay: 3000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    beforeConnect: async () => {
      if (needsRefresh) {
        await refreshAccessToken();
        needsRefresh = false;
      }
      const token = useAuthStore.getState().accessToken;
      c.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    },
    onConnect: () => {
      registry.forEach((r) => {
        r.sub = c.subscribe(r.dest, r.handler);
      });
      connectListeners.forEach((cb) => cb());
    },
    onWebSocketClose: (evt) => {
      if (evt?.code === 1008) needsRefresh = true; // token expired server-side (§G.2)
    },
  });
  return c;
}

export function retainChatSocket(): void {
  refCount += 1;
  if (!client) client = build();
  if (!client.active) client.activate();
}

export function releaseChatSocket(): void {
  refCount = Math.max(0, refCount - 1);
  if (refCount === 0 && client) {
    const c = client;
    client = null;
    registry.clear();
    void c.deactivate();
  }
}

/** Subscribe to a broker destination; returns an unsubscribe. Survives reconnects via the registry. */
export function subscribeChat(dest: string, handler: Handler): () => void {
  const key = Symbol(dest);
  const reg: Registered = { dest, handler };
  registry.set(key, reg);
  if (client?.connected) reg.sub = client.subscribe(dest, handler);
  return () => {
    reg.sub?.unsubscribe();
    registry.delete(key);
  };
}

/** Fire-and-forget publish to an /app destination (typing / delivered). No-op if not connected. */
export function publishChat(dest: string, body: unknown): void {
  if (client?.connected) client.publish({ destination: dest, body: JSON.stringify(body) });
}

/** Register a callback fired on every (re)connect — used to history-sync after a drop. */
export function onChatConnected(cb: () => void): () => void {
  connectListeners.add(cb);
  return () => {
    connectListeners.delete(cb);
  };
}
