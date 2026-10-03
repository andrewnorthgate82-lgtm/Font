/*
 * PromptSaz — «پرامپت‌ساز»
 * Root Gradle settings. Single-module app.
 *
 * Toolchain (versions verified 2026-10-02):
 *   AGP 9.4.0 (built-in Kotlin, new DSL) · Gradle 9.8.0 · JDK 17+
 */
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PromptSaz"

include(":app")

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
    """
    PromptSaz requires JDK 17 or newer (AGP 9 requirement), but the current JDK is ${JavaVersion.current()}.
    Java Home: [${System.getProperty("java.home")}]
    In Android Studio: Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK.
    https://developer.android.com/build/jdks#jdk-config-in-studio
    """.trimIndent()
}
