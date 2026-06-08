import { useEffect } from 'react';
import { io } from 'socket.io-client';
import { useQueryClient } from '@tanstack/react-query';
import type { Match } from '../types';

/**
 * Subscribes to the matchmaking-service real-time slot counter.
 * Socket is used ONLY through this hook (never directly in components).
 * If the WS backend is not running, the socket simply never emits — no crash.
 */
export function useMatchSocket(matchId?: string) {
  const qc = useQueryClient();
  useEffect(() => {
    if (!matchId) return;
    const socket = io(import.meta.env.VITE_WS_URL as string, {
      reconnection: false,
      autoConnect: true,
    });
    socket.emit('join-match-room', matchId);
    socket.on('slot-updated', (data: Partial<Match>) => {
      qc.setQueryData<Match>(['match', matchId], (old) => (old ? { ...old, ...data } : old));
    });
    return () => {
      socket.disconnect();
    };
  }, [matchId, qc]);
}
