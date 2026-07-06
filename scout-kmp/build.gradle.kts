plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.mavenPublish)
}

// iOS targets gated so the default Android build never needs the Kotlin/Native toolchain.
val enableIos = providers.gradleProperty("scout.enableIos").orNull == "true"

// Release pins the sibling modules to published Maven versions (gradle.properties) so a kmp release
// publishes nothing but scout-kmp itself. Only active with -Prelease; local builds use project(...)
// so source changes flow through immediately.
val releaseMode = providers.gradleProperty("release").isPresent
val coreDependency: Any =
    if (releaseMode) "io.base14:scout-core:${providers.gradleProperty("scout.coreVersion").get()}" else project(":scout-core")
val androidDependency: Any =
    if (releaseMode) "io.base14:scout-android:${providers.gradleProperty("scout.androidVersion").get()}" else project(":scout-android")

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
        minSdk = 26
    }

    if (enableIos) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(coreDependency)
        }
        androidMain.dependencies {
            api(androidDependency)
        }
        if (enableIos) {
            val iosDependency: Any =
                if (releaseMode) "io.base14:scout-ios:${providers.gradleProperty("scout.iosVersion").get()}" else project(":scout-ios")
            val iosMain = maybeCreate("iosMain")
            iosMain.dependsOn(getByName("commonMain"))
            getByName("iosArm64Main").dependsOn(iosMain)
            getByName("iosSimulatorArm64Main").dependsOn(iosMain)
            iosMain.dependencies {
                api(iosDependency)
            }
        }
    }
}

group = "io.base14"

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates("io.base14", "scout-kmp", version.toString())
    pom {
        name.set("Scout KMP")
        description.set("Scout RUM — unified Kotlin Multiplatform entry point (Android + iOS).")
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
