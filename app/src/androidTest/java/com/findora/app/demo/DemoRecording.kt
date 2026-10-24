package com.findora.app.demo

/**
 * Marks a scripted demo segment: a walkthrough of one feature at human pace, recorded by the Demo recordings
 * workflow (one clip per test). The gating instrumented run skips these tests (`notAnnotation` runner argument
 * in app/build.gradle.kts) because they assert little and spend most of their time pausing for the camera.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class DemoRecording
