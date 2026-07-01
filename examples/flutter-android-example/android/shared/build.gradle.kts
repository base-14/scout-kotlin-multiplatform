import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Compose Multiplatform UI primitives. Exposed via `api` so the
                // consuming Android :app gets a consistent set of compose
                // artifacts transitively.
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material3)
                api(compose.ui)
            }
        }
    }
}

android {
    namespace = "io.base14.hybrid_demo.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
