import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

group = "io.github.abdullahalhakimi"
version = "0.1.0"

android {
    namespace = "com.alhakimi.biometricktx"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// AGP 9 built-in Kotlin: configure the compiler via the top-level `kotlin` extension.
// jvmTarget defaults to compileOptions.targetCompatibility (17), so it is not repeated here.
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

dependencies {
    api(libs.androidx.annotation)
    // androidx.biometric types (e.g. BiometricPrompt.CryptoObject) appear in the public API
    // (BiometricRequest.cryptoObject, BiometricKeyStore.cryptoObjectFor), so this must be `api`
    // for consumers to compile against those signatures.
    api(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

// Maven Central publishing, targeting the Central Publisher Portal (central.sonatype.com).
//
// The com.vanniktech.maven.publish plugin replaces the old hand-rolled maven-publish/signing setup:
// it wires the release variant + sources jar, generates the -javadoc.jar, applies GPG signing, and
// uploads to the Portal. Credentials and keys are read from Gradle properties, which CI supplies as
// ORG_GRADLE_PROJECT_* environment variables — no secrets live in this repo.
mavenPublishing {
    // Publish only the `release` variant, with a real sources jar and an empty -javadoc.jar.
    // Central requires a -javadoc.jar to exist; for a Kotlin-only module Dokka would be the
    // alternative, but an empty jar satisfies validation and avoids the Dokka V1 deprecation.
    configure(
        AndroidSingleVariantLibrary(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        ),
    )

    // groupId comes from `group` above, version from `version` above.
    coordinates(artifactId = "biometric-ktx")

    // Upload to the Central Portal and release automatically once validation passes, so a tag
    // push needs no manual "Publish" click on the website.
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    pom {
        name.set("biometric-ktx")
        description.set(
            "A thin, ergonomic Kotlin wrapper around AndroidX BiometricPrompt " +
                "with a coroutine-first API and optional Keystore crypto binding.",
        )
        url.set("https://github.com/abdullahalhakimi/biometric-ktx")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("abdullahalhakimi")
                name.set("Abdullah Alhakimi")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/abdullahalhakimi/biometric-ktx.git")
            developerConnection.set("scm:git:ssh://github.com/abdullahalhakimi/biometric-ktx.git")
            url.set("https://github.com/abdullahalhakimi/biometric-ktx")
        }
    }
}
