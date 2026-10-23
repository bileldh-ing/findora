package com.findora.app.core.domain.format

import androidx.compose.runtime.Immutable
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * A static table of indicative exchange rates: how many units of each currency one unit of [base] buys, as
 * published on [asOf]. Bundled with the app (no network); the UI always labels converted amounts as
 * indicative and shows the date.
 */
@Immutable
data class ExchangeRates(
    val asOf: LocalDate,
    val base: Currency,
    val perBase: Map<Currency, BigDecimal>,
    val source: String = "",
) {
    init {
        require(perBase[base]?.compareTo(BigDecimal.ONE) == 0) { "The base currency must have a rate of 1" }
        require(perBase.values.all { it.signum() > 0 }) { "Exchange rates must be positive" }
    }

    fun rate(currency: Currency): BigDecimal? = perBase[currency]
}

/**
 * Converts rents between currencies with [BigDecimal] arithmetic, never floating point.
 *
 * Rounding rules: the cross rate is computed as `amount × rate(to) ÷ rate(from)` at [INTERMEDIATE_SCALE]
 * decimal places (HALF_EVEN, so no systematic bias), then the result is rounded HALF_UP to whole major units,
 * because Findora quotes and displays rents in whole units. Converting to the same currency returns the input
 * unchanged; a currency missing from the table cannot be converted (null).
 */
class CurrencyConverter(
    val rates: ExchangeRates,
) {
    fun convert(
        money: Money,
        to: Currency,
    ): Money? {
        if (money.currency == to) return money
        val exact = convertExact(BigDecimal.valueOf(money.amount), money.currency, to) ?: return null
        return Money(exact.setScale(0, RoundingMode.HALF_UP).longValueExact(), to)
    }

    /** The unrounded converted amount (for derived figures such as price per square foot). */
    fun convertExact(
        amount: BigDecimal,
        from: Currency,
        to: Currency,
    ): BigDecimal? {
        if (from == to) return amount
        val fromRate = rates.rate(from) ?: return null
        val toRate = rates.rate(to) ?: return null
        return amount.multiply(toRate).divide(fromRate, INTERMEDIATE_SCALE, RoundingMode.HALF_EVEN)
    }

    companion object {
        const val INTERMEDIATE_SCALE = 10
    }
}
