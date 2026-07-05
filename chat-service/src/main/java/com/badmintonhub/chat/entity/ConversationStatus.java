package com.badmintonhub.chat.entity;

/**
 * Lifecycle of a support thread (per-staff private inbox model, UC_Chatting §B).
 * <pre>
 *   OPEN (unassigned queue) ──claim──► ASSIGNED (one staff's inbox) ──close──► CLOSED
 *        ▲                                │
 *        └────────── release ────────────┘
 *   reopen: CLOSED ──customer sends again (keeps assignedStaffId)──► ASSIGNED
 * </pre>
 */
public enum ConversationStatus {
    OPEN,
    ASSIGNED,
    CLOSED
}
