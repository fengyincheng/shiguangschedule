package com.xingheyuzhuan.shiguangschedule.ui.campus

const val GDOU_LOGIN_URL = "https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn/"
// 与 shiguang_warehouse/resources/GDOU/gdouyj.js 的运行时反代前缀推导保持一致。
internal fun campusBase(url: String): String? {
    val match=Regex("^(https?://[^/]+)(/[^?#]*)?").find(url) ?: return null
    val origin=match.groupValues[1]
    val path=match.groupValues[2]
    if(origin=="https://jw.gdou.edu.cn")return origin
    if(origin!="https://wfw.gdou.edu.cn")return null
    if(path=="/https://jw.gdou.edu.cn" || path.startsWith("/https://jw.gdou.edu.cn/"))return "$origin/https://jw.gdou.edu.cn"
    if(Regex("^/(?:xtgl|kbcx|kwgl|cjcx)/").containsMatchIn(path))return origin
    val prefix=Regex("^/(?:http|https|https-443)/[^/]+(?=/(?:xtgl|kbcx|kwgl|cjcx)/)").find(path)?.value
    return prefix?.let { origin+it }
}
