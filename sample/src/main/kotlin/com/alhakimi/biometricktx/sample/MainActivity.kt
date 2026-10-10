package com.alhakimi.biometricktx.sample

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.alhakimi.biometricktx.Authenticators
import com.alhakimi.biometricktx.BiometricAuthenticator
import com.alhakimi.biometricktx.BiometricResult
import com.alhakimi.biometricktx.crypto.BiometricCrypto
import com.alhakimi.biometricktx.crypto.BiometricKeyStore
import com.alhakimi.biometricktx.crypto.EncryptedData
import com.alhakimi.biometricktx.crypto.KeyInvalidatedException
import kotlinx.coroutines.launch

/**
 * Minimal demo exercising the library:
 *  - plain strong-biometric auth
 *  - biometric-or-credential auth
 *  - crypto-bound auth (encrypt then decrypt with a biometric-gated Keystore key)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var authenticator: BiometricAuthenticator
    private lateinit var resultView: TextView
    private val keyStore = BiometricKeyStore()

    // Holds the last ciphertext so the demo can decrypt it on a follow-up auth.
    private var lastEncrypted: EncryptedData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authenticator = BiometricAuthenticator(this)
        resultView = TextView(this).apply {
            text = getString(R.string.result_placeholder)
            setPadding(32, 32, 32, 32)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(button(getString(R.string.auth_plain)) { runPlainAuth() })
            addView(button(getString(R.string.auth_credential_fallback)) { runCredentialAuth() })
            addView(button(getString(R.string.auth_crypto)) { runCryptoAuth() })
            addView(resultView)
        }
        setContentView(root)
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun runPlainAuth() {
        lifecycleScope.launch {
            val result = authenticator.authenticate {
                title = "Confirm it's you"
                subtitle = "Use your fingerprint or face"
                negativeButtonText = "Use PIN instead"
                allowedAuthenticators = Authenticators.BIOMETRIC_STRONG
            }
            showResult("Plain auth", result)
        }
    }

    private fun runCredentialAuth() {
        lifecycleScope.launch {
            val result = authenticator.authenticate {
                title = "Confirm it's you"
                allowedAuthenticators = Authenticators.BIOMETRIC_STRONG_OR_DEVICE_CREDENTIAL
            }
            showResult("Biometric or PIN", result)
        }
    }

    private fun runCryptoAuth() {
        lifecycleScope.launch {
            val alias = "sample_auth_key"
            if (!keyStore.hasKey(alias)) {
                keyStore.generateKey(alias)
            }

            val cryptoObject = try {
                keyStore.cryptoObjectFor(alias)
            } catch (e: KeyInvalidatedException) {
                // Key was invalidated (enrollment changed): recreate and retry once.
                keyStore.deleteKey(alias)
                keyStore.generateKey(alias)
                keyStore.cryptoObjectFor(alias)
            }

            val result = authenticator.authenticate {
                title = "Authorize secure operation"
                subtitle = "Biometric required to use the stored key"
                allowedAuthenticators = Authenticators.BIOMETRIC_STRONG
                this.cryptoObject = cryptoObject
            }

            if (result is BiometricResult.Success) {
                val cipher = cryptoObject.cipher
                if (cipher != null) {
                    val payload = "secret-payload".toByteArray()
                    val encrypted = BiometricCrypto.encrypt(cipher, payload)
                    lastEncrypted = encrypted
                    val decrypted = BiometricCrypto.decrypt(keyStore.getKey(alias)!!, encrypted)
                    resultView.text =
                        "Crypto auth: SUCCESS\nDecrypted = \"${String(decrypted)}\""
                    return@launch
                }
            }
            showResult("Crypto auth", result)
        }
    }

    private fun showResult(label: String, result: BiometricResult) {
        val text = when (result) {
            is BiometricResult.Success -> "$label: SUCCESS"
            is BiometricResult.Failed -> "$label: FAILED (${result.error::class.simpleName})"
            is BiometricResult.Error -> "$label: ERROR (${result.error::class.simpleName})"
        }
        resultView.text = text
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
