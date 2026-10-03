/*
 * PromptSaz — root build file.
 *
 * All plugins used by subprojects are declared here (apply false) so the build
 * script classpath stays identical across modules.
 *
 * NOTE (AGP 9): there is deliberately NO `org.jetbrains.kotlin.android` plugin —
 * AGP 9 ships built-in Kotlin support. The Kotlin version on the classpath is
 * raised to 2.2.10 by the Compose and serialization plugins below.
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
