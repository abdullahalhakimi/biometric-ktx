# biometric-ktx

A thin, ergonomic Kotlin wrapper around [AndroidX BiometricPrompt](https://developer.android.com/jetpack/androidx/releases/biometric)
with a coroutine-first API and optional Android Keystore crypto binding.

- **Kotlin-first**: `suspend` functions and `Flow`, no callback pyramids.
- **Exhaustive results**: sealed `BiometricResult` / `BiometricEvent` / `BiometricError` types.
- **One prompt at a time**: a racing second call returns `PromptAlreadyRunning` instead of throwing.
- **Optional crypto binding**: gate Keystore-backed AES-GCM encrypt/decrypt behind a biometric check.
- **Zero app-level boilerplate**: construct with a `FragmentActivity` and go.

## Installation

```kotlin
dependencies {
    implementation("com.alhakimi.biometricktx:biometric-ktx:0.1.0")
}
```

Add the biometric permission to your manifest:

```xml
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
```

## Quick start

```kotlin
val authenticator = BiometricAuthenticator(activity)

val result = authenticator.authenticate {
    title = "Confirm it's you"
    subtitle = "Use your fingerprint"
    negativeButtonText = "Use PIN instead"
    allowedAuthenticators = Authenticators.BIOMETRIC_STRONG
}

when (result) {
    is BiometricResult.Success -> { /* authenticated */ }
    is BiometricResult.Failed  -> { /* recoverable; prompt still visible */ }
    is BiometricResult.Error   -> { /* terminal: canceled, lockout, hardware missing */ }
}
```

## Streaming events

If you want to render prompt lifecycle in your UI, use the Flow variant:

```kotlin
authenticator.authenticateFlow { title = "Confirm" }
    .collect { event ->
        when (event) {
            is BiometricEvent.Started  -> showWaiting()
            is BiometricEvent.Failed   -> showHint(event.error)
            is BiometricEvent.Help     -> showHint(event.error)
            is BiometricEvent.Succeeded -> onAuthenticated()
            is BiometricEvent.Error    -> onError(event.error)
        }
    }
```

## Checking availability first

`canAuthenticate` returns a sealed `BiometricAvailability`, so you can branch exhaustively:

```kotlin
when (authenticator.canAuthenticate(Authenticators.BIOMETRIC_STRONG)) {
    BiometricAvailability.Available -> prompt()
    BiometricAvailability.NoneEnrolled -> promptToEnroll()
    BiometricAvailability.HardwareUnavailable -> retryLater()
    BiometricAvailability.NoHardware -> fallBackToPassword()
    BiometricAvailability.Unsupported -> fallBackToPassword()
    BiometricAvailability.SecurityUpdateRequired -> promptToUpdate()
    BiometricAvailability.Unknown -> fallBackToPassword()
}
```

## Crypto-bound authentication

Bind a prompt to an Android Keystore AES-GCM key so the key can only be used to encrypt/decrypt
after a successful biometric check:

```kotlin
val keyStore = BiometricKeyStore(context)

// Creating a biometric-bound key requires a secure screen lock (PIN / pattern / password).
if (keyStore.isDeviceSecure() && !keyStore.hasKey("auth_key")) {
    keyStore.generateKey("auth_key")
}

val result = authenticator.authenticate {
    title = "Authorize secure operation"
    cryptoObject = keyStore.cryptoObjectFor("auth_key")
    allowedAuthenticators = Authenticators.BIOMETRIC_STRONG
}

if (result is BiometricResult.Success) {
    val cipher = keyStore.cryptoObjectFor("auth_key").cipher!!
    val encrypted = BiometricCrypto.encrypt(cipher, payload)
    // store encrypted.iv + encrypted.ciphertext
}
```

If the user adds or removes a biometric enrollment, the key is invalidated and a
`KeyInvalidatedException` is thrown — catch it, re-create the key, and re-encrypt.

> **Requires a secure screen lock.** The Android Keystore cannot create (or use) keys with
> `setUserAuthenticationRequired(true)` unless a PIN / pattern / password is set. Check
> `keyStore.isDeviceSecure()` before prompting, or be prepared to catch
> `SecureLockScreenRequiredException`. `BiometricKeyStore` takes a `Context` (it stores only the
> `applicationContext`).

## Biometric modalities

Android does **not** let callers request a specific modality (fingerprint vs face). It lets you
request a minimum **strength** via `allowedAuthenticators`:

| Constant | Meaning |
| --- | --- |
| `Authenticators.BIOMETRIC_STRONG` | Strong biometrics only (e.g. fingerprint) |
| `Authenticators.BIOMETRIC_WEAK` | Weak or stronger (most face unlock) |
| `Authenticators.DEVICE_CREDENTIAL` | PIN / pattern / password |
| `Authenticators.BIOMETRIC_STRONG_OR_DEVICE_CREDENTIAL` | Either strong biometric or credential |

The OS picks whichever enrolled biometric satisfies the requested strength.

## Sample app

The `sample/` module is a Jetpack Compose demo of a **biometric-first login**:

- On launch it attempts a biometric sign-in automatically.
- Success swaps to a home screen showing the login method used, with a **Log out** button that
  returns to the login flow.
- Any terminal biometric outcome (cancel, lockout, no hardware, no enrollment) falls back to an
  email/password form, with a hint explaining why.

It exercises `authenticate()`, `canAuthenticate()`, and the error-to-fallback mapping. The password
path is a stub (accepts any non-blank credentials) — it's a UI/auth-flow demo, not real backend
auth.

To try the biometric path you need a device or emulator with an **enrolled fingerprint/face**; the
crypto demo additionally needs a **secure screen lock** (PIN / pattern / password).

```bash
./gradlew :sample:installDebug
```

## Building

Requires a JDK (17+) and the Android SDK. The build uses AGP 9, which compiles Kotlin via its
built-in Kotlin support (no separate `kotlin-android` plugin).

```bash
./gradlew :biometric:assembleRelease       # build the library AAR
./gradlew :biometric:testDebugUnitTest     # run JVM unit tests
./gradlew :sample:assembleDebug            # build the Compose demo APK
./gradlew :sample:installDebug             # install the demo app on a connected device
```

## Publishing

Publishing is configured for Maven Central. Provide credentials and signing keys via environment
variables (`OSSRH_USERNAME`, `OSSRH_PASSWORD`, `SIGNING_KEY`, `SIGNING_PASSWORD`) — nothing is
committed to the repo.

## License

[MIT](LICENSE)
