package com.findora.app.feature.detail

import androidx.compose.runtime.Immutable
import com.findora.app.core.domain.insight.AffordabilityCalculator
import com.findora.app.core.domain.insight.AffordabilityError
import com.findora.app.core.domain.insight.AffordabilityInput
import com.findora.app.core.domain.insight.AffordabilityReport
import com.findora.app.core.domain.insight.AffordabilityResult
import com.findora.app.core.domain.model.Money

/** User actions in the "Can I afford it?" calculator. */
sealed interface AffordabilityEvent {
    data object Open : AffordabilityEvent

    data object Dismiss : AffordabilityEvent

    data class IncomeChanged(
        val text: String,
    ) : AffordabilityEvent

    data class DepositMonthsChanged(
        val months: Int,
    ) : AffordabilityEvent

    data class AgencyFeeChanged(
        val percent: Int,
    ) : AffordabilityEvent

    data class MovingEstimateChanged(
        val text: String,
    ) : AffordabilityEvent
}

/** Which field an input problem belongs to, so the sheet can show it under that field. */
enum class AffordabilityFieldError {
    /** Income is zero. */
    IncomeZero,

    /** More digits than the calculator accepts. */
    AmountTooLarge,
}

/**
 * The calculator's form and result for one listing's [rent]. Text fields hold digits only; [report] is
 * null until a valid income is entered. Reduced with [reduce], a pure function shared by the ViewModel
 * and tests.
 */
@Immutable
data class AffordabilityUiState(
    val rent: Money,
    val isOpen: Boolean = false,
    val incomeText: String = "",
    val depositMonths: Int = DEFAULT_DEPOSIT_MONTHS,
    val agencyFeePercent: Int = DEFAULT_AGENCY_FEE_PERCENT,
    val movingText: String = AffordabilityCalculator.suggestedMovingEstimate(rent.amount).toString(),
) {
    private val result: AffordabilityResult? =
        incomeText.toLongOrNull()?.let { income ->
            AffordabilityCalculator.evaluate(
                AffordabilityInput(
                    monthlyIncome = income,
                    rent = rent.amount,
                    depositMonths = depositMonths,
                    agencyFeePercent = agencyFeePercent,
                    movingEstimate = movingText.toLongOrNull() ?: 0L,
                ),
            )
        }

    val report: AffordabilityReport? get() = (result as? AffordabilityResult.Success)?.report

    private val errors: Set<AffordabilityError> get() = (result as? AffordabilityResult.Invalid)?.errors.orEmpty()

    val incomeError: AffordabilityFieldError?
        get() =
            when {
                AffordabilityError.IncomeTooLarge in errors -> AffordabilityFieldError.AmountTooLarge
                AffordabilityError.IncomeNotPositive in errors -> AffordabilityFieldError.IncomeZero
                else -> null
            }

    val movingError: AffordabilityFieldError?
        get() =
            if (AffordabilityError.MovingEstimateTooLarge in errors) AffordabilityFieldError.AmountTooLarge else null

    fun money(amount: Long): Money = Money(amount, rent.currency)

    fun reduce(event: AffordabilityEvent): AffordabilityUiState =
        when (event) {
            AffordabilityEvent.Open -> copy(isOpen = true)
            AffordabilityEvent.Dismiss -> copy(isOpen = false)
            is AffordabilityEvent.IncomeChanged -> copy(incomeText = sanitizeAmount(event.text))
            is AffordabilityEvent.DepositMonthsChanged ->
                copy(depositMonths = event.months.coerceIn(0, AffordabilityCalculator.MAX_DEPOSIT_MONTHS))
            is AffordabilityEvent.AgencyFeeChanged ->
                copy(agencyFeePercent = event.percent.coerceIn(0, AffordabilityCalculator.MAX_AGENCY_FEE_PERCENT))
            is AffordabilityEvent.MovingEstimateChanged -> copy(movingText = sanitizeAmount(event.text))
        }

    companion object {
        const val DEFAULT_DEPOSIT_MONTHS = 2
        const val DEFAULT_AGENCY_FEE_PERCENT = 5

        /** One digit more than [AffordabilityCalculator.MAX_AMOUNT] so "too large" can still be shown. */
        const val MAX_DIGITS = 13

        /**
         * Keeps ASCII and locale digits (Arabic-Indic, Eastern Arabic-Indic) as ASCII, drops everything else
         * (grouping separators, spaces, signs) and leading zeros, and caps the length at [MAX_DIGITS].
         */
        fun sanitizeAmount(text: String): String {
            val digits =
                text
                    .mapNotNull { char -> Character.digit(char, RADIX).takeIf { it >= 0 } }
                    .joinToString("")
                    .take(MAX_DIGITS)
            return if (digits.isEmpty()) "" else digits.trimStart('0').ifEmpty { "0" }
        }

        private const val RADIX = 10
    }
}
