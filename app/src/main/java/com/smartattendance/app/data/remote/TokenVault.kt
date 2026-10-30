package com.smartattendance.app.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only the encrypted refresh token is persisted; Android backup is disabled. */
class TokenVault(context: Context) {
    private val prefs=context.getSharedPreferences("auth_vault",Context.MODE_PRIVATE)
    private val alias="smart_attendance_refresh_v1"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(token: String) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key())
        prefs.edit().putString("refresh",Base64.encodeToString(cipher.iv+cipher.doFinal(token.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)).apply()
    }
    fun read(): String? = runCatching {
        val bytes=Base64.decode(prefs.getString("refresh",null) ?: return null,Base64.NO_WRAP)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
    }.getOrElse { clear(); null }
    fun clear() { prefs.edit().clear().apply() }
}
