package com.xingheyuzhuan.shiguangschedule.ui.campus

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.webkit.CookieManager
import java.io.File
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** 校外通道会话加密存于 noBackupFilesDir，复用全 App 的 WebView CookieManager。 */
internal class CampusSessionStore(context: Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"gdou-session.enc"))
    private val alias="gdou-campus-session-v1"
    var base: String=GDOU_LOGIN_URL.trimEnd('/')
        private set
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun observe(url: String) { campusBase(url)?.let{base=it;save()} }
    @Synchronized fun save() {
        val cookie=CookieManager.getInstance().getCookie("$base/") ?: return
        runCatching {
            val json=JSONObject().put("base",base).put("cookie",cookie).toString()
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())}
            val bytes=cipher.iv + cipher.doFinal(json.toByteArray(Charsets.UTF_8))
            val stream=file.startWrite()
            try{stream.write(bytes);file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}
        }
    }
    @Synchronized fun restore() {
        if(!file.baseFile.exists())return
        runCatching {
            val bytes=file.readFully();check(bytes.size>12)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))}
            val json=JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
            val savedBase=json.getString("base")
            check(campusBase("$savedBase/xtgl/index_initMenu.html")==savedBase)
            base=savedBase
            val existing=CookieManager.getInstance().getCookie("$base/").orEmpty().split(';').map{it.trim().substringBefore('=')}.toSet()
            val path=URI(base).path.ifBlank{"/"}
            json.getString("cookie").split(';').filter{it.trim().substringBefore('=') !in existing}.forEach { part ->
                CookieManager.getInstance().setCookie("$base/",part.trim()+"; Path=$path; Secure; HttpOnly")
            }
            CookieManager.getInstance().flush()
        }.onFailure { file.delete() }
    }
}
