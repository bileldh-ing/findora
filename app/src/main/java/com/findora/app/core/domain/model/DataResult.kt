package com.findora.app.core.domain.model

/** Why a remote operation failed, independent of the transport. */
enum class DataError { Network, Timeout, Server, NotFound }

/** Outcome of refreshing the local cache from the remote source. */
sealed interface RefreshResult {
    /** The cache was replaced with [count] listings fetched from the remote source. */
    data class Fetched(
        val count: Int,
    ) : RefreshResult

    /** The cache was fresh enough, no request was made. */
    data class UpToDate(
        val count: Int,
    ) : RefreshResult

    /** The remote source failed; [cachedCount] listings are still available offline. */
    data class Failed(
        val error: DataError,
        val cachedCount: Int,
    ) : RefreshResult
}
