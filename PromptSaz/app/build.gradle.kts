/*
 * PromptSaz — app module.
 *
 * AGP 9 notes:
 *  - No `org.jetbrains.kotlin.android` plugin: AGP 9 provides built-in Kotlin.
 *  - The Compose and serialization plugins bring Kotlin 2.4.20 onto the classpath,
 *    which raises the built-in Kotlin version above AGP's default.
 *  - Kotlin compiler options go in the top-level `kotlin { compilerOptions { } }` DSL.
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.promptsaz.app"

    // Compose 1.12 / Navigation 2.10 / androidx.hilt 1.4 artifacts require
    // compiling against API 37 (AGP 9.4 supports up to 37).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.promptsaz.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

room {
    // Exported schemas enable auto-migrations and are committed to version control.
    schemaDirectory("$projectDir/schemas")
}

hilt {
    // KSP2-safe aggregation: the ASM aggregating task cannot resolve
    // annotations in the same module under built-in Kotlin + KSP 2.3.x.
    enableAggregatingTask = false
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
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
}
