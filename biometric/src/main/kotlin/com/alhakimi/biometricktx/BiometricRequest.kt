package com.alhakimi.biometricktx

import androidx.biometric.BiometricPrompt

/**
 * Immutable description of a single authentication attempt.
 *
 * Build one with the DSL:
 *
 * ```kotlin
 * val request = BiometricRequest {
 *     title = "Confirm it's you"
 *     subtitle = "Use your fingerprint"
 *     negativeButtonText = "Use PIN instead"
 *     allowedAuthenticators = Authenticators.BIOMETRIC_STRONG
 * }
 * ```
 */
class BiometricRequest internal constructor(
    val title: CharSequence,
    val subtitle: CharSequence?,
    val description: CharSequence?,
    val negativeButtonText: CharSequence,
    val allowedAuthenticators: Int,
    val confirmationRequired: Boolean,
    val cryptoObject: BiometricPrompt.CryptoObject?,
) {
    /** Builder used by the DSL. */
    class Builder {
        /** Required prompt title. */
        var title: CharSequence = ""

        /** Optional secondary line. */
        var subtitle: CharSequence? = null

        /** Optional description paragraph. */
        var description: CharSequence? = null

        /**
         * Text for the negative button. Ignored on API 30+ when the request allows only a device
         * credential (the platform supplies its own cancel affordance in that case).
         */
        var negativeButtonText: CharSequence = "Cancel"

        /**
         * Which authenticators are acceptable. Defaults to strong biometrics.
         * See [Authenticators].
         */
        var allowedAuthenticators: Int = Authenticators.BIOMETRIC_STRONG

        /**
         * Whether user confirmation (e.g. a button press) is required after a successful biometric
         * match. Useful when the auth gates a sensitive transaction. Defaults to `true` to match
         * platform behavior for strong biometrics.
         */
        var confirmationRequired: Boolean = true

        /** Optional crypto object, binding the prompt to a Keystore operation. */
        var cryptoObject: BiometricPrompt.CryptoObject? = null

        /** Applies configuration to an existing [BiometricRequest], for reuse/defaults. */
        fun build(): BiometricRequest {
            require(title.isNotEmpty()) { "BiometricRequest.title must not be empty." }
            return BiometricRequest(
                title = title,
                subtitle = subtitle,
                description = description,
                negativeButtonText = negativeButtonText,
                allowedAuthenticators = allowedAuthenticators,
                confirmationRequired = confirmationRequired,
                cryptoObject = cryptoObject,
            )
        }
    }

    /** Returns a copy of this request with the given [block] applied. */
    inline fun copy(block: Builder.() -> Unit): BiometricRequest =
        Builder().apply {
            title = this@BiometricRequest.title
            subtitle = this@BiometricRequest.subtitle
            description = this@BiometricRequest.description
            negativeButtonText = this@BiometricRequest.negativeButtonText
            allowedAuthenticators = this@BiometricRequest.allowedAuthenticators
            confirmationRequired = this@BiometricRequest.confirmationRequired
            cryptoObject = this@BiometricRequest.cryptoObject
        }.apply(block).build()

    companion object {
        /** Entry point for the builder DSL. */
        inline operator fun invoke(block: Builder.() -> Unit): BiometricRequest =
            Builder().apply(block).build()
    }
}
