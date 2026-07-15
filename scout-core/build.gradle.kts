plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.mavenPublish)
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
        minSdk = 26
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

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    // Sign only when a key is configured (CI), so local publishToMavenLocal still works.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates("io.base14", "scout-core", version.toString())
    pom {
        name.set("Scout Core")
        description.set("Scout RUM — shared Kotlin Multiplatform engine (OpenTelemetry-based).")
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

val generateScoutBuildInfo by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/scoutBuildInfo/kotlin")
    val ver = project.version.toString()
    inputs.property("version", ver)
    outputs.dir(outDir)
    doLast {
        val f = outDir.get().file("io/base14/scout/core/ScoutBuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText(
            "package io.base14.scout.core\n\n" +
                "internal object ScoutBuildInfo {\n" +
                "    const val CORE_VERSION: String = \"$ver\"\n" +
                "}\n",
        )
    }
}

kotlin.sourceSets.named("commonMain").configure {
    kotlin.srcDir(generateScoutBuildInfo)
}
