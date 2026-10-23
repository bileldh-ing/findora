package com.findora.app.core.data.reference

import android.content.Context
import android.util.Log
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.format.ExchangeRates
import com.findora.app.core.domain.model.AboutCredits
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.OpenSourceLibrary
import com.findora.app.core.domain.model.PhotoCredit
import com.findora.app.core.domain.repository.ReferenceDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class ExchangeRatesDto(
    val asOf: String,
    val base: String,
    val source: String = "",
    /** Rates are strings so they reach BigDecimal without a binary floating-point detour. */
    val rates: Map<String, String>,
)

@Serializable
internal data class LibraryDto(
    val name: String,
    val author: String,
    val license: String,
    val url: String,
)

@Serializable
internal data class PhotographerDto(
    val name: String,
    val url: String,
    val photos: Int,
)

@Serializable
internal data class PhotosDto(
    val source: String,
    val license: String,
    val licenseUrl: String,
    val photographers: List<PhotographerDto>,
)

@Serializable
internal data class MapCreditDto(
    val attribution: String,
    val license: String,
    val url: String,
)

@Serializable
internal data class LicensesDto(
    val libraries: List<LibraryDto>,
    val photos: PhotosDto,
    val map: MapCreditDto,
)

/** Parses the bundled reference files; pure so JVM tests feed it strings. */
internal object ReferenceDataParser {
    /** Currencies the app does not know are skipped, so a newer table never breaks an older app. */
    fun exchangeRates(
        json: Json,
        text: String,
    ): ExchangeRates {
        val dto = json.decodeFromString<ExchangeRatesDto>(text)
        val rates =
            dto.rates
                .mapNotNull { (code, rate) ->
                    Currency.entries.firstOrNull { it.code == code }?.let { it to BigDecimal(rate) }
                }.toMap()
        return ExchangeRates(LocalDate.parse(dto.asOf), Currency.fromCode(dto.base), rates, dto.source)
    }

    fun aboutCredits(
        json: Json,
        text: String,
    ): AboutCredits {
        val dto = json.decodeFromString<LicensesDto>(text)
        return AboutCredits(
            libraries = dto.libraries.map { OpenSourceLibrary(it.name, it.author, it.license, it.url) },
            photoSource = dto.photos.source,
            photoLicense = dto.photos.license,
            photoLicenseUrl = dto.photos.licenseUrl,
            photographers = dto.photos.photographers.map { PhotoCredit(it.name, it.url, it.photos) },
            mapAttribution = dto.map.attribution,
            mapLicense = dto.map.license,
            mapUrl = dto.map.url,
        )
    }
}

/** Reads `assets/rates/exchange_rates.json` and `assets/about/licenses.json` once, off the main thread. */
@Singleton
class AssetReferenceDataRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val json: Json,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : ReferenceDataRepository {
        private val mutex = Mutex()
        private var rates: ExchangeRates? = null

        override suspend fun exchangeRates(): ExchangeRates? =
            mutex.withLock {
                rates ?: readRates()?.also { rates = it }
            }

        override suspend fun aboutCredits(): AboutCredits =
            withContext(io) { ReferenceDataParser.aboutCredits(json, read(LICENSES)) }

        private suspend fun readRates(): ExchangeRates? =
            withContext(io) {
                try {
                    ReferenceDataParser.exchangeRates(json, read(RATES))
                } catch (error: IOException) {
                    Log.w(TAG, "Exchange rates unavailable", error)
                    null
                } catch (error: SerializationException) {
                    Log.w(TAG, "Exchange rates unreadable", error)
                    null
                } catch (error: DateTimeParseException) {
                    Log.w(TAG, "Exchange rates undated", error)
                    null
                } catch (error: IllegalArgumentException) {
                    Log.w(TAG, "Exchange rates invalid", error)
                    null
                }
            }

        private fun read(path: String): String =
            context.assets
                .open(path)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

        internal companion object {
            const val RATES = "rates/exchange_rates.json"
            const val LICENSES = "about/licenses.json"
            private const val TAG = "ReferenceData"
        }
    }
