package com.findora.app.core.domain.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The thread on screen right now (the conversation screen is resumed). A reply to it is marked read at once and
 * never notified; a reply to any other thread posts a notification.
 */
@Singleton
class ActiveConversation
    @Inject
    constructor() {
        private val current = MutableStateFlow<String?>(null)
        val conversationId: StateFlow<String?> = current.asStateFlow()

        fun enter(conversationId: String) {
            current.value = conversationId
        }

        /** Clears only when [conversationId] is still the active one (a newer screen may have taken over). */
        fun leave(conversationId: String) {
            current.update { if (it == conversationId) null else it }
        }

        fun isActive(conversationId: String): Boolean = current.value == conversationId
    }

/** Starts and runs the demo agent's side of a thread after the user sends a message. */
interface AgentResponder {
    /** Conversation ids where the agent is "typing" right now. */
    val typing: StateFlow<Set<String>>

    /** Delivers, reads and answers [messageId] in the background; returns at once. */
    fun onUserMessage(
        userId: String,
        conversationId: String,
        messageId: String,
        text: String,
    )
}
