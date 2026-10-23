package com.findora.app.core.domain.alert

import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.repository.NewSavedSearch
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.SavedSearchRepository
import com.findora.app.core.domain.repository.SearchAlertScheduler
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Saves [filter] in [regionId] under [name] for [userId]. The listings matching it right now are remembered as
 * seen, so the first alert only reports homes that appear after saving.
 */
class SaveSearch
    @Inject
    constructor(
        private val savedSearches: SavedSearchRepository,
        private val properties: PropertyRepository,
        private val scheduler: SearchAlertScheduler,
    ) {
        suspend operator fun invoke(
            userId: String,
            name: String,
            query: String,
            regionId: String,
            filter: PropertyFilter,
        ): Long {
            val seen =
                properties
                    .observeProperties(regionId)
                    .first()
                    .filter(filter::accepts)
                    .mapTo(HashSet()) { it.id }
            val id = savedSearches.create(userId, NewSavedSearch(name, query, regionId, filter, seen))
            scheduler.ensureScheduled()
            return id
        }

    }
