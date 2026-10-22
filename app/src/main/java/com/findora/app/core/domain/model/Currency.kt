package com.findora.app.core.domain.model

/**
 * Currencies Findora can display. [fractionDigits] follows ISO 4217 (the Tunisian dinar has
 * three minor digits); rents are quoted in whole units, so formatting hides fractions.
 */
enum class Currency(
    val code: String,
    val fractionDigits: Int,
) {
    INR("INR", 2),
    USD("USD", 2),
    EUR("EUR", 2),
    TND("TND", 3),
    AED("AED", 2),
    ;

    companion object {
        fun fromCode(code: String): Currency =
            entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("Unsupported currency code: $code")
    }
}
