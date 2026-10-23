package com.findora.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup (cold and warm) and Home-scroll frame timing of the minified `benchmarkRelease` build, without any
 * ahead-of-time compilation ([CompilationMode.None]) versus with the Baseline Profile installed
 * ([CompilationMode.Partial]). The difference between the two is what the profile buys a fresh Play install.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupNoCompilation() = startup(StartupMode.COLD, CompilationMode.None())

    @Test
    fun coldStartupBaselineProfile() = startup(StartupMode.COLD, baselineProfile())

    @Test
    fun warmStartupNoCompilation() = startup(StartupMode.WARM, CompilationMode.None())

    @Test
    fun warmStartupBaselineProfile() = startup(StartupMode.WARM, baselineProfile())

    @Test
    fun homeScrollNoCompilation() = homeScroll(CompilationMode.None())

    @Test
    fun homeScrollBaselineProfile() = homeScroll(baselineProfile())

    private fun baselineProfile() = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)

    private fun startup(
        mode: StartupMode,
        compilation: CompilationMode,
    ) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = mode,
        iterations = STARTUP_ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        waitForFirstScreen()
    }

    private fun homeScroll(compilation: CompilationMode) =
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.WARM,
            iterations = SCROLL_ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                waitForFirstScreen()
                continueAsGuestIfNeeded()
            },
        ) {
            scrollHomeFeed()
        }

    private companion object {
        const val STARTUP_ITERATIONS = 5
        const val SCROLL_ITERATIONS = 3
    }
}
