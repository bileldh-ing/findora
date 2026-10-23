package com.findora.app.core.testing

import com.findora.app.core.alert.ChannelTexts
import com.findora.app.core.alert.NewMatchesAlert
import com.findora.app.core.alert.PriceDropAlert
import com.findora.app.core.alert.SearchAlertNotifier
import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.SavedSearch
import com.findora.app.core.domain.repository.HomeWidgetRefresher
import com.findora.app.core.domain.repository.ListingsRefreshListener
import com.findora.app.core.domain.repository.NewSavedSearch
import com.findora.app.core.domain.repository.PriceWatchRepository
import com.findora.app.core.domain.repository.SavedSearchRepository
import com.findora.app.core.domain.repository.SearchAlertScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** In-memory saved searches with the same ownership rules as the Room repository. */
class FakeSavedSearchRepository(
    private val now: () -> Instant = { Instant.EPOCH },
) : SavedSearchRepository {
    val all = MutableStateFlow<List<SavedSearch>>(emptyList())
    val seen = mutableMapOf<Long, MutableSet<String>>()
    private var nextId = 1L

    override fun observeSavedSearches(userId: String): Flow<List<SavedSearch>> =
        all.map { list -> list.filter { it.userId == userId }.sortedByDescending { it.id } }

    override suspend fun savedSearch(
        userId: String,
        id: Long,
    ): SavedSearch? = all.value.firstOrNull { it.id == id && it.userId == userId }

    override suspend fun create(
        userId: String,
        search: NewSavedSearch,
    ): Long {
        val id = nextId++
        all.update {
            it +
                SavedSearch(
                    id = id,
                    userId = userId,
                    name = search.name.trim(),
                    query = search.query,
                    regionId = search.regionId,
                    filter = search.filter,
                    alertsEnabled = search.alertsEnabled,
                    frequency = search.frequency,
                    createdAt = now(),
                    lastCheckedAt = now(),
                )
        }
        seen[id] = search.seenIds.toMutableSet()
        return id
    }

    private fun edit(
        userId: String,
        id: Long,
        change: (SavedSearch) -> SavedSearch,
    ): Boolean {
        val found = all.value.any { it.id == id && it.userId == userId }
        if (found) all.update { list -> list.map { if (it.id == id) change(it) else it } }
        return found
    }

    override suspend fun rename(
        userId: String,
        id: Long,
        name: String,
    ): Boolean = name.isNotBlank() && edit(userId, id) { it.copy(name = name.trim()) }

    override suspend fun setAlertsEnabled(
        userId: String,
        id: Long,
        enabled: Boolean,
    ): Boolean = edit(userId, id) { it.copy(alertsEnabled = enabled) }

    override suspend fun setFrequency(
        userId: String,
        id: Long,
        frequency: AlertFrequency,
    ): Boolean = edit(userId, id) { it.copy(frequency = frequency) }

    override suspend fun delete(
        userId: String,
        id: Long,
    ): Boolean {
        val found = savedSearch(userId, id) != null
        if (found) {
            all.update { list -> list.filterNot { it.id == id } }
            seen.remove(id)
        }
        return found
    }

    override suspend fun alertingSearches(userId: String): List<SavedSearch> =
        all.value.filter { it.userId == userId && it.alertsEnabled }

    override suspend fun seenIds(id: Long): Set<String> = seen[id].orEmpty().toSet()

    override suspend fun recordCheck(
        id: Long,
        newlySeen: Set<String>,
        checkedAt: Instant,
        notified: Boolean,
    ) {
        seen.getOrPut(id) { mutableSetOf() } += newlySeen
        all.update { list -> list.map { if (it.id == id) it.copy(lastCheckedAt = checkedAt) else it } }
    }

    /** Pretends the last check of [id] happened at [at] (to make a daily search due). */
    fun setLastChecked(
        id: Long,
        at: Instant?,
    ) = all.update { list -> list.map { if (it.id == id) it.copy(lastCheckedAt = at) else it } }
}

class FakePriceWatchRepository : PriceWatchRepository {
    val byUser = mutableMapOf<String, Map<String, Long>>()

    override suspend fun baselines(userId: String): Map<String, Long> = byUser[userId].orEmpty()

    override suspend fun replace(
        userId: String,
        rents: Map<String, Long>,
    ) {
        byUser[userId] = rents
    }
}

class RecordingAlertScheduler :
    SearchAlertScheduler,
    ListingsRefreshListener {
    var scheduled = 0
    val refreshedRegions = mutableListOf<String>()

    override fun ensureScheduled() {
        scheduled++
    }

    override fun onRegionRefreshed(regionId: String) {
        refreshedRegions += regionId
    }
}

class RecordingWidgetRefresher : HomeWidgetRefresher {
    var scheduled = 0
    var refreshes = 0

    override fun ensureScheduled() {
        scheduled++
    }

    override fun requestRefresh() {
        refreshes++
    }
}

class RecordingSearchAlertNotifier(
    private val allowed: Boolean = true,
) : SearchAlertNotifier {
    val newMatches = mutableListOf<NewMatchesAlert>()
    val priceDrops = mutableListOf<PriceDropAlert>()

    override fun postNewMatches(
        alert: NewMatchesAlert,
        channel: ChannelTexts,
    ): Boolean {
        if (allowed) newMatches += alert
        return allowed
    }

    override fun postPriceDrop(
        alert: PriceDropAlert,
        channel: ChannelTexts,
    ): Boolean {
        if (allowed) priceDrops += alert
        return allowed
    }
}
