package com.xingheyuzhuan.shiguangschedule.ui.campus

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.WebCompatDelegate
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.DESKTOP_USER_AGENT
import android.webkit.WebResourceRequest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

private class AndroidCampusClient(private val session: CampusSessionStore) : CampusClient {
    override suspend fun post(path: String, fields: Map<String,String>): String = withContext(Dispatchers.IO) {
        session.restore()
        require(path.startsWith("/") && !path.startsWith("//"))
        val school=session.base
        val connection = URL(school + path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20000; connection.readTimeout = 30000
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
            connection.setRequestProperty("Referer", school + path.substringBefore('?'))
            CookieManager.getInstance().getCookie(school + path)?.let { connection.setRequestProperty("Cookie", it) }
            val body = fields.entries.joinToString("&") { URLEncoder.encode(it.key,"UTF-8") + "=" + URLEncoder.encode(it.value,"UTF-8") }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            connection.headerFields.entries.filter { it.key?.equals("Set-Cookie", true) == true }.flatMap { it.value }.forEach {
                CookieManager.getInstance().setCookie(school + path, it)
            }
            CookieManager.getInstance().flush()
            if (status == 401 || (status in 300..399 && connection.getHeaderField("Location").orEmpty().let { "login" in it || "authserver" in it || "wengine" in it || "auth" in it })) throw CampusLoginRequired()
            check(status == 200) { "教务系统返回 HTTP $status，请稍后重试" }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.also {
                responseObject(it)
                session.save()
            }
        } finally { connection.disconnect() }
    }
}
@Composable actual fun rememberCampusClient(): CampusClient {
    val context=LocalContext.current.applicationContext
    return remember(context) { AndroidCampusClient(CampusSessionStore(context)) }
}
@SuppressLint("SetJavaScriptEnabled")
@Composable actual fun CampusLogin(onClose: () -> Unit) {
    val context=LocalContext.current.applicationContext
    val session=remember(context){CampusSessionStore(context)}
    var web by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf(GDOU_LOGIN_URL) }
    var hint by remember { mutableStateOf("完成学校登录后，点击完成") }
    DisposableEffect(Unit) { onDispose { CookieManager.getInstance().flush(); session.save(); web?.destroy() } }
    Dialog(onDismissRequest=onClose, properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={ web?.loadUrl(GDOU_LOGIN_URL) }) { Text("校外教务入口") }
                    TextButton(onClick={ CookieManager.getInstance().flush(); onClose() }) { Text("完成") }
                }
                Text(address, modifier=Modifier.padding(horizontal=16.dp), style=MaterialTheme.typography.labelSmall, maxLines=2)
                Text(hint, modifier=Modifier.padding(16.dp), style=MaterialTheme.typography.bodySmall)
                AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(), factory={ context ->
                    WebView(context).apply {
                        web=this
                        settings.javaScriptEnabled=true; settings.domStorageEnabled=true
                        settings.allowFileAccess=false; settings.allowContentAccess=false
                        CookieManager.getInstance().setAcceptCookie(true)
                        val delegate=WebCompatDelegate(this).enhanceSettings(true)
                        webChromeClient=delegate.wrapWebChromeClient(WebChromeClient()){}
                        webViewClient=delegate.wrapWebViewClient(object: WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri=request.url
                                return uri.scheme !in listOf("http", "https") || !(uri.host == "gdou.edu.cn" || uri.host?.endsWith(".gdou.edu.cn") == true)
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                address=runCatching { URI(url).let { "${it.scheme}://${it.host}${it.path}" } }.getOrDefault("学校登录页面")
                                session.observe(url)
                                if (campusBase(url) != null && "/xtgl/index" in url) hint="已进入教务系统，点击完成后刷新"
                                CookieManager.getInstance().flush()
                            }
                        }, {true})
                        session.restore()
                        loadUrl(GDOU_LOGIN_URL)
                    }
                })
            }
        }
    }
}
