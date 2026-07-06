plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
    }
}

android {
    namespace = "io.base14.scout.android"
    compileSdk = 35
    ndkVersion = "27.1.12297006"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a", "x86")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        // Lint runs in CI for signal, but warnings/issues don't fail the build.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Release pins scout-core to a published Maven version (gradle.properties `scout.coreVersion`) so an
// android-only release re-publishes nothing but itself. Only active with -Prelease; local builds use
// project(":scout-core") so source changes flow through immediately.
val coreDependency: Any =
    if (providers.gradleProperty("release").isPresent) {
        "io.base14:scout-core:${providers.gradleProperty("scout.coreVersion").get()}"
    } else {
        project(":scout-core")
    }

dependencies {
    api(coreDependency)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.jankstats)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    // Optional instrumentation surfaces — host provides them if used.
    compileOnly(libs.androidx.navigation.compose)
    compileOnly(libs.okhttp)
    compileOnly("androidx.compose.ui:ui:1.7.6") // for Compose semantics tap resolution

    testImplementation(libs.kotlin.test)

    // Instrumented (on-device) tests — real Android runtime + a local OTLP sink.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(libs.ktor.client.core)
    androidTestImplementation(libs.ktor.client.okhttp)
    androidTestImplementation(libs.kotlinx.coroutines)
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates("io.base14", "scout-android", version.toString())
    pom {
        name.set("Scout Android")
        description.set("Scout RUM SDK for Android — auto-instrumentation + OpenTelemetry export.")
        url.set("https://github.com/base-14/scout-kotlin-multiplatform")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("base-14")
                name.set("base14")
                url.set("https://base14.io")
            }
        }
        scm {
            url.set("https://github.com/base-14/scout-kotlin-multiplatform")
            connection.set("scm:git:git://github.com/base-14/scout-kotlin-multiplatform.git")
            developerConnection.set("scm:git:ssh://git@github.com/base-14/scout-kotlin-multiplatform.git")
        }
    }
}
