plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
}

allprojects {
    group = "io.base14"
    // Release version comes from the git tag with its SDK prefix stripped (e.g. android-0.1.2 ->
    // 0.1.2), passed by CI as -PreleaseVersion=<version>. Falls back to a default for local builds.
    version = (findProperty("releaseVersion") as String?)?.takeIf { it.isNotBlank() } ?: "0.1.0"
}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    val ktlintRules = mapOf(
        // Keep auto-fixable formatting (indent/imports/whitespace/trailing-comma) enforced; disable
        // rules that aren't auto-fixable or clash with our conventions (Compose PascalCase, long
        // lines, deliberate KDoc blocks) so `make fmt` fully satisfies `make ci`.
        "ktlint_standard_max-line-length" to "disabled",
        "ktlint_standard_function-naming" to "disabled",
        "ktlint_standard_property-naming" to "disabled",
        "ktlint_standard_backing-property-naming" to "disabled",
        "ktlint_standard_class-naming" to "disabled",
        "ktlint_standard_filename" to "disabled",
        "ktlint_standard_no-wildcard-imports" to "disabled",
        "ktlint_standard_no-consecutive-comments" to "disabled",
        "ktlint_standard_discouraged-comment-location" to "disabled",
    )
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**")
            ktlint("1.2.1").editorConfigOverride(ktlintRules)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint("1.2.1").editorConfigOverride(ktlintRules)
        }
    }
}
