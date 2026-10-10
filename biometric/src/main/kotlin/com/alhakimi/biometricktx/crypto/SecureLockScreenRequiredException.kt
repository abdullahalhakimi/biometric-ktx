package com.alhakimi.biometricktx.crypto

/**
 * Thrown when the device has no secure screen lock (PIN / pattern / password) and therefore cannot
 * create or use keys that require user authentication.
 *
 * The Android Keystore refuses to generate a key with `setUserAuthenticationRequired(true)` unless a
 * secure lock screen is set. Check [BiometricKeyStore.isDeviceSecure] before calling
 * [BiometricKeyStore.generateKey] to handle this up front.
 */
class SecureLockScreenRequiredException(
    message: String = "A secure screen lock (PIN, pattern, or password) must be set before " +
        "creating biometric-bound keys.",
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
