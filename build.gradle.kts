// Top-level build file. Plugin versions are declared in gradle/libs.versions.toml.
//
// Note: AGP 9 has built-in Kotlin support, so the org.jetbrains.kotlin.android plugin is NOT
// applied. Kotlin sources compile via AGP's built-in Kotlin integration.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.maven.publish) apply false
}
