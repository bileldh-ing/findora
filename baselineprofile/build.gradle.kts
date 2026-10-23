import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Drives the minified app on an emulator/device with UiAutomator: generates the Baseline Profile and runs the
// startup Macrobenchmark. Nothing here ships in the app. See docs/PERFORMANCE.md.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.findora.baselineprofile"
    compileSdk = 35

    defaultConfig {
        // Baseline Profile generation without root needs API 33+; the CI emulator is API 34.
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Emulators are fine for profile generation and for relative (None vs Partial) startup numbers;
        // absolute timings still need a physical mid-range device.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

baselineProfile {
    // Runs on the emulator already booted by the CI job (or any connected device locally).
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    onVariants { variant ->
        val artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { artifactsLoader.load(it)?.applicationId.orEmpty() },
        )
    }
}
