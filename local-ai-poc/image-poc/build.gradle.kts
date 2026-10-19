plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.font.localimagepoc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.font.localimagepoc"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // MediaPipe 0.10.26.1 emits calls to the full protobuf Any.Builder API while its POM
    // declares protobuf-javalite. Exclude lite and use the matching full runtime to avoid
    // NoSuchMethodError at ImageGenerator.createFromOptions() on Android.
    implementation("com.google.mediapipe:tasks-vision-image-generator:0.10.26.1") {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
    implementation("com.google.protobuf:protobuf-java:4.32.0")
}
