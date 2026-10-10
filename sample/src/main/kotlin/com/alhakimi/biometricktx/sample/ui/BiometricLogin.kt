package com.alhakimi.biometricktx.sample.ui

import com.alhakimi.biometricktx.BiometricAuthenticator
import com.alhakimi.biometricktx.BiometricError
import com.alhakimi.biometricktx.BiometricResult

/**
 * What the login flow should do after a biometric attempt finishes.
 */
sealed interface BiometricOutcome {
    /** The user authenticated; move to the home screen. */
    data object Authenticated : BiometricOutcome

    /**
     * The prompt ended without success (cancel, lockout, no hardware, no enrollment, ...). Show the
     * password form, optionally with a hint explaining why.
     */
    data class ShowPassword(val hint: String?) : BiometricOutcome
}

/**
 * Runs a single biometric authentication and maps the library result onto a [BiometricOutcome].
 *
 * This is a thin, framework-free wrapper so the Compose layer can call it from a coroutine without
 * dealing with the library's sealed types directly.
 */
suspend fun authenticateWithBiometric(authenticator: BiometricAuthenticator): BiometricOutcome {
    val result = authenticator.authenticate {
        title = "Sign in"
        subtitle = "Use your fingerprint or face to continue"
        negativeButtonText = "Use password instead"
    }
    return when (result) {
        is BiometricResult.Success -> BiometricOutcome.Authenticated

        is BiometricResult.Failed ->
            // Recoverable per-attempt failure; the platform prompt is still visible. We treat it the
            // same as a terminal outcome for this demo by letting the prompt continue, but if we
            // reach here the prompt has ended, so fall back with a neutral hint.
            BiometricOutcome.ShowPassword(hint = "Biometric not recognized.")

        is BiometricResult.Error ->
            BiometricOutcome.ShowPassword(hint = hintFor(result))
    }
}

/** Maps a terminal [BiometricResult.Error] to a user-facing hint, or null for a plain fallback. */
private fun hintFor(result: BiometricResult.Error): String? =
    when (result.error) {
        is BiometricError.Lockout,
        is BiometricError.LockoutPermanent,
        -> "Too many attempts. Sign in with your password instead."

        is BiometricError.NoBiometricsEnrolled ->
            "No biometrics enrolled. Use your password instead."

        is BiometricError.HardwareUnavailable ->
            "Biometrics unavailable. Use your password instead."

        // User canceled, timed out, or anything else: fall back silently.
        else -> null
    }
