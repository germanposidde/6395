package com.kmp.hook.elite.util

import android.util.Base64
import com.kmp.hook.elite.localdata.SingleMap
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.iterator

object AESEncryption {
    private val KEY_LENGTH_BITS = 256
    private val PBKDF2_ITERATIONS = 256
    private val SALT_LENGTH = 16
    private val IV_LENGTH = 12
    private val TAG_LENGTH = 16
    private val TAG_LENGTH_BITS = TAG_LENGTH * 8

    /**
     * Derives a key using PBKDF2 with SHA-256
     */
    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Encrypts plaintext using AES-256-GCM
     * Returns base64-encoded: salt || iv || ciphertext || tag
     */
    fun encrypt(plaintext: String, password: String): String {
        // Generate random salt and IV
        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().apply {
            nextBytes(salt)
            nextBytes(iv)
        }

        // Derive key (note: password is reversed, matching PHP strrev())
        val key = deriveKey(password.reversed(), salt)
        val secretKey = SecretKeySpec(key, "AES")

        // Encrypt using AES-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val ciphertextWithTag = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

        // Split ciphertext and tag
        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - TAG_LENGTH)
        val tag = ciphertextWithTag.copyOfRange(
            ciphertextWithTag.size - TAG_LENGTH,
            ciphertextWithTag.size
        )

        // Combine: salt || iv || ciphertext || tag
        val combined = salt + iv + ciphertext + tag

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts base64-encoded encrypted data
     */
    fun decrypt(base64: String, password: String): String {
        val decoded = Base64.decode(base64, Base64.NO_WRAP)

        if (decoded.size < SALT_LENGTH + IV_LENGTH + TAG_LENGTH) {
            throw kotlin.IllegalArgumentException("Data too short")
        }

        // Extract components
        val salt = decoded.copyOfRange(0, SALT_LENGTH)
        val iv = decoded.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertextAndTag = decoded.copyOfRange(SALT_LENGTH + IV_LENGTH, decoded.size)

        // Derive key
        val key = deriveKey(password.reversed(), salt)
        val secretKey = SecretKeySpec(key, "AES")

        // Decrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val plaintext = cipher.doFinal(ciphertextAndTag)
        return String(plaintext, StandardCharsets.UTF_8)
    }
}

object PayloadEncoder {
    private val EXTRA_PARAM_1 = SingleMap.map["paramOne"]
    private val EXTRA_PARAM_3 = SingleMap.map["weeks"]
    private val EXTRA_PARAM_4 = SingleMap.map["p4"]
    private val EXTRA_PARAM_5 = SingleMap.map["p5"]
    private val EXTRA_PARAM_7 = SingleMap.map["p7"]
    private val EXTRA_PARAM_8 = SingleMap.map["p8"]
    private val EXTRA_PARAM_9 = SingleMap.map["p9"]

    private val jsonSecret = "$EXTRA_PARAM_3$EXTRA_PARAM_9$EXTRA_PARAM_1"
    private val jsonKeysSecret = "$EXTRA_PARAM_9$EXTRA_PARAM_8$EXTRA_PARAM_7"
    private val jsonValuesSecret = "$EXTRA_PARAM_4$EXTRA_PARAM_5$EXTRA_PARAM_3"

    /**
     * Encodes payload compatible with PHP decodePayload function
     *
     * @param data Map of key-value pairs to encode
     * @return Base64-encoded encrypted payload
     */
    fun encodePayload(data: Map<String, String>): String {
        // Step 1: Encrypt each key and value
        val encryptedMap = mutableMapOf<String, String>()

        for ((key, value) in data) {
            val encryptedKey = AESEncryption.encrypt(key, jsonKeysSecret)
            val encryptedValue = AESEncryption.encrypt(value, jsonValuesSecret)
            encryptedMap[encryptedKey] = encryptedValue
        }

        // Step 2: Convert to JSON
        val jsonObject = JSONObject(encryptedMap)
        val jsonString = jsonObject.toString()

        // Step 3: Encrypt the entire JSON
        return AESEncryption.encrypt(jsonString, jsonSecret)
    }
}
