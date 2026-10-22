import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
}

/**
 * Reads a release setting from an environment variable (CI) or a Gradle property
 * (for local builds, e.g. in ~/.gradle/gradle.properties). Blank values count as absent.
 */
fun releaseSetting(
    env: String,
    property: String,
): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(property).orNull?.takeIf { it.isNotBlank() }

val releaseKeystorePath = releaseSetting("FINDORA_KEYSTORE_PATH", "findora.keystore.path")
val releaseKeystorePassword = releaseSetting("FINDORA_KEYSTORE_PASSWORD", "findora.keystore.password")
val releaseKeyAlias = releaseSetting("FINDORA_KEY_ALIAS", "findora.key.alias")
val releaseKeyPassword = releaseSetting("FINDORA_KEY_PASSWORD", "findora.key.password")
val hasReleaseSigning =
    listOf(releaseKeystorePath, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }
val releaseVersionCode = releaseSetting("FINDORA_VERSION_CODE", "findora.versionCode")?.toInt()
val releaseVersionName = releaseSetting("FINDORA_VERSION_NAME", "findora.versionName")

android {
    namespace = "com.findora.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.findora.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val keystore = file(requireNotNull(releaseKeystorePath))
                check(keystore.isFile) { "Release keystore not found at $keystore (FINDORA_KEYSTORE_PATH)." }
                storeFile = keystore
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Upload-key signed when the FINDORA_* signing settings are present (see docs/RELEASING.md);
            // otherwise debug-signed so CI and recruiters can still build and install the release APK.
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        lintConfig = file("lint.xml")
        // Version-bump nags are not code defects; Dependabot/Renovate handles them.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

androidComponents {
    // CI injects versionCode/versionName (FINDORA_VERSION_CODE / FINDORA_VERSION_NAME); defaultConfig is the fallback.
    onVariants { variant ->
        variant.outputs.forEach { output ->
            releaseVersionCode?.let { output.versionCode.set(it) }
            releaseVersionName?.let { output.versionName.set(it) }
        }
    }
    // Compose UI tests need the ui-test-manifest activity, which only debug builds merge.
    // Release-specific behaviour is covered by tests that toggle the build flag explicitly.
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }) {
    if (hasReleaseSigning) {
        logger.lifecycle("Findora release signing: upload keystore (key alias '$releaseKeyAlias').")
    } else {
        logger.warn(
            "Findora release signing: FINDORA_KEYSTORE_* settings not found, release artifacts are DEBUG-SIGNED " +
                "and cannot be uploaded to Google Play. See docs/RELEASING.md.",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

composeCompiler {
    // Enable with -PcomposeMetrics to inspect stability/recomposition reports.
    if (project.hasProperty("composeMetrics")) {
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
        reportsDestination = layout.buildDirectory.dir("compose_reports")
    }
}

tasks.withType<Test>().configureEach {
    // Robolectric needs headroom; override on small machines with -PtestMaxHeap=512m.
    maxHeapSize = providers.gradleProperty("testMaxHeap").getOrElse("2g")
}

kover {
    reports {
        filters {
            excludes {
                // Generated code and pure UI wiring are excluded; domain/data are measured.
                classes("*Hilt_*", "*_Factory*", "*_HiltModules*", "*.BuildConfig", "*ComposableSingletons*")
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
