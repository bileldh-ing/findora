package com.findora.app.core.domain.repository

import com.findora.app.core.domain.format.ExchangeRates
import com.findora.app.core.domain.model.AboutCredits

/** Read-only data bundled with the app: the indicative exchange-rate table and the About screen credits. */
interface ReferenceDataRepository {
    /** The bundled rates, or null when the table could not be read (amounts then stay in listing currency). */
    suspend fun exchangeRates(): ExchangeRates?

    suspend fun aboutCredits(): AboutCredits
}
