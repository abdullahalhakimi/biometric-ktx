package com.alhakimi.biometricktx

import com.alhakimi.biometricktx.internal.PromptCallback
import com.alhakimi.biometricktx.internal.PromptLauncher

/**
 * A fake [PromptLauncher] that records calls and lets tests drive the [PromptCallback] manually.
 *
 * Pure Kotlin: no androidx / Android types, so it runs on the plain JVM.
 */
internal class FakePromptLauncher : PromptLauncher {
    var lastRequest: BiometricRequest? = null
        private set
    var lastCallback: PromptCallback? = null
        private set
    var authenticateCount: Int = 0
        private set
    var cancelCount: Int = 0
        private set

    /** When true, authenticate() immediately delivers the configured outcome. */
    var autoOutcome: Outcome? = null

    sealed interface Outcome {
        data object Success : Outcome
        data class Error(val code: Int, val message: String) : Outcome
        data object Failed : Outcome
    }

    override fun authenticate(request: BiometricRequest, callback: PromptCallback) {
        authenticateCount++
        lastRequest = request
        lastCallback = callback
        when (val outcome = autoOutcome) {
            is Outcome.Success -> callback.onSuccess()
            is Outcome.Error -> callback.onError(outcome.code, outcome.message)
            is Outcome.Failed -> callback.onFailed()
            null -> { /* wait for the test to drive it */ }
        }
    }

    override fun cancel() {
        cancelCount++
    }

    fun emitSuccess() = lastCallback!!.onSuccess()
    fun emitError(code: Int, message: String) = lastCallback!!.onError(code, message)
    fun emitFailed() = lastCallback!!.onFailed()
}
