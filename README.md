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
    implementation("io.github.abdullahalhakimi:biometric-ktx:0.1.0")
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

## Usage in Jetpack Compose

The library is UI-toolkit-agnostic and has no View/XML dependencies, so it works in a pure Compose
app. There is **one requirement**: `BiometricAuthenticator` needs a `FragmentActivity`, which
androidx.biometric's `BiometricPrompt` requires. The default Compose template's `ComponentActivity`
is *not* a `FragmentActivity`, so extend `AppCompatActivity` (or `FragmentActivity`):

```kotlin
// ✗ will not compile — ComponentActivity is not a FragmentActivity
class MainActivity : ComponentActivity()

// ✓ AppCompatActivity IS a FragmentActivity
class MainActivity : AppCompatActivity() {
    private lateinit var authenticator: BiometricAuthenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authenticator = BiometricAuthenticator(this)   // construct once, after attach()
        setContent { BiometricSampleTheme { LoginFlow(authenticator) } }
    }
}
```

Then drive it from ordinary Compose state. Auto-prompt once on entering the login flow with
`LaunchedEffect`, or trigger it from a click with `rememberCoroutineScope`:

```kotlin
@Composable
fun LoginFlow(authenticator: BiometricAuthenticator) {
    var authState by remember { mutableStateOf<AuthState>(AuthState.NotLoggedIn) }
    val scope = rememberCoroutineScope()

    // Auto-prompt once (biometric-first); LaunchedEffect is lifecycle-aware and won't re-fire on
    // recomposition.
    LaunchedEffect(authState) {
        if (authState is AuthState.NotLoggedIn) {
            when (val result = authenticator.authenticate { title = "Sign in" }) {
                is BiometricResult.Success -> authState = AuthState.LoggedIn(LoginMethod.Biometric)
                else -> { /* fall back to the password form */ }
            }
        }
    }

    when (val state = authState) {
        is AuthState.LoggedIn -> HomeScreen(
            onLogout = { authState = AuthState.NotLoggedIn },
        )
        is AuthState.NotLoggedIn -> LoginScreen(
            onUseBiometric = {
                scope.launch { /* call authenticate() the same way */ }
            },
        )
    }
}
```

The key pattern: `authenticate()` is a normal `suspend` function, so call it from `LaunchedEffect`,
`rememberCoroutineScope().launch { }`, or a `ViewModel`'s `viewModelScope` — and map the sealed
`BiometricResult` into your UI state. See `sample/` for a complete working version of exactly this.

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

Published to Maven Central as **`io.github.abdullahalhakimi:biometric-ktx`** through the
[Central Publisher Portal](https://central.sonatype.com/), driven by the
[`com.vanniktech.maven.publish`](https://github.com/vanniktech/gradle-maven-publish-plugin) plugin.
It owns the release variant + sources jar, generates the required `-javadoc.jar`, signs every
artifact, and releases automatically after Central's validation.

### One-time setup

1. **Namespace** — verified on the Portal as `io.github.abdullahalhakimi`.
2. **Token** — generate one at [central.sonatype.com/usertoken](https://central.sonatype.com/usertoken).
3. **GPG key** — Central requires every artifact to be signed:

   ```bash
   gpg --gen-key                                   # RSA 4096, set a passphrase, use your real email
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>   # publish the PUBLIC key
   gpg --export-secret-keys --armor <KEY_ID>       # prints the PRIVATE key — copy all of it
   ```

### Credentials

No secrets live in the repo. The [Publish workflow](.github/workflows/publish.yml) reads four
**Settings → Secrets and variables → Actions → New repository secret** entries:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Portal token username |
| `MAVEN_CENTRAL_PASSWORD` | Portal token password |
| `SIGNING_KEY` | The full ASCII-armored private key from `gpg --export-secret-keys --armor` |
| `SIGNING_PASSWORD` | The passphrase chosen in `gpg --gen-key` |

### Cutting a release

```bash
git tag v0.1.0
git push origin v0.1.0
```

CI runs the unit tests, then `publishAndReleaseToMavenCentral`. Artifacts typically show up on
Maven Central 10–30 minutes after validation passes. Versions are immutable — a bad release must
be superseded by a new version, never overwritten.

### Local dry run

```bash
./gradlew :biometric:publishToMavenLocal
```

Produces the identical layout (`.aar`, `-sources.jar`, `-javadoc.jar`, `.pom`, `.module`, `.asc`
signatures) under `~/.m2/repository` without contacting Central. Signing is still required, so the
GPG key must be configured first.

## License

[MIT](LICENSE)
