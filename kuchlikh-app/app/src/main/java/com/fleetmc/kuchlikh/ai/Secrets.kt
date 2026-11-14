package com.fleetmc.kuchlikh.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.fleetmc.kuchlikh.BuildConfig
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Secrets {

    private val pad: ByteArray = MessageDigest.getInstance("SHA-256").digest("kuchlikh.fleetmc".toByteArray())

    fun builtIn(): String {
        val blob = BuildConfig.AI_BLOB
        if (blob.isEmpty() || blob.length % 2 != 0) return ""
        val bytes = ByteArray(blob.length / 2) { i ->
            (blob.substring(i * 2, i * 2 + 2).toInt(16) xor pad[i % pad.size].toInt()).toByte()
        }
        return String(bytes)
    }
}

class KeyVault(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("vault", Context.MODE_PRIVATE)

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun save(value: String): Boolean = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val sealed = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit().putString("groq", Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }.isSuccess

    fun load(): String? = runCatching {
        val sealed = Base64.decode(prefs.getString("groq", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, sealed, 0, 12))
        String(cipher.doFinal(sealed, 12, sealed.size - 12))
    }.getOrNull()

    fun clear() {
        prefs.edit().remove("groq").apply()
    }

    fun has(): Boolean = prefs.contains("groq")

    private companion object {
        const val ALIAS = "kuchlikh.vault"
    }
}
