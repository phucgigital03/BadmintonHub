import { useEffect } from 'react';
import { useAuthStore } from '../store/authStore';
import {
  retainChatSocket,
  releaseChatSocket,
  subscribeChat,
  publishChat,
  onChatConnected,
} from '../lib/stompClient';

/**
 * Keeps the shared STOMP socket alive while a chat surface is mounted+enabled (ref-counted), and exposes
 * subscribe / publish / onConnected. Never touch the client directly from components — always via this hook.
 */
export function useChatSocket(enabled: boolean) {
  const token = useAuthStore((s) => s.accessToken);

  useEffect(() => {
    if (!enabled || !token) return;
    retainChatSocket();
    return () => releaseChatSocket();
  }, [enabled, token]);

  return { subscribe: subscribeChat, publish: publishChat, onConnected: onChatConnected };
}
