package com.xingheyuzhuan.shiguangschedule.ui.campus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.*
@Composable actual fun rememberCampusClient(): CampusClient = remember {
    object: CampusClient { override suspend fun post(path: String, fields: Map<String,String>): String = error("请在 Android 版使用校园同步") }
}
@Composable actual fun CampusLogin(onClose: () -> Unit) {
    AlertDialog(onDismissRequest=onClose, title={Text("校园同步")}, text={Text("请在 Android 版登录广东海洋大学教务系统")}, confirmButton={TextButton(onClick=onClose){Text("确定")}})
}
