import axiosClient from './axiosClient';
import type { ChatConversation, ChatMessage, Page } from '../types';

/** REST for chat-service (through the gateway :3000). Realtime push comes over STOMP (lib/stompClient). */
export const chatApi = {
  /** UC-CHAT-01 — find-or-create my open thread (idempotent). */
  open: (displayName?: string) =>
    axiosClient
      .post<ChatConversation>('/api/chat/conversations', { displayName })
      .then((r) => r.data),

  /** UC-CHAT-03 — my inbox / the unassigned queue / (ADMIN) everything. */
  list: (params?: { queue?: 'unassigned'; scope?: 'all'; page?: number; size?: number }) =>
    axiosClient
      .get<Page<ChatConversation>>('/api/chat/conversations', { params })
      .then((r) => r.data),

  /** Keyset history (newest→oldest); pass the oldest shown message id as `before` to load older. */
  messages: (id: string, before?: string, limit = 30) =>
    axiosClient
      .get<ChatMessage[]>(`/api/chat/conversations/${id}/messages`, { params: { before, limit } })
      .then((r) => r.data),

  send: (id: string, body: { clientMsgId: string; content: string; senderName?: string }) =>
    axiosClient
      .post<ChatMessage>(`/api/chat/conversations/${id}/messages`, { ...body, type: 'TEXT' })
      .then((r) => r.data),

  sendImage: (id: string, file: File, opts: { clientMsgId: string; caption?: string; senderName?: string }) => {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('clientMsgId', opts.clientMsgId);
    if (opts.caption) fd.append('caption', opts.caption);
    if (opts.senderName) fd.append('senderName', opts.senderName);
    return axiosClient
      .post<ChatMessage>(`/api/chat/conversations/${id}/images`, fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data);
  },

  markRead: (id: string) =>
    axiosClient.patch<ChatConversation>(`/api/chat/conversations/${id}/read`).then((r) => r.data),

  claim: (id: string) =>
    axiosClient.post<ChatConversation>(`/api/chat/conversations/${id}/claim`).then((r) => r.data),

  transfer: (id: string, toStaffId: string) =>
    axiosClient
      .post<ChatConversation>(`/api/chat/conversations/${id}/transfer`, { toStaffId })
      .then((r) => r.data),

  release: (id: string) =>
    axiosClient.post<ChatConversation>(`/api/chat/conversations/${id}/release`).then((r) => r.data),

  close: (id: string) =>
    axiosClient.post<ChatConversation>(`/api/chat/conversations/${id}/close`).then((r) => r.data),
};
