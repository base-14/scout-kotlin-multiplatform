import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.mavenPublish)
}

// Release pins scout-core to a published Maven version (gradle.properties `scout.coreVersion`) so an
// ios-only release re-publishes nothing but itself. Only active with -Prelease; local builds use
// project(":scout-core") so source changes flow through immediately.
val coreDependency: Any =
    if (providers.gradleProperty("release").isPresent) {
        "io.base14:scout-core:${providers.gradleProperty("scout.coreVersion").get()}"
    } else {
        project(":scout-core")
    }

kotlin {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        optIn.add("io.opentelemetry.kotlin.ExperimentalApi")
        optIn.add("io.opentelemetry.kotlin.semconv.IncubatingApi")
    }

    val xcf = XCFramework("Scout")
    val iosTargets = listOf(iosArm64(), iosSimulatorArm64())
    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "Scout"
            isStatic = false
            export(coreDependency)
            xcf.add(this)
        }
    }

    sourceSets {
        iosMain.dependencies {
            api(coreDependency)
            implementation(libs.ktor.client.darwin)
        }
    }
}

group = "io.base14"

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates("io.base14", "scout-ios", version.toString())
    pom {
        name.set("Scout iOS Engine")
        description.set("Scout RUM — Kotlin/Native iOS engine (consumed by KMP apps; Swift apps use the Scout Swift package).")
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

val generateScoutIosBuildInfo by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/scoutIosBuildInfo/kotlin")
    val ver = project.version.toString()
    inputs.property("version", ver)
    outputs.dir(outDir)
    doLast {
        val f = outDir.get().file("io/base14/scout/ios/ScoutIosBuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText(
            "package io.base14.scout.ios\n\n" +
                "internal object ScoutIosBuildInfo {\n" +
                "    const val IOS_VERSION: String = \"$ver\"\n" +
                "}\n",
        )
    }
}

kotlin.sourceSets.named("iosMain").configure {
    kotlin.srcDir(generateScoutIosBuildInfo)
}
