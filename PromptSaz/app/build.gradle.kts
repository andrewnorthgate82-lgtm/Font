/*
 * PromptSaz — app module.
 *
 * AGP 9 notes:
 *  - No `org.jetbrains.kotlin.android` plugin: AGP 9 provides built-in Kotlin
 *    (Kotlin 2.2.10). Do not add a `kotlin { compilerOptions { } }` block —
 *    jvmTarget follows android.compileOptions (verified working matrix).
 *  - Room is wired through plain KSP arguments instead of the Room Gradle
 *    plugin (the plugin predates AGP 9 built-in Kotlin and interferes with
 *    KSP task registration).
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.promptsaz.app"

    // Compose 1.12 / Navigation 2.10 / androidx.hilt 1.4 artifacts require
    // compiling against API 37.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.promptsaz.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // Robolectric graphic tests need the merged resources + assets.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    // Room without the Room Gradle plugin: schema export for future
    // auto-migrations + idiomatic Kotlin codegen.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Lifecycle + Navigation
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Preferences + secure storage
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Kotlin ecosystem
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Unit tests (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Robolectric — actually RENDERS Compose UI on the JVM so the graphic
    // regression tests (drawer opacity, insets) run in plain unit tests.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
