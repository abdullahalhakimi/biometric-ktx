package com.alhakimi.biometricktx.crypto

/**
 * Thrown when a Keystore key that was bound to biometrics has been invalidated, typically because
 * the user added or removed a biometric enrollment after the key was created.
 *
 * When this is thrown, the key must be re-created (and any data encrypted under the old key is
 * unrecoverable). See [BiometricKeyStore.generateKey].
 */
class KeyInvalidatedException(
    message: String = "The biometric-bound key was invalidated. Re-create it and re-encrypt.",
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
