package com.alhakimi.biometricktx.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Manages Android Keystore keys that are gated behind biometric authentication.
 *
 * A key created here can only be used to sign/encrypt once the user passes a biometric (or
 * device-credential) check, which is enforced by the Keystore itself. Use [cryptoObjectFor] to get
 * a [BiometricPrompt.CryptoObject] to pass into a prompt, then perform the operation after
 * authentication succeeds.
 *
 * ```kotlin
 * val keyStore = BiometricKeyStore()
 * keyStore.generateKey("auth_key")
 *
 * val result = authenticator.authenticate {
 *     title = "Confirm it's you"
 *     cryptoObject = keyStore.cryptoObjectFor("auth_key")
 * }
 * if (result is BiometricResult.Success) {
 *     val signature = keyStore.signWithKey("auth_key", payload)
 * }
 * ```
 */
class BiometricKeyStore {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Generates (or regenerates) a biometric-gated AES key under [alias].
     *
     * @param alias Keystore alias for the key. Any existing key with this alias is replaced.
     * @param requireBiometricPerUse When `true` (default), every use of the key requires a fresh
     *   biometric check. When `false`, the key is usable for a short time window after a single
     *   check (validity is bounded by the platform to at most 30s for strong biometrics).
     * @param invalidatedByBiometricEnrollment When `true` (default), adding or removing a biometric
     *   enrollment invalidates the key (surfacing as [KeyInvalidatedException]).
     */
    fun generateKey(
        alias: String,
        requireBiometricPerUse: Boolean = true,
        invalidatedByBiometricEnrollment: Boolean = true,
    ): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(invalidatedByBiometricEnrollment)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: per-use auth via positive-authentication-requirement duration of 0.
            val authParameters = if (requireBiometricPerUse) {
                0 // biometric required for every use
            } else {
                AUTH_VALIDITY_SECONDS
            }
            builder.setUserAuthenticationParameters(
                authParameters,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            // Pre-30: validity duration in seconds. 0 means "auth required for every use".
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(
                if (requireBiometricPerUse) 0 else AUTH_VALIDITY_SECONDS,
            )
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Returns the [SecretKey] for [alias], or `null` if none exists.
     */
    fun getKey(alias: String): SecretKey? {
        val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey
    }

    /** Whether a key exists under [alias]. */
    fun hasKey(alias: String): Boolean = keyStore.containsAlias(alias)

    /** Deletes the key under [alias], if present. */
    fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    /**
     * Returns a [BiometricPrompt.CryptoObject] wrapping an initialized GCM [javax.crypto.Cipher]
     * for the key under [alias], suitable for passing into a prompt.
     *
     * @throws KeyInvalidatedException if the key existed but has been invalidated.
     * @throws IllegalStateException if no key exists under [alias] (call [generateKey] first).
     */
    fun cryptoObjectFor(alias: String): BiometricPrompt.CryptoObject {
        val key = getKey(alias)
            ?: throw IllegalStateException("No key under alias '$alias'. Call generateKey() first.")
        val cipher = javax.crypto.Cipher.getInstance(GCM_TRANSFORMATION)
        return try {
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
            BiometricPrompt.CryptoObject(cipher)
        } catch (e: java.security.UnrecoverableKeyException) {
            throw KeyInvalidatedException(cause = e)
        } catch (e: java.security.InvalidKeyException) {
            throw KeyInvalidatedException(cause = e)
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AUTH_VALIDITY_SECONDS = 30
    }
}
