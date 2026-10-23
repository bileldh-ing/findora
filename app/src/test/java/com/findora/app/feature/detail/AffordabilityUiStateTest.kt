package com.findora.app.feature.detail

import com.findora.app.core.domain.insight.AffordabilityCalculator
import com.findora.app.core.domain.insight.AffordabilityVerdict
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AffordabilityUiStateTest {
    private val initial = AffordabilityUiState(Money(1_200, Currency.EUR))

    @Test
    fun startsEmptyWithDefaults() {
        assertNull(initial.report)
        assertNull(initial.incomeError)
        assertEquals(AffordabilityUiState.DEFAULT_DEPOSIT_MONTHS, initial.depositMonths)
        assertEquals(AffordabilityUiState.DEFAULT_AGENCY_FEE_PERCENT, initial.agencyFeePercent)
        assertEquals("600", initial.movingText)
    }

    @Test
    fun typingAnIncomeProducesAReportInTheListingCurrency() {
        val state = initial.reduce(AffordabilityEvent.IncomeChanged("4 000"))
        assertEquals("4000", state.incomeText)
        val report = requireNotNull(state.report)
        assertEquals(AffordabilityVerdict.Comfortable, report.verdict)
        assertEquals(1_200L, report.maxRent)
        assertEquals(Money(2_800, Currency.EUR), state.money(report.remainingIncome))
        // 2 months deposit + 5% of a year's rent (720) + 600 moving.
        assertEquals(2_400L + 720L + 600L, report.upfrontTotal)
    }

    @Test
    fun sanitizesAmounts() {
        assertEquals("1500", AffordabilityUiState.sanitizeAmount("1,500"))
        assertEquals("1500", AffordabilityUiState.sanitizeAmount("-1.500 €"))
        assertEquals("0", AffordabilityUiState.sanitizeAmount("000"))
        assertEquals("42", AffordabilityUiState.sanitizeAmount("0042"))
        assertEquals("", AffordabilityUiState.sanitizeAmount("abc"))
        // Arabic-Indic digits typed on an Arabic keyboard.
        assertEquals("2500", AffordabilityUiState.sanitizeAmount("\u0662\u0665\u0660\u0660"))
        assertEquals(AffordabilityUiState.MAX_DIGITS, AffordabilityUiState.sanitizeAmount("9".repeat(20)).length)
    }

    @Test
    fun flagsZeroAndTooLargeAmounts() {
        assertEquals(AffordabilityFieldError.IncomeZero, initial.reduce(AffordabilityEvent.IncomeChanged("0")).incomeError)
        val huge = initial.reduce(AffordabilityEvent.IncomeChanged("9".repeat(13)))
        assertEquals(AffordabilityFieldError.AmountTooLarge, huge.incomeError)
        assertNull(huge.report)
        val moving =
            initial
                .reduce(AffordabilityEvent.IncomeChanged("5000"))
                .reduce(AffordabilityEvent.MovingEstimateChanged("9".repeat(13)))
        assertEquals(AffordabilityFieldError.AmountTooLarge, moving.movingError)
        assertNull(moving.report)
    }

    @Test
    fun clampsSlidersAndTreatsAnEmptyMovingEstimateAsZero() {
        val state =
            initial
                .reduce(AffordabilityEvent.IncomeChanged("5000"))
                .reduce(AffordabilityEvent.DepositMonthsChanged(99))
                .reduce(AffordabilityEvent.AgencyFeeChanged(-3))
                .reduce(AffordabilityEvent.MovingEstimateChanged(""))
        assertEquals(AffordabilityCalculator.MAX_DEPOSIT_MONTHS, state.depositMonths)
        assertEquals(0, state.agencyFeePercent)
        val report = requireNotNull(state.report)
        assertEquals(1_200L * 6, report.upfrontTotal)
    }

    @Test
    fun opensAndCloses() {
        val open = initial.reduce(AffordabilityEvent.Open)
        assertEquals(true, open.isOpen)
        assertEquals(false, open.reduce(AffordabilityEvent.Dismiss).isOpen)
    }
}
