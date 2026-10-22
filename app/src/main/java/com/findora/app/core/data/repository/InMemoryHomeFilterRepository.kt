package com.findora.app.core.data.repository

import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.repository.HomeFilterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Process-scoped filter state; it resets when another user claims it, so each user starts from the default feed. */
@Singleton
class InMemoryHomeFilterRepository
    @Inject
    constructor() : HomeFilterRepository {
        private val state = MutableStateFlow(PropertyFilter())
        override val filter: StateFlow<PropertyFilter> = state.asStateFlow()

        override fun update(transform: (PropertyFilter) -> PropertyFilter) = state.update(transform)

        private var owner: String? = null

        override fun reset() {
            state.value = PropertyFilter()
        }

        @Synchronized
        override fun claim(ownerId: String) {
            if (owner != null && owner != ownerId) reset()
            owner = ownerId
        }
    }
