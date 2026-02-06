package org.yatt.app.data.remote

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class KdfParams(
    val salt: String,
    val iterations: Int,
    val hash: String = "SHA-256",
    val name: String = "PBKDF2"
)

class OneDriveCrypto {
    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun buildKdfParams(salt: ByteArray, iterations: Int = 200000): KdfParams {
        return KdfParams(
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            iterations = iterations
        )
    }

    fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    fun encrypt(payloadJson: String, key: SecretKey, kdf: KdfParams): JSONObject {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(payloadJson.toByteArray(StandardCharsets.UTF_8))

        return JSONObject()
            .put("format", "yatt-onedrive-v1")
            .put("version", 1)
            .put(
                "kdf",
                JSONObject()
                    .put("name", kdf.name)
                    .put("hash", kdf.hash)
                    .put("iterations", kdf.iterations)
                    .put("salt", kdf.salt)
            )
            .put(
                "cipher",
                JSONObject()
                    .put("name", "AES-GCM")
                    .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    .put("text", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            )
    }

    fun decrypt(encrypted: JSONObject, key: SecretKey): String {
        if (encrypted.optString("format") != "yatt-onedrive-v1") {
            throw IllegalArgumentException("Invalid encrypted payload format")
        }
        val cipherJson = encrypted.getJSONObject("cipher")
        val iv = Base64.decode(cipherJson.getString("iv"), Base64.NO_WRAP)
        val text = Base64.decode(cipherJson.getString("text"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(text)
        return String(plaintext, StandardCharsets.UTF_8)
    }
}
