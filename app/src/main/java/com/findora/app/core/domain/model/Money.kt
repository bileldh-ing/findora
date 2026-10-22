package com.findora.app.core.domain.model

import androidx.compose.runtime.Immutable

/** An amount in whole major units (rent is always quoted in whole units) and its [currency]. */
@Immutable
data class Money(
    val amount: Long,
    val currency: Currency,
) : Comparable<Money> {
    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amount = amount + other.amount)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amount = amount - other.amount)
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) { "Cannot combine ${currency.code} with ${other.currency.code}" }
    }
}
