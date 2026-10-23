package com.findora.app.core.domain.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AffordabilityCalculatorTest {
    private fun input(
        income: Long = 10_000,
        rent: Long = 3_000,
        deposit: Int = 2,
        fee: Int = 5,
        moving: Long = 500,
    ) = AffordabilityInput(income, rent, deposit, fee, moving)

    private fun report(input: AffordabilityInput): AffordabilityReport =
        (AffordabilityCalculator.evaluate(input) as AffordabilityResult.Success).report

    private fun errors(input: AffordabilityInput): Set<AffordabilityError> =
        (AffordabilityCalculator.evaluate(input) as AffordabilityResult.Invalid).errors

    @Test
    fun maxRentIsThirtyPercentRoundedDown() {
        assertEquals(3_000L, AffordabilityCalculator.maxRent(10_000))
        assertEquals(2_999L, AffordabilityCalculator.maxRent(9_999))
        assertEquals(0L, AffordabilityCalculator.maxRent(3))
        assertEquals(0L, AffordabilityCalculator.maxRent(0))
    }

    @Test
    fun maxRentRejectsOutOfRangeIncome() {
        assertThrows(IllegalArgumentException::class.java) { AffordabilityCalculator.maxRent(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            AffordabilityCalculator.maxRent(AffordabilityCalculator.MAX_AMOUNT + 1)
        }
    }

    @Test
    fun verdictBoundariesAreExact() {
        assertEquals(AffordabilityVerdict.Comfortable, report(input(rent = 3_000)).verdict)
        assertEquals(AffordabilityVerdict.Stretch, report(input(rent = 3_001)).verdict)
        assertEquals(AffordabilityVerdict.Stretch, report(input(rent = 4_000)).verdict)
        assertEquals(AffordabilityVerdict.Over, report(input(rent = 4_001)).verdict)
        // 30% of 3,333 is 999.9: a rent of 999 fits, 1,000 does not, despite maxRent rounding down to 999.
        assertEquals(AffordabilityVerdict.Comfortable, report(input(income = 3_333, rent = 999)).verdict)
        assertEquals(AffordabilityVerdict.Stretch, report(input(income = 3_333, rent = 1_000)).verdict)
    }

    @Test
    fun upfrontCostAddsDepositAgencyFeeAndMoving() {
        val result = report(input(rent = 3_000, deposit = 2, fee = 5, moving = 500))
        assertEquals(6_000L, result.deposit)
        // 5% of a year's rent: 36,000 * 0.05.
        assertEquals(1_800L, result.agencyFee)
        assertEquals(500L, result.movingEstimate)
        assertEquals(8_300L, result.upfrontTotal)
    }

    @Test
    fun agencyFeeRoundsToTheNearestUnit() {
        // 1,001 * 12 * 1% = 120.12 -> 120 (down); 1,001 * 12 * 7% = 840.84 -> 841 (up).
        assertEquals(120L, AffordabilityCalculator.agencyFee(1_001, 1))
        assertEquals(841L, AffordabilityCalculator.agencyFee(1_001, 7))
        // 1,004 * 12 * 15% = 1,807.2 -> 1,807; exact values stay exact.
        assertEquals(1_807L, AffordabilityCalculator.agencyFee(1_004, 15))
        assertEquals(150L, AffordabilityCalculator.agencyFee(1_250, 1))
        assertEquals(0L, AffordabilityCalculator.agencyFee(1_250, 0))
    }

    @Test
    fun zeroDepositAndFeeLeaveOnlyMoving() {
        val result = report(input(deposit = 0, fee = 0, moving = 0))
        assertEquals(0L, result.upfrontTotal)
        val maxed =
            report(
                input(deposit = AffordabilityCalculator.MAX_DEPOSIT_MONTHS, fee = AffordabilityCalculator.MAX_AGENCY_FEE_PERCENT),
            )
        assertEquals(18_000L, maxed.deposit)
        assertEquals(5_400L, maxed.agencyFee)
    }

    @Test
    fun shareAndRemainingIncome() {
        val result = report(input(income = 10_000, rent = 2_500))
        assertEquals(0.25, result.rentShare, 1e-9)
        assertEquals(7_500L, result.remainingIncome)
        val over = report(input(income = 2_000, rent = 3_000))
        assertEquals(1.5, over.rentShare, 1e-9)
        assertEquals(0L, over.remainingIncome)
        assertEquals(AffordabilityVerdict.Over, over.verdict)
    }

    @Test
    fun invalidInputsAreAllReported() {
        val all = errors(AffordabilityInput(0, 0, -1, 16, -1))
        assertEquals(
            setOf(
                AffordabilityError.IncomeNotPositive,
                AffordabilityError.RentNotPositive,
                AffordabilityError.DepositMonthsOutOfRange,
                AffordabilityError.AgencyFeeOutOfRange,
                AffordabilityError.MovingEstimateNegative,
            ),
            all,
        )
        val tooLarge = AffordabilityCalculator.MAX_AMOUNT + 1
        assertEquals(
            setOf(
                AffordabilityError.IncomeTooLarge,
                AffordabilityError.RentTooLarge,
                AffordabilityError.DepositMonthsOutOfRange,
                AffordabilityError.MovingEstimateTooLarge,
            ),
            errors(AffordabilityInput(tooLarge, tooLarge, 7, 15, tooLarge)),
        )
        assertEquals(setOf(AffordabilityError.IncomeNotPositive), errors(input(income = -5)))
    }

    @Test
    fun limitsAreInclusive() {
        val max = AffordabilityCalculator.MAX_AMOUNT
        val result = AffordabilityCalculator.evaluate(AffordabilityInput(max, max, 6, 15, max))
        assertTrue(result is AffordabilityResult.Success)
        val report = (result as AffordabilityResult.Success).report
        assertEquals(AffordabilityVerdict.Over, report.verdict)
        assertTrue(report.upfrontTotal > 0)
    }

    @Test
    fun suggestedMovingEstimateIsHalfARent() {
        assertEquals(1_500L, AffordabilityCalculator.suggestedMovingEstimate(3_000))
        assertEquals(0L, AffordabilityCalculator.suggestedMovingEstimate(1))
    }
}
