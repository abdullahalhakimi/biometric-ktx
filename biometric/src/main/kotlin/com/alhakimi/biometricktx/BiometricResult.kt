package com.alhakimi.biometricktx

/**
 * The terminal outcome of a one-shot [BiometricAuthenticator.authenticate] call.
 *
 * - [Success] means the biometric was recognized (and, if a CryptoObject was supplied, the
 *   operation was authorized).
 * - [Failed] means the prompt was dismissed with a recoverable per-attempt failure (e.g. the user
 *   tapped "Try again", or a wrong fingerprint was read). Note that the prompt may still be
 *   visible; this is the reason for the failure, surfaced for UI feedback.
 * - [Error] means the prompt ended and cannot continue (cancellation, lockout, hardware missing).
 */
sealed interface BiometricResult {
    /** Authentication succeeded. */
    data object Success : BiometricResult

    /** The current attempt failed but the prompt can continue. */
    data class Failed(val error: BiometricError) : BiometricResult

    /** The prompt ended with a terminal error. */
    data class Error(val error: BiometricError) : BiometricResult
}
