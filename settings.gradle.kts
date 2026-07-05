pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "scout-kotlin-multiplatform"

include(":scout-core")
include(":scout-android")
include(":scout-kmp")

// scout-ios only configures when the Kotlin/Native toolchain is requested, so the default
// Android build never needs it. Enable with: -Pscout.enableIos=true
if (providers.gradleProperty("scout.enableIos").orNull == "true") {
    include(":scout-ios")
}
