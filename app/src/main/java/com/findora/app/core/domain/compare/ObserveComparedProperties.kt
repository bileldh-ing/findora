package com.findora.app.core.domain.compare

import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.CompareRepository
import com.findora.app.core.domain.repository.PropertyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The current user's compare selection as listings, in column order; listings no longer cached are left out. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveComparedProperties
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val compare: CompareRepository,
        private val properties: PropertyRepository,
    ) {
        /** The user the selection belongs to (the guest id when signed out). */
        val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()

        operator fun invoke(): Flow<List<Property>> =
            userId
                .flatMapLatest { compare.observe(it) }
                .flatMapLatest { ids ->
                    if (ids.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(ids.map { properties.observeProperty(it) }) { homes -> homes.filterNotNull() }
                    }
                }
    }
