package com.alhakimi.biometricktx

/**
 * Lifecycle events emitted by [BiometricAuthenticator.authenticateFlow].
 *
 * Unlike the one-shot [BiometricResult], the Flow variant streams intermediate events (e.g.
 * [Started], [Help], recoverable [Failed]) before a terminal [Succeeded] or [Error], which makes
 * it suitable for driving richer UI.
 */
sealed interface BiometricEvent {
    /** The prompt has been shown and is awaiting user input. */
    data object Started : BiometricEvent

    /** Authentication succeeded. Terminal. */
    data object Succeeded : BiometricEvent

    /** A recoverable per-attempt failure occurred. The prompt is still active. */
    data class Failed(val error: BiometricError) : BiometricEvent

    /** The prompt emitted a help/hint message (e.g. "Fingerprint not recognized. Try again."). */
    data class Help(val error: BiometricError) : BiometricEvent

    /** The prompt ended with a terminal error. Terminal. */
    data class Error(val error: BiometricError) : BiometricEvent
}
