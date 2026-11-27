package com.xingheyuzhuan.shiguangschedule.ui.campus

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.webkit.CookieManager
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 教务 Cookie 加密存储于本机 noBackupFilesDir，设备密钥不可导出。学校决定会话有效期。 */
internal class CampusSessionStore(context: Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"gdou-session.enc"))
    private val alias="gdou-campus-session-v1"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save() {
        val cookie=CookieManager.getInstance().getCookie("https://jw.gdou.edu.cn") ?: return
        runCatching {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())}
            val bytes=cipher.iv + cipher.doFinal(cookie.toByteArray(Charsets.UTF_8))
            val stream=file.startWrite()
            try{stream.write(bytes);file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}
        }
    }
    @Synchronized fun restore() {
        val existing=CookieManager.getInstance().getCookie("https://jw.gdou.edu.cn").orEmpty()
            .split(';').map { it.trim().substringBefore('=') }.toSet()
        if("JSESSIONID" in existing)return
        runCatching {
            val bytes=file.readFully()
            check(bytes.size>12)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))}
            val cookie=String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
            cookie.split(';').filter { it.trim().substringBefore('=') !in existing }.forEach { part ->
                CookieManager.getInstance().setCookie("https://jw.gdou.edu.cn",part.trim()+"; Path=/; Secure; HttpOnly")
            }
            CookieManager.getInstance().flush()
        }.onFailure { file.delete() }
    }
}
