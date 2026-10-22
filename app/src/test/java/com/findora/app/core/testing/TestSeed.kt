package com.findora.app.core.testing

import com.findora.app.core.common.AppClock
import com.findora.app.core.data.seed.ListingDto
import com.findora.app.core.data.seed.StreamSeedReader
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.time.Instant

/** Unit tests run with the module directory as working directory, so the real bundled assets are on disk here. */
val assetsDir = File("src/main/assets")

val testJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

/** Reads the same seed files the app ships, straight from src/main/assets. */
fun fileSeedReader(): StreamSeedReader = StreamSeedReader(testJson) { path -> File(assetsDir, path).inputStream() }

val seedListings: List<ListingDto> by lazy { fileSeedReader().readListings().listings }

/** Manually advanced clock for TTL and timestamp tests. */
class MutableClock(
    var current: Instant = Instant.parse("2026-10-22T09:00:00Z"),
) : AppClock {
    override fun now(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
