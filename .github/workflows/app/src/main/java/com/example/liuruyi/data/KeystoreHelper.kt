package com.example.liuruyi.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 存 Android Keystore：用 Keystore 内的 AES 密钥加密后，
 * 密文存 SharedPreferences。明文 Key 绝不写入聊天文件，也不进日志。
 */
object KeystoreHelper {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "liuruyi_api_key"
    private const val PREFS = "liuruyi_secrets"
    private const val PREF_KEY = "encrypted_api_key"

    @Volatile
    private var cachedKey: SecretKey? = null

    fun saveApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (apiKey.isBlank()) {
            prefs.edit().remove(PREF_KEY).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherBytes = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val payload = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, payload, 0, iv.size)
        System.arraycopy(cipherBytes, 0, payload, iv.size, cipherBytes.size)
        prefs.edit().putString(PREF_KEY, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun loadApiKey(context: Context): String? {
        val payload = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_KEY, null)
            ?: return null
        return try {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val cipherBytes = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun hasApiKey(context: Context): Boolean = !loadApiKey(context).isNullOrBlank()

    fun clearApiKey(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(PREF_KEY).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
            cachedKey = it
            return it
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        val key = generator.generateKey()
        cachedKey = key
        return key
    }
}
