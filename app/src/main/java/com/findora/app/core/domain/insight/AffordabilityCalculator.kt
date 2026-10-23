package com.findora.app.core.domain.insight

import androidx.compose.runtime.Immutable

/** How a rent sits against the 30% rule. */
enum class AffordabilityVerdict {
    /** Rent is at most 30% of monthly income. */
    Comfortable,

    /** Rent is above 30% but at most 40% of monthly income: possible, with less room for savings. */
    Stretch,

    /** Rent is above 40% of monthly income. */
    Over,
}

/** A rejected calculator input. Every problem is reported at once so the form can flag each field. */
enum class AffordabilityError {
    IncomeNotPositive,
    IncomeTooLarge,
    RentNotPositive,
    RentTooLarge,
    DepositMonthsOutOfRange,
    AgencyFeeOutOfRange,
    MovingEstimateNegative,
    MovingEstimateTooLarge,
}

/**
 * Calculator inputs, all in whole major units of the listing's currency.
 *
 * [agencyFeePercent] is a percentage of one year's rent (the usual way agencies quote it, e.g. 5% in Dubai,
 * about one month in India at 8%).
 */
@Immutable
data class AffordabilityInput(
    val monthlyIncome: Long,
    val rent: Long,
    val depositMonths: Int,
    val agencyFeePercent: Int,
    val movingEstimate: Long,
)

/** The result for a valid [AffordabilityInput]. */
@Immutable
data class AffordabilityReport(
    val monthlyIncome: Long,
    val rent: Long,
    /** The highest rent the 30% rule allows for this income. */
    val maxRent: Long,
    val verdict: AffordabilityVerdict,
    /** Rent as a share of income, 0.0 to any positive value (above 1.0 when rent exceeds income). */
    val rentShare: Double,
    /** Income left after rent, never below zero. */
    val remainingIncome: Long,
    val deposit: Long,
    val agencyFee: Long,
    val movingEstimate: Long,
) {
    /** Paid before moving in: deposit + agency fee + moving estimate. */
    val upfrontTotal: Long get() = deposit + agencyFee + movingEstimate
}

sealed interface AffordabilityResult {
    data class Success(
        val report: AffordabilityReport,
    ) : AffordabilityResult

    data class Invalid(
        val errors: Set<AffordabilityError>,
    ) : AffordabilityResult
}

/**
 * The 30% rule: rent should take at most 30% of gross monthly income. Pure Kotlin and exact: shares are
 * compared with integer arithmetic so a rent of exactly 30% is Comfortable on every device.
 */
object AffordabilityCalculator {
    const val RULE_PERCENT = 30
    const val STRETCH_PERCENT = 40
    const val MAX_DEPOSIT_MONTHS = 6
    const val MAX_AGENCY_FEE_PERCENT = 15

    /** Upper bound for any amount (just under one trillion), which keeps every product inside a Long. */
    const val MAX_AMOUNT = 999_999_999_999L

    private const val PERCENT = 100L
    private const val MONTHS_PER_YEAR = 12L

    /** Highest rent the rule allows, rounded down to a whole unit. */
    fun maxRent(monthlyIncome: Long): Long {
        require(monthlyIncome in 0..MAX_AMOUNT) { "Income out of range: $monthlyIncome" }
        return monthlyIncome * RULE_PERCENT / PERCENT
    }

    /** One year's rent times [percent], rounded half up to a whole unit. */
    fun agencyFee(
        rent: Long,
        percent: Int,
    ): Long = (rent * MONTHS_PER_YEAR * percent + PERCENT / 2) / PERCENT

    fun validate(input: AffordabilityInput): Set<AffordabilityError> =
        buildSet {
            when {
                input.monthlyIncome <= 0 -> add(AffordabilityError.IncomeNotPositive)
                input.monthlyIncome > MAX_AMOUNT -> add(AffordabilityError.IncomeTooLarge)
            }
            when {
                input.rent <= 0 -> add(AffordabilityError.RentNotPositive)
                input.rent > MAX_AMOUNT -> add(AffordabilityError.RentTooLarge)
            }
            if (input.depositMonths !in 0..MAX_DEPOSIT_MONTHS) add(AffordabilityError.DepositMonthsOutOfRange)
            if (input.agencyFeePercent !in 0..MAX_AGENCY_FEE_PERCENT) add(AffordabilityError.AgencyFeeOutOfRange)
            when {
                input.movingEstimate < 0 -> add(AffordabilityError.MovingEstimateNegative)
                input.movingEstimate > MAX_AMOUNT -> add(AffordabilityError.MovingEstimateTooLarge)
            }
        }

    fun evaluate(input: AffordabilityInput): AffordabilityResult {
        val errors = validate(input)
        if (errors.isNotEmpty()) return AffordabilityResult.Invalid(errors)
        val income = input.monthlyIncome
        val rent = input.rent
        val verdict =
            when {
                rent * PERCENT <= income * RULE_PERCENT -> AffordabilityVerdict.Comfortable
                rent * PERCENT <= income * STRETCH_PERCENT -> AffordabilityVerdict.Stretch
                else -> AffordabilityVerdict.Over
            }
        return AffordabilityResult.Success(
            AffordabilityReport(
                monthlyIncome = income,
                rent = rent,
                maxRent = maxRent(income),
                verdict = verdict,
                rentShare = rent.toDouble() / income,
                remainingIncome = (income - rent).coerceAtLeast(0),
                deposit = rent * input.depositMonths,
                agencyFee = agencyFee(rent, input.agencyFeePercent),
                movingEstimate = input.movingEstimate,
            ),
        )
    }

    /** A starting point for the moving estimate field: half a month's rent, which the user can edit. */
    fun suggestedMovingEstimate(rent: Long): Long = (rent / 2).coerceAtLeast(0)
}
