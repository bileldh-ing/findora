package com.findora.app.core.data.remote

import com.findora.app.core.domain.model.DataError
import javax.inject.Inject
import javax.inject.Singleton

/** How the fake backend misbehaves. Switchable at runtime (debug panel, tests). */
sealed interface FailureMode {
    data object None : FailureMode

    data class Always(
        val error: DataError,
    ) : FailureMode

    /** Each call fails with probability [rate] (0.0 to 1.0). */
    data class Random(
        val rate: Double,
        val error: DataError = DataError.Network,
    ) : FailureMode {
        init {
            require(rate in 0.0..1.0) { "rate must be within 0..1" }
        }
    }
}

@Singleton
class FakeRemoteConfig
    @Inject
    constructor() {
        @Volatile
        var latencyMillis: LongRange = DEFAULT_LATENCY

        @Volatile
        var failureMode: FailureMode = FailureMode.None

        private val pendingFailures = ArrayDeque<DataError>()

        /** Makes the next [count] calls fail with [error], then falls back to [failureMode]. */
        @Synchronized
        fun failNext(
            count: Int = 1,
            error: DataError = DataError.Network,
        ) {
            require(count > 0) { "count must be positive" }
            repeat(count) { pendingFailures.addLast(error) }
        }

        @Synchronized
        internal fun takePendingFailure(): DataError? = pendingFailures.removeFirstOrNull()

        @Synchronized
        fun reset() {
            latencyMillis = DEFAULT_LATENCY
            failureMode = FailureMode.None
            pendingFailures.clear()
        }

        companion object {
            val DEFAULT_LATENCY = 150L..600L
        }
    }
