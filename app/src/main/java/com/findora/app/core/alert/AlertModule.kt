package com.findora.app.core.alert

import com.findora.app.core.data.repository.RoomPriceWatchRepository
import com.findora.app.core.data.repository.RoomSavedSearchRepository
import com.findora.app.core.domain.repository.HomeWidgetRefresher
import com.findora.app.core.domain.repository.ListingsRefreshListener
import com.findora.app.core.domain.repository.PriceWatchRepository
import com.findora.app.core.domain.repository.SavedSearchRepository
import com.findora.app.core.domain.repository.SearchAlertScheduler
import com.findora.app.widget.WorkManagerHomeWidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AlertModule {
    @Binds
    abstract fun bindSavedSearchRepository(impl: RoomSavedSearchRepository): SavedSearchRepository

    @Binds
    abstract fun bindPriceWatchRepository(impl: RoomPriceWatchRepository): PriceWatchRepository

    @Binds
    abstract fun bindSearchAlertScheduler(impl: WorkManagerSearchAlertScheduler): SearchAlertScheduler

    @Binds
    abstract fun bindListingsRefreshListener(impl: WorkManagerSearchAlertScheduler): ListingsRefreshListener

    @Binds
    abstract fun bindSearchAlertNotifier(impl: AndroidSearchAlertNotifier): SearchAlertNotifier

    @Binds
    abstract fun bindHomeWidgetRefresher(impl: WorkManagerHomeWidgetRefresher): HomeWidgetRefresher
}
