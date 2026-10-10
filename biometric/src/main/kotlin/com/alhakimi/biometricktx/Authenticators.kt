package com.alhakimi.biometricktx

/**
 * Constants describing which authenticators a prompt is allowed to use.
 *
 * These map to the `AUTHENTICATOR_*` bitmask accepted by
 * [androidx.biometric.BiometricPrompt.PromptInfo.Builder.setAllowedAuthenticators]. The Android
 * platform deliberately does not let callers request a specific biometric modality (fingerprint
 * vs face); it lets them request a minimum *strength*. See the README for details.
 */
object Authenticators {
    /** Strong biometrics only (e.g. fingerprint, strong-certified face). */
    const val BIOMETRIC_STRONG: Int =
        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG

    /** Weak biometrics, or anything stronger (includes most face unlock implementations). */
    const val BIOMETRIC_WEAK: Int =
        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** The device credential (PIN / pattern / password) only. */
    const val DEVICE_CREDENTIAL: Int =
        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** Strong biometrics or the device credential. */
    const val BIOMETRIC_STRONG_OR_DEVICE_CREDENTIAL: Int =
        BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /** Weak (or stronger) biometrics or the device credential. */
    const val BIOMETRIC_WEAK_OR_DEVICE_CREDENTIAL: Int =
        BIOMETRIC_WEAK or DEVICE_CREDENTIAL
}
