package com.alhakimi.biometricktx.internal

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.alhakimi.biometricktx.BiometricRequest
import java.util.concurrent.Executor

/**
 * Pure-Kotlin callback seam. Keeping this free of androidx types means [PromptController] (the
 * coroutine bridge) is unit-testable on the plain JVM with no Android framework present.
 */
internal interface PromptCallback {
    fun onSuccess()
    fun onError(code: Int, message: String?)
    fun onFailed()
}

/**
 * Seam over [BiometricPrompt] so [PromptController] can be unit tested with a fake, without a real
 * FragmentActivity or sensor.
 *
 * This is internal to the library; consumers never implement it.
 */
internal interface PromptLauncher {
    /** Shows the prompt for [request], delivering events to [callback]. */
    fun authenticate(request: BiometricRequest, callback: PromptCallback)

    /** Cancels the in-flight prompt, if any. Safe to call when nothing is showing. */
    fun cancel()
}

/**
 * The real launcher backed by [BiometricPrompt], bound to a [FragmentActivity].
 *
 * This is the only place androidx prompt types are constructed. A fresh [BiometricPrompt] is
 * created per [authenticate] so the supplied callback receives that session's events; the active
 * prompt is retained so [cancel] can dismiss it.
 */
internal class BiometricPromptLauncher(
    private val activity: FragmentActivity,
    private val executor: Executor,
) : PromptLauncher {

    private var activePrompt: BiometricPrompt? = null

    override fun authenticate(request: BiometricRequest, callback: PromptCallback) {
        val biometricCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                callback.onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                callback.onError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                callback.onFailed()
            }
        }

        val prompt = BiometricPrompt(activity, executor, biometricCallback)
        activePrompt = prompt

        val promptInfo = buildPromptInfo(request)
        val cryptoObject = request.cryptoObject
        if (cryptoObject != null) {
            prompt.authenticate(promptInfo, cryptoObject)
        } else {
            prompt.authenticate(promptInfo)
        }
    }

    override fun cancel() {
        activePrompt?.cancelAuthentication()
        activePrompt = null
    }

    private fun buildPromptInfo(request: BiometricRequest): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(request.title)
            .setAllowedAuthenticators(request.allowedAuthenticators)
            .setConfirmationRequired(request.confirmationRequired)

        request.subtitle?.let { builder.setSubtitle(it) }
        request.description?.let { builder.setDescription(it) }

        // setNegativeButtonText is only valid when the request does NOT include a device
        // credential. Those requests get the platform's own cancel affordance, and setting a
        // negative button throws.
        val includesCredential =
            request.allowedAuthenticators and
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL != 0
        if (!includesCredential) {
            builder.setNegativeButtonText(request.negativeButtonText)
        }

        return builder.build()
    }
}
