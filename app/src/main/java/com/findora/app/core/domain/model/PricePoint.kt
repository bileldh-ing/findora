package com.findora.app.core.domain.model

import androidx.compose.runtime.Immutable
import java.time.YearMonth

/** Monthly rent for one calendar month, in the listing's currency. */
@Immutable
data class PricePoint(
    val month: YearMonth,
    val amount: Long,
)
