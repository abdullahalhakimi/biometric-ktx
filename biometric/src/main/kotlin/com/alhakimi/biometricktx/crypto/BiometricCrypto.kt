package com.alhakimi.biometricktx.crypto

import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Result of encrypting data with a biometric-bound key.
 *
 * @property iv The AES-GCM initialization vector. It is NOT secret and must be stored alongside
 *   [ciphertext] so decryption can be performed later.
 * @property ciphertext The encrypted bytes (includes the GCM auth tag).
 */
data class EncryptedData(
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedData) return false
        return iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertext.contentHashCode()
}

/**
 * Runs AES-GCM encrypt/decrypt using an already-authenticated [Cipher] from a
 * [BiometricPrompt.CryptoObject](via [BiometricKeyStore.cryptoObjectFor]).
 *
 * Call these only AFTER the biometric prompt has succeeded; the Keystore enforces that the cipher
 * was initialized through an authorized [BiometricPrompt.CryptoObject].
 */
object BiometricCrypto {

    /** Encrypts [plaintext] with the (already-authenticated) [cipher]. */
    fun encrypt(cipher: Cipher, plaintext: ByteArray): EncryptedData {
        val iv = cipher.iv
            ?: throw IllegalStateException(
                "Cipher has no IV; init it in ENCRYPT_MODE before use.",
            )
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedData(iv = iv, ciphertext = ciphertext)
    }

    /**
     * Decrypts [data] using a cipher re-initialized for decryption under [key].
     *
     * Because GCM decryption needs the original IV, this builds a fresh [Cipher], initializes it
     * with [data.iv], and requires the same biometric-gated key. This is the post-authentication
     * path where the prompt already unlocked key usage for this session.
     *
     * @throws KeyInvalidatedException if the key was invalidated since encryption.
     */
    fun decrypt(
        key: SecretKey,
        data: EncryptedData,
        transformation: String = "AES/GCM/NoPadding",
    ): ByteArray {
        val cipher = Cipher.getInstance(transformation)
        return try {
            cipher.init(Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, data.iv))
            cipher.doFinal(data.ciphertext)
        } catch (e: java.security.InvalidKeyException) {
            throw KeyInvalidatedException(cause = e)
        }
    }
}
