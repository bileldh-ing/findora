package com.findora.app.core.data.chat

import android.util.Log
import com.findora.app.core.common.AppClock
import com.findora.app.core.di.ApplicationScope
import com.findora.app.core.domain.chat.ActiveConversation
import com.findora.app.core.domain.chat.AgentReplyEngine
import com.findora.app.core.domain.chat.AgentReplyFormatter
import com.findora.app.core.domain.chat.AgentReplyRequest
import com.findora.app.core.domain.chat.AgentResponder
import com.findora.app.core.domain.chat.Conversation
import com.findora.app.core.domain.model.NotificationToggle
import com.findora.app.core.domain.notification.NewNotification
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.domain.repository.ChatRepository
import com.findora.app.core.domain.repository.NotificationRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.SettingsRepository
import com.findora.app.core.domain.repository.ViewingRepository
import com.findora.app.core.domain.usecase.ObserveDisplayPreferences
import com.findora.app.core.domain.viewing.ViewingSlotGenerator
import com.findora.app.core.notification.ChatNotification
import com.findora.app.core.notification.ChatNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Pauses that make the demo agent feel human: delivery, then reading, then typing (sized by the reply). */
data class AgentTiming(
    val deliverMillis: Long = DELIVER_MILLIS,
    val readMillis: Long = READ_MILLIS,
) {
    @Inject
    constructor() : this(DELIVER_MILLIS, READ_MILLIS)

    private companion object {
        const val DELIVER_MILLIS = 600L
        const val READ_MILLIS = 1_100L
    }
}

/**
 * The on-device demo agent. After the user sends a message it marks it delivered, then read, shows "typing"
 * for a while and stores the [AgentReplyEngine]'s answer. Messages of one thread are answered in order. When the
 * thread is not on screen the reply also lands in the notifications center and, if allowed, as a system
 * notification on the Messages channel; each is skipped when its toggle is off in Settings. Runs in the
 * application scope, so leaving the screen does not lose it.
 */
@Singleton
class DemoAgentResponder
    @Inject
    constructor(
        private val chats: ChatRepository,
        private val properties: PropertyRepository,
        private val viewings: ViewingRepository,
        private val notifications: NotificationRepository,
        private val engine: AgentReplyEngine,
        private val formatter: AgentReplyFormatter,
        private val generator: ViewingSlotGenerator,
        private val notifier: ChatNotifier,
        private val active: ActiveConversation,
        private val clock: AppClock,
        private val zone: ZoneId,
        private val timing: AgentTiming,
        private val settings: SettingsRepository,
        private val displayPreferences: ObserveDisplayPreferences,
        @ApplicationScope private val scope: CoroutineScope,
    ) : AgentResponder {
        private val typingIds = MutableStateFlow<Set<String>>(emptySet())
        override val typing: StateFlow<Set<String>> = typingIds.asStateFlow()

        private val locks = mutableMapOf<String, Mutex>()

        override fun onUserMessage(
            userId: String,
            conversationId: String,
            messageId: String,
            text: String,
        ) {
            scope.launch {
                guarded("deliver") {
                    delay(timing.deliverMillis)
                    chats.markDelivered(messageId)
                }
                lockFor(conversationId).withLock {
                    guarded("reply") { answer(userId, conversationId, text) }
                }
            }
        }

        private suspend fun answer(
            userId: String,
            conversationId: String,
            text: String,
        ) {
            val conversation = chats.observeConversation(userId, conversationId).first() ?: return
            delay(timing.readMillis)
            chats.markReadByAgent(conversationId)
            typingIds.update { it + conversationId }
            try {
                val body = compose(conversation, text)
                chats.receiveAgentMessage(conversationId, body) ?: return
                deliver(conversation, body)
            } finally {
                typingIds.update { it - conversationId }
            }
        }

        /** Builds the reply and waits the typing time it deserves. */
        private suspend fun compose(
            conversation: Conversation,
            text: String,
        ): String {
            val property = properties.observeProperty(conversation.propertyId).first()
            if (property == null) {
                delay(MIN_TYPING_MILLIS)
                return formatter.listingGone()
            }
            val now = clock.now()
            val days =
                generator.days(
                    propertyBookings = viewings.observePropertyBookings(property.id, now).first(),
                    userBookings = viewings.observeViewings(conversation.userId).first().map { it.range },
                )
            val reply =
                engine.reply(
                    AgentReplyRequest(
                        message = text,
                        property = property,
                        days = days,
                        now = now,
                        zone = zone,
                        firstReply = !chats.hasAgentReplied(conversation.id),
                    ),
                )
            delay(engine.typingDelay(reply).toMillis())
            return formatter.format(reply, zone, displayPreferences().first())
        }

        private suspend fun deliver(
            conversation: Conversation,
            body: String,
        ) {
            if (active.isActive(conversation.id)) {
                chats.markConversationRead(conversation.userId, conversation.id)
                return
            }
            val prefs = settings.settings.first()
            if (prefs.isEnabled(NotificationToggle.InApp)) {
                notifications.add(
                    conversation.userId,
                    NewNotification(
                        kind = NotificationKind.NewMessage,
                        subject = conversation.agent.name,
                        propertyId = conversation.propertyId,
                        preview = body,
                        conversationId = conversation.id,
                    ),
                )
            }
            if (prefs.isEnabled(NotificationToggle.Messages)) {
                notifier.post(
                    ChatNotification(conversation.id, conversation.agent.name, conversation.propertyTitle, body),
                )
            }
        }

        private fun lockFor(conversationId: String): Mutex =
            synchronized(locks) { locks.getOrPut(conversationId) { Mutex() } }

        /** A failed step is logged and skipped; it must never crash the process-wide scope. */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun guarded(
            what: String,
            block: suspend () -> Unit,
        ) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Demo agent could not $what", error)
            }
        }

        private companion object {
            const val TAG = "DemoAgent"
            const val MIN_TYPING_MILLIS = 1_200L
        }
    }
