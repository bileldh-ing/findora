package com.findora.app.core.common

import java.time.Instant

/** Injectable wall clock so time-dependent logic (cache TTL, timestamps) is testable. */
fun interface AppClock {
    fun now(): Instant
}

object SystemAppClock : AppClock {
    override fun now(): Instant = Instant.now()
}
