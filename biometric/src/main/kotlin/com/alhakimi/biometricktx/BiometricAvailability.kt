package com.alhakimi.biometricktx

/**
 * The result of checking whether a given set of authenticators can currently satisfy a prompt.
 *
 * Wraps [androidx.biometric.BiometricManager.canAuthenticate] into a sealed type so callers can
 * branch exhaustively instead of comparing integer codes.
 */
sealed interface BiometricAvailability {
    /** At least one of the requested authenticators can be used right now. */
    data object Available : BiometricAvailability

    /** Nothing satisfying the request is enrolled (user must add a fingerprint/face/credential). */
    data object NoneEnrolled : BiometricAvailability

    /** Biometric hardware is present but currently unavailable (e.g. disabled, or in use). */
    data object HardwareUnavailable : BiometricAvailability

    /** The device has no biometric hardware at all. */
    data object NoHardware : BiometricAvailability

    /** The request is unsupported on this device (e.g. an invalid authenticator combination). */
    data object Unsupported : BiometricAvailability

    /** A security update is required before biometrics can be used. */
    data object SecurityUpdateRequired : BiometricAvailability

    /** The status could not be determined. */
    data object Unknown : BiometricAvailability

    companion object {
        /** Maps a raw [androidx.biometric.BiometricManager] canAuthenticate code. */
        internal fun from(code: Int): BiometricAvailability = when (code) {
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> Available
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> NoneEnrolled
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> HardwareUnavailable
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> NoHardware
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> Unsupported
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                SecurityUpdateRequired
            else -> Unknown
        }
    }
}
