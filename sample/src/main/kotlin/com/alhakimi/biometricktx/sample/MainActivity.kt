package com.alhakimi.biometricktx.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.alhakimi.biometricktx.Authenticators
import com.alhakimi.biometricktx.BiometricAuthenticator
import com.alhakimi.biometricktx.BiometricAvailability
import com.alhakimi.biometricktx.sample.ui.BiometricOutcome
import com.alhakimi.biometricktx.sample.ui.HomeScreen
import com.alhakimi.biometricktx.sample.ui.LoginScreen
import com.alhakimi.biometricktx.sample.ui.LoginLoading
import com.alhakimi.biometricktx.sample.ui.authenticateWithBiometric
import com.alhakimi.biometricktx.sample.ui.theme.BiometricSampleTheme
import kotlinx.coroutines.launch

/**
 * A biometric-first login demo.
 *
 * On launch the app attempts a biometric sign-in automatically. Success swaps to [HomeScreen]; any
 * terminal biometric outcome falls back to the password form on [LoginScreen]. The password path is
 * a demo stub (accepts any non-blank credentials) — this is not real authentication.
 *
 * The activity must be an [AppCompatActivity] (a FragmentActivity) because androidx.biometric's
 * BiometricPrompt requires one.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var authenticator: BiometricAuthenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authenticator = BiometricAuthenticator(this)

        setContent {
            BiometricSampleTheme {
                LoginFlow(
                    authenticator = authenticator,
                    biometricAvailable = biometricAvailable(),
                )
            }
        }
    }

    /** Whether a biometric prompt could plausibly succeed right now. */
    private fun biometricAvailable(): Boolean =
        authenticator.canAuthenticate(Authenticators.BIOMETRIC_STRONG) ==
            BiometricAvailability.Available
}

/**
 * Owns the login session state and switches between the login flow and the home screen.
 *
 * The biometric prompt fires once automatically when the login flow first composes (biometric-first),
 * and again on demand via the "Use biometric" button.
 */
@Composable
private fun LoginFlow(
    authenticator: BiometricAuthenticator,
    biometricAvailable: Boolean,
) {
    var authState by remember { mutableStateOf<AuthState>(AuthState.NotLoggedIn) }
    var passwordHint by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Local scope so button handlers can launch coroutines that update state.
    val scope = rememberCoroutineScope()

    // Shared handler for a biometric attempt (auto or button-triggered).
    suspend fun runBiometric() {
        busy = true
        when (val outcome = authenticateWithBiometric(authenticator)) {
            is BiometricOutcome.Authenticated ->
                authState = AuthState.LoggedIn(LoginMethod.Biometric)
            is BiometricOutcome.ShowPassword -> {
                passwordHint = outcome.hint
                busy = false
            }
        }
    }

    // Biometric-first: attempt once automatically on entering the login flow. LaunchedEffect keys on
    // the auth state so it re-arms after a logout, and is lifecycle-aware (won't leak or re-fire on
    // recomposition).
    if (authState is AuthState.NotLoggedIn && biometricAvailable) {
        LaunchedEffect(authState) {
            runBiometric()
        }
    }

    when (val state = authState) {
        is AuthState.LoggedIn -> HomeScreen(
            method = state.method,
            onLogout = {
                authState = AuthState.NotLoggedIn
                passwordHint = null
            },
        )

        is AuthState.NotLoggedIn -> {
            if (busy) {
                LoginLoading()
            } else {
                LoginScreen(
                    hint = passwordHint,
                    biometricAvailable = biometricAvailable,
                    onLogin = { _, _ ->
                        // Demo stub: accept any non-blank credentials.
                        authState = AuthState.LoggedIn(LoginMethod.Password)
                    },
                    onUseBiometric = {
                        scope.launch { runBiometric() }
                    },
                )
            }
        }
    }
}
