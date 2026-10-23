package com.findora.app.core.data.export

import com.findora.app.BuildConfig
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.ChatRepository
import com.findora.app.core.domain.repository.FavoritesRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.SearchHistoryRepository
import com.findora.app.core.domain.repository.SettingsRepository
import com.findora.app.core.domain.repository.UserDataExporter
import com.findora.app.core.domain.repository.ViewingRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
internal data class UserDataExportDto(
    val format: String = FORMAT,
    val appVersion: String,
    val exportedAt: String,
    val profile: ProfileDto,
    val preferences: PreferencesDto,
    val favourites: List<FavouriteDto>,
    val viewings: List<ViewingDto>,
    val conversations: List<ConversationDto>,
    val recentSearches: List<String>,
) {
    companion object {
        const val FORMAT = "findora-user-data/1"
    }
}

@Serializable
internal data class ProfileDto(
    val userId: String,
    val guest: Boolean,
    val displayName: String? = null,
    val email: String? = null,
)

@Serializable
internal data class PreferencesDto(
    val theme: String,
    val language: String,
    val displayCurrency: String?,
    val areaUnit: String,
    val reduceMotion: String,
    val messageNotifications: Boolean,
    val viewingReminderNotifications: Boolean,
    val inAppNotifications: Boolean,
)

@Serializable
internal data class FavouriteDto(
    val propertyId: String,
    val title: String,
    val locality: String,
    val rentPerMonth: Long,
    val currency: String,
)

@Serializable
internal data class ViewingDto(
    val propertyId: String,
    val propertyTitle: String?,
    val startAt: String,
    val durationMinutes: Int,
    val note: String,
    val bookedAt: String,
)

@Serializable
internal data class MessageDto(
    val from: String,
    val body: String,
    val sentAt: String,
)

@Serializable
internal data class ConversationDto(
    val propertyId: String,
    val propertyTitle: String,
    val agent: String,
    val startedAt: String,
    val messages: List<MessageDto>,
)

/**
 * Exports one user's data as pretty-printed JSON (ISO-8601 UTC timestamps). Only that user's rows are read, so
 * a guest export never contains a signed-in account's data and vice versa. Secrets (password hash, session
 * token) are never part of it.
 */
class JsonUserDataExporter
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val settings: SettingsRepository,
        private val favorites: FavoritesRepository,
        private val viewings: ViewingRepository,
        private val chats: ChatRepository,
        private val properties: PropertyRepository,
        private val searchHistory: SearchHistoryRepository,
        private val clock: AppClock,
    ) : UserDataExporter {
        private val json =
            Json {
                prettyPrint = true
                encodeDefaults = true
            }

        override suspend fun export(userId: String): String {
            val account = auth.observeCurrentUser().first()?.takeIf { it.id == userId }
            val prefs = settings.settings.first()
            val dto =
                UserDataExportDto(
                    appVersion = BuildConfig.VERSION_NAME,
                    exportedAt = clock.now().toString(),
                    profile =
                        ProfileDto(
                            userId = userId,
                            guest = userId == AuthRepository.GUEST_USER_ID,
                            displayName = account?.displayName,
                            email = account?.email,
                        ),
                    preferences =
                        PreferencesDto(
                            theme = prefs.themeMode.name,
                            language = prefs.language.name,
                            displayCurrency = prefs.currencyOverride?.code,
                            areaUnit = prefs.areaUnit.name,
                            reduceMotion = prefs.reduceMotion.name,
                            messageNotifications = prefs.messageNotifications,
                            viewingReminderNotifications = prefs.viewingReminderNotifications,
                            inAppNotifications = prefs.inAppNotifications,
                        ),
                    favourites =
                        favorites.observeFavorites(userId).first().map {
                            FavouriteDto(it.id, it.title, it.locality, it.rent.amount, it.rent.currency.code)
                        },
                    viewings =
                        viewings.observeViewings(userId).first().map {
                            ViewingDto(
                                propertyId = it.propertyId,
                                propertyTitle = properties.observeProperty(it.propertyId).first()?.title,
                                startAt = it.startAt.toString(),
                                durationMinutes = it.durationMinutes,
                                note = it.note,
                                bookedAt = it.createdAt.toString(),
                            )
                        },
                    conversations = conversations(userId),
                    recentSearches =
                        searchHistory
                            .observeRecent(userId, SearchHistoryRepository.MAX_STORED)
                            .first()
                            .map { it.query },
                )
            return json.encodeToString(UserDataExportDto.serializer(), dto)
        }

        private suspend fun conversations(userId: String): List<ConversationDto> =
            chats.observeConversations(userId).first().map { summary ->
                val conversation = summary.conversation
                ConversationDto(
                    propertyId = conversation.propertyId,
                    propertyTitle = conversation.propertyTitle,
                    agent = conversation.agent.name,
                    startedAt = conversation.createdAt.toString(),
                    messages =
                        chats.observeMessages(userId, conversation.id).first().map {
                            MessageDto(if (it.fromUser) FROM_USER else FROM_AGENT, it.body, it.sentAt.toString())
                        },
                )
            }

        private companion object {
            const val FROM_USER = "you"
            const val FROM_AGENT = "agent"
        }
    }
