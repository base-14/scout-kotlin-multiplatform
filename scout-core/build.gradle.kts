plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
}

// iOS targets are gated so the default (Android) build/publish never needs the Kotlin/Native
// toolchain. Enable for the iOS phase with: -Pscout.enableIos=true
val enableIos = providers.gradleProperty("scout.enableIos").orNull == "true"

kotlin {
    compilerOptions {
        // Emit metadata consumable by Kotlin 2.0+ apps (matches opentelemetry-kotlin's floor).
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        optIn.add("io.opentelemetry.kotlin.ExperimentalApi")
        optIn.add("io.opentelemetry.kotlin.semconv.IncubatingApi")
    }

    jvm()

    androidLibrary {
        namespace = "io.base14.scout.core"
        compileSdk = 35
        minSdk = 21
    }

    if (enableIos) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.otel.api)
            api(libs.otel.sdk.api)
            implementation(libs.otel.implementation)
            implementation(libs.otel.exporters.core)
            // exporters-persistence transitively pulls exporters-otlp → ktor 3.4.2 → Kotlin 2.3 stdlib.
            // We use our own OTLP/JSON exporter, so exclude the unused OTLP module to keep the
            // Kotlin 2.1 consumer floor. (Disk serialization uses exporters-protobuf, kept.)
            implementation("io.opentelemetry.kotlin:exporters-persistence:0.4.0") {
                exclude(group = "io.opentelemetry.kotlin", module = "exporters-otlp")
            }
            implementation(libs.otel.semconv)
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.encoding)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.annotation)
        }
        if (enableIos) {
            val iosMain = maybeCreate("iosMain")
            iosMain.dependsOn(getByName("commonMain"))
            getByName("iosArm64Main").dependsOn(iosMain)
            getByName("iosSimulatorArm64Main").dependsOn(iosMain)
            iosMain.dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
    }
}
