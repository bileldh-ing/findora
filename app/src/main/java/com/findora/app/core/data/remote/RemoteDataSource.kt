package com.findora.app.core.data.remote

import com.findora.app.core.data.seed.ListingDto
import com.findora.app.core.data.seed.RegionDto
import com.findora.app.core.domain.model.DataError
import java.io.IOException

/**
 * The listings API. The default binding is [FakeRemoteDataSource]; a Retrofit implementation can
 * replace it without touching repositories because both speak these DTOs.
 */
interface RemoteDataSource {
    suspend fun fetchRegions(): List<RegionDto>

    /** One page of a region's listings, newest first. [page] starts at 0. */
    suspend fun fetchListings(
        regionId: String,
        page: Int,
        pageSize: Int,
    ): ListingPage

    suspend fun fetchListing(id: String): ListingDto
}

data class ListingPage(
    val items: List<ListingDto>,
    val page: Int,
    val totalCount: Int,
    val nextPage: Int?,
)

/** Transport-level failure, mapped to a domain [DataError] by repositories. */
class RemoteException(
    val error: DataError,
    message: String,
) : IOException(message)
