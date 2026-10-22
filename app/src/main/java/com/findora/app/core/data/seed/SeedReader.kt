package com.findora.app.core.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the bundled sample dataset. Abstracted so JVM tests can read the same files from disk. */
interface SeedReader {
    fun readListings(): SeedListingsDto

    fun readRegions(): SeedRegionsDto
}

/** Parses seed JSON from any stream source; shared by the asset reader and tests. */
open class StreamSeedReader(
    private val json: Json,
    private val open: (String) -> InputStream,
) : SeedReader {
    override fun readListings(): SeedListingsDto = read(LISTINGS)

    override fun readRegions(): SeedRegionsDto = read(REGIONS)

    private inline fun <reified T> read(path: String): T =
        open(path).bufferedReader(Charsets.UTF_8).use { json.decodeFromString<T>(it.readText()) }

    companion object {
        const val LISTINGS = "seed/listings.json"
        const val REGIONS = "seed/regions.json"
    }
}

@Singleton
class AssetSeedReader
    @Inject
    constructor(
        @ApplicationContext context: Context,
        json: Json,
    ) : StreamSeedReader(json, { path -> context.assets.open(path) })
