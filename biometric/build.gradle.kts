plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
}

group = "com.alhakimi.biometricktx"
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
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

// Maven Central / Sonatype publishing configuration.
// Credentials and signing keys are read from environment variables / gradle.properties
// so no secrets live in the repo. See README for the required variables.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.alhakimi.biometricktx"
            artifactId = "biometric-ktx"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("biometric-ktx")
                description.set(
                    "A thin, ergonomic Kotlin wrapper around AndroidX BiometricPrompt " +
                        "with a coroutine-first API and optional Keystore crypto binding."
                )
                url.set("https://github.com/alhakimi/biometric-ktx")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("alhakimi")
                        name.set("Alhakimi")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/alhakimi/biometric-ktx.git")
                    developerConnection.set("scm:git:ssh://github.com/alhakimi/biometric-ktx.git")
                    url.set("https://github.com/alhakimi/biometric-ktx")
                }
            }
        }
    }

    repositories {
        maven {
            name = "sonatype"
            val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            url = uri(
                if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            )
            credentials {
                username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey") as String? ?: System.getenv("SIGNING_KEY")
    val signingPassword =
        findProperty("signingPassword") as String? ?: System.getenv("SIGNING_PASSWORD")
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["release"])
    }
}
