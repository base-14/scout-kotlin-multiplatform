import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    `maven-publish`
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
            isStatic = true
            export(project(":scout-core"))
            xcf.add(this)
        }
    }

    sourceSets {
        iosMain.dependencies {
            api(project(":scout-core"))
            implementation(libs.ktor.client.darwin)
        }
    }
}

group = "io.base14"
version = "0.1.19"
