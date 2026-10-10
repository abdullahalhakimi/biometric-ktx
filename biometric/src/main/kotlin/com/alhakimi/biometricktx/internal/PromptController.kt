package com.alhakimi.biometricktx.internal

import com.alhakimi.biometricktx.BiometricError
import com.alhakimi.biometricktx.BiometricEvent
import com.alhakimi.biometricktx.BiometricRequest
import com.alhakimi.biometricktx.BiometricResult
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bridges [PromptLauncher]'s callback API into suspending / Flow surfaces.
 *
 * Responsibilities:
 *  - Enforce one prompt at a time via [mutex]; a racing call surfaces
 *    [BiometricError.PromptAlreadyRunning] instead of throwing.
 *  - Convert platform callbacks into [BiometricResult] (one-shot) or [BiometricEvent] (Flow).
 *  - Handle cancellation: if the caller cancels the coroutine, dismiss the prompt.
 *
 * This class depends only on the pure-Kotlin [PromptLauncher]/[PromptCallback] seam, so it is unit
 * testable on the plain JVM.
 */
internal class PromptController(
    private val launcher: PromptLauncher,
) {
    private val mutex = Mutex()

    /** One-shot authenticate. Returns after the prompt reaches a terminal state. */
    suspend fun authenticate(request: BiometricRequest): BiometricResult {
        if (mutex.isLocked) {
            return BiometricResult.Error(BiometricError.PromptAlreadyRunning)
        }
        return mutex.withLock {
            suspendCancellableCoroutine { continuation ->
                val callback = object : PromptCallback {
                    override fun onSuccess() {
                        if (continuation.isActive) {
                            continuation.resume(BiometricResult.Success)
                        }
                    }

                    override fun onError(code: Int, message: String?) {
                        if (continuation.isActive) {
                            continuation.resume(
                                BiometricResult.Error(BiometricError.from(code, message)),
                            )
                        }
                    }

                    override fun onFailed() {
                        // Recoverable per-attempt failure. The prompt stays active and the user can
                        // retry; the terminal outcome arrives via onSuccess/onError. We do nothing
                        // here for the one-shot API.
                    }
                }

                launcher.authenticate(request, callback)

                continuation.invokeOnCancellation {
                    launcher.cancel()
                }
            }
        }
    }

    /** Streaming authenticate: emits lifecycle events until a terminal event, then completes. */
    fun authenticateFlow(request: BiometricRequest): Flow<BiometricEvent> = channelFlow {
        if (mutex.isLocked) {
            send(BiometricEvent.Error(BiometricError.PromptAlreadyRunning))
            return@channelFlow
        }
        mutex.withLock {
            val callback = object : PromptCallback {
                override fun onSuccess() {
                    trySend(BiometricEvent.Succeeded)
                    close()
                }

                override fun onError(code: Int, message: String?) {
                    trySend(BiometricEvent.Error(BiometricError.from(code, message)))
                    close()
                }

                override fun onFailed() {
                    trySend(
                        BiometricEvent.Failed(
                            BiometricError.UserFailed(
                                code = ERROR_USER_FAILED_CODE,
                                message = "Biometric not recognized. Try again.",
                            ),
                        ),
                    )
                }
            }
            launcher.authenticate(request, callback)
            send(BiometricEvent.Started)
            awaitClose { launcher.cancel() }
        }
    }

    private companion object {
        /**
         * Mirrors androidx.biometric.BiometricPrompt.ERROR_USER_FAILED. Inlined as a plain Int so
         * this controller stays free of androidx types and JVM-unit-testable.
         */
        const val ERROR_USER_FAILED_CODE = 1003
    }
}
