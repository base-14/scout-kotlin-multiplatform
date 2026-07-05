plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    `maven-publish`
}

// iOS targets gated so the default Android build never needs the Kotlin/Native toolchain.
val enableIos = providers.gradleProperty("scout.enableIos").orNull == "true"

kotlin {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        optIn.add("io.opentelemetry.kotlin.ExperimentalApi")
        optIn.add("io.opentelemetry.kotlin.semconv.IncubatingApi")
    }

    androidLibrary {
        namespace = "io.base14.scout.kmp"
        compileSdk = 35
        minSdk = 21
    }

    if (enableIos) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":scout-core"))
        }
        androidMain.dependencies {
            api(project(":scout-android"))
        }
        if (enableIos) {
            val iosMain = maybeCreate("iosMain")
            iosMain.dependsOn(getByName("commonMain"))
            getByName("iosArm64Main").dependsOn(iosMain)
            getByName("iosSimulatorArm64Main").dependsOn(iosMain)
            iosMain.dependencies {
                api(project(":scout-ios"))
            }
        }
    }
}

group = "io.base14"
version = "0.1.19"
