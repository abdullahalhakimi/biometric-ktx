package com.alhakimi.biometricktx

/**
 * A biometric/prompt error, mapped from the raw AndroidX [androidx.biometric.BiometricPrompt]
 * error codes into a sealed, exhaustive hierarchy.
 *
 * Every case carries the original [code] and a human-readable [message] so callers that need the
 * platform detail can always reach it, while still being able to `when` exhaustively over the
 * known cases.
 */
sealed class BiometricError(
    /** The raw error code reported by the platform. */
    open val code: Int,
    /** A human-readable message supplied by the platform. */
    open val message: String,
) {
    /** The biometric sensor is not available (e.g. hardware missing or disabled). */
    data class HardwareUnavailable(override val code: Int, override val message: String) :
        BiometricError(code, message)

    /** There are no biometrics enrolled on the device. */
    data class NoBiometricsEnrolled(override val code: Int, override val message: String) :
        BiometricError(code, message)

    /** The sensor is too dirty/unreliable to process right now; user can retry. */
    data class UnableToProcess(override val code: Int, override val message: String) :
        BiometricError(code, message)

    /** The user cancelled the prompt (tapped cancel / swiped away). */
    data class UserCanceled(override val code: Int, override val message: String) :
        BiometricError(code, message)

    /** The user tried but failed to authenticate (e.g. unrecognized fingerprint). */
    data class UserFailed(override val code: Int, override val message: String) :
        BiometricError(code, message)

    /** The user was locked out after too many failed attempts (retry later). */
    data class Lockout(override val code: Int, override val message: String) :
        BiometricError(code, message)

    /** The user is permanently locked out; a credential is required. */
    data class LockoutPermanent(override val code: Int, override val message: String) :
        BiometricError(code, message)

    /** The vendor produced an error specific to their implementation. */
    data class Vendor(override val code: Int, override val message: String) :
        BiometricError(code, message)

    /** No device credential (PIN/pattern/password) is set on the device. */
    data class NoDeviceCredential(override val code: Int, override val message: String) :
        BiometricError(code, message)

    /** The prompt timed out. */
    data class Timeout(override val code: Int, override val message: String) :
        BiometricError(code, message)

    /** The requested operation was canceled by the system/app. */
    data class Canceled(override val code: Int, override val message: String) :
        BiometricError(code, message)

    /**
     * A prompt is already being shown by this authenticator. Emitted instead of throwing when a
     * second [BiometricAuthenticator.authenticate] call races an in-flight prompt.
     */
    data object PromptAlreadyRunning : BiometricError(
        CODE_PROMPT_ALREADY_RUNNING,
        "A biometric prompt is already running.",
    )

    /** An unrecognized error code that this library version does not map to a specific case. */
    data class Unknown(override val code: Int, override val message: String) :
        BiometricError(code, message)

    companion object {
        /**
         * Internal sentinel code used for [PromptAlreadyRunning]. It is outside the range of the
         * platform's error codes so it can never collide with a real one.
         */
        internal const val CODE_PROMPT_ALREADY_RUNNING = -1000

        /** Maps a raw AndroidX error code + message into a [BiometricError]. */
        fun from(code: Int, message: String?): BiometricError {
            val msg = message.orEmpty()
            return when (code) {
                androidx.biometric.BiometricPrompt.ERROR_HW_UNAVAILABLE ->
                    HardwareUnavailable(code, msg)
                androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS ->
                    NoBiometricsEnrolled(code, msg)
                androidx.biometric.BiometricPrompt.ERROR_UNABLE_TO_PROCESS ->
                    UnableToProcess(code, msg)
                androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED ->
                    UserCanceled(code, msg)
                androidx.biometric.BiometricPrompt.ERROR_LOCKOUT ->
                    Lockout(code, msg)
                androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                    LockoutPermanent(code, msg)
                androidx.biometric.BiometricPrompt.ERROR_VENDOR ->
                    Vendor(code, msg)
                androidx.biometric.BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                    NoDeviceCredential(code, msg)
                androidx.biometric.BiometricPrompt.ERROR_TIMEOUT ->
                    Timeout(code, msg)
                androidx.biometric.BiometricPrompt.ERROR_CANCELED ->
                    Canceled(code, msg)
                else -> Unknown(code, msg)
            }
        }
    }
}
