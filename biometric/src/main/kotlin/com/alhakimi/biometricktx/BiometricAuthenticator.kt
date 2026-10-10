package com.alhakimi.biometricktx

import androidx.annotation.MainThread
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.alhakimi.biometricktx.internal.BiometricPromptLauncher
import com.alhakimi.biometricktx.internal.PromptController
import kotlinx.coroutines.flow.Flow

/**
 * Entry point for the library. Wraps [androidx.biometric.BiometricPrompt] behind a coroutine-first
 * API.
 *
 * Construct on the main thread with the [FragmentActivity] that will host the prompt:
 *
 * ```kotlin
 * val authenticator = BiometricAuthenticator(activity)
 *
 * // one-shot
 * when (val result = authenticator.authenticate { title = "Confirm it's you" }) {
 *     is BiometricResult.Success -> ...
 *     is BiometricResult.Failed  -> ...
 *     is BiometricResult.Error   -> ...
 * }
 * ```
 *
 * Only one prompt may be active at a time. A racing second call returns
 * [BiometricResult.Error] with [BiometricError.PromptAlreadyRunning] rather than throwing.
 */
class BiometricAuthenticator @MainThread constructor(
    private val activity: FragmentActivity,
) {
    private val controller = PromptController(
        BiometricPromptLauncher(activity, ContextCompat.getMainExecutor(activity)),
    )

    /**
     * Checks whether the given [authenticators] can currently satisfy a prompt.
     *
     * @param authenticators one of the [Authenticators] constants (bitmask).
     */
    @MainThread
    fun canAuthenticate(authenticators: Int = Authenticators.BIOMETRIC_STRONG): BiometricAvailability {
        val manager = BiometricManager.from(activity)
        return BiometricAvailability.from(manager.canAuthenticate(authenticators))
    }

    /**
     * Shows a biometric prompt and suspends until it reaches a terminal state.
     *
     * Safe to call from any dispatcher; platform callbacks are bridged internally. Cancelling the
     * calling coroutine dismisses the prompt.
     */
    suspend fun authenticate(request: BiometricRequest = BiometricRequest { }): BiometricResult =
        controller.authenticate(request)

    /** DSL convenience overload. */
    suspend fun authenticate(block: BiometricRequest.Builder.() -> Unit): BiometricResult =
        controller.authenticate(BiometricRequest(block))

    /**
     * Streams [BiometricEvent]s for a prompt until a terminal event ([BiometricEvent.Succeeded] or
     * [BiometricEvent.Error]), then completes.
     */
    fun authenticateFlow(request: BiometricRequest): Flow<BiometricEvent> =
        controller.authenticateFlow(request)

    /** DSL convenience overload for the Flow variant. */
    fun authenticateFlow(block: BiometricRequest.Builder.() -> Unit): Flow<BiometricEvent> =
        controller.authenticateFlow(BiometricRequest(block))
}
