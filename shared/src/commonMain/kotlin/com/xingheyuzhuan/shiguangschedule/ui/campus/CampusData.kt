package com.xingheyuzhuan.shiguangschedule.ui.campus

import androidx.compose.runtime.Composable
import com.xingheyuzhuan.shiguangschedule.data.model.CourseImportExport.ImportCourseJsonModel
import kotlinx.serialization.json.*

class CampusLoginRequired : Exception("登录已失效，请登录教务系统后重试")
interface CampusClient {
    suspend fun post(path: String, fields: Map<String, String>): String
}
@Composable expect fun rememberCampusClient(): CampusClient
@Composable expect fun CampusLogin(onClose: () -> Unit)

internal fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun responseObject(body: String): JsonObject {
    if (Regex("login_slogin|authserver/login|id=[\"']?(?:fm1|loginForm)|统一身份认证").containsMatchIn(body)) throw CampusLoginRequired()
    return runCatching { Json.parseToJsonElement(body).jsonObject }.getOrElse { error("教务系统返回格式异常，请稍后重试") }
}

data class CampusExam(val course: String, val courseId: String, val time: String, val location: String,
    val seat: String, val campus: String, val name: String)
data class CampusScore(val course: String, val courseId: String, val score: String, val credit: String, val point: String)
data class CampusPage(val items: List<JsonObject>, val pages: Int?, val records: Int?)
internal fun parsePage(body: String): CampusPage {
    val data = responseObject(body)
    val items = data["items"] as? JsonArray ?: error("教务系统未返回查询列表，请核对访问权限")
    return CampusPage(items.map { it as? JsonObject ?: error("查询记录格式异常") }, data.text("total").toIntOrNull(), data.text("records").toIntOrNull())
}
class CampusRepository(private val client: CampusClient) {
    private fun term(year: Int, semester: Int): Map<String, String> {
        require(year in 2000..2100 && semester in 1..2)
        return mapOf("xnm" to year.toString(), "xqm" to if (semester == 1) "3" else "12")
    }
    private suspend fun query(path: String, year: Int, semester: Int): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        var previous: List<JsonObject>? = null
        for (page in 1..30) {
            val result = parsePage(client.post(path, term(year, semester) + mapOf(
                "queryModel.showCount" to "100", "queryModel.currentPage" to page.toString(),
                "queryModel.sortName" to "", "queryModel.sortOrder" to "asc", "_search" to "false")))
            check(result.items.isEmpty() || previous != result.items) { "教务系统重复返回同一页，请稍后重试" }
            all.addAll(result.items)
            previous = result.items
            val complete = result.records?.let { all.size >= it }
                ?: result.pages?.let { page >= it } ?: (result.items.size < 100)
            if (complete) {
                check(result.records == null || result.records == all.size) { "返回总数与记录数不符，请重新刷新" }
                return all
            }
            check(result.items.isNotEmpty()) { "考试或成绩分页数据不完整，请重新刷新" }
        }
        error("查询页数超出预期，请核对学校页面")
    }
    suspend fun exams(year: Int, semester: Int) = query("/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105", year, semester).map {
        check(it.text("kcmc").isNotBlank()) { "考试课程字段缺失" }
        CampusExam(it.text("kcmc"), it.text("kch"), it.text("kssj"), it.text("cdmc"),
            it.text("zwh").ifBlank { it.text("zwxh") }, it.text("xqmc"), it.text("ksmc"))
    }.sortedBy { it.time.ifBlank { "9999" } }
    suspend fun scores(year: Int, semester: Int) = query("/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005", year, semester).map {
        check(it.text("kcmc").isNotBlank()) { "成绩课程字段缺失" }
        CampusScore(it.text("kcmc"), it.text("kch"), it.text("cj"), it.text("xf"), it.text("jd"))
    }
    suspend fun schedule(year: Int, semester: Int): List<ImportCourseJsonModel> {
        val data = responseObject(client.post("/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151", term(year,semester)))
        val list = data["kbList"] as? JsonArray ?: error("课表接口未返回 kbList，原课表保持不变")
        // 实践课采用另一种排课结构，拒绝静默丢弃后覆盖原课表。
        check((data["sjkList"] as? JsonArray).isNullOrEmpty()) { "本学期包含实践课，请使用原教务导入入口完整导入" }
        return list.map { parseCourse(it.jsonObject) }
    }
}
internal fun parseWeeks(raw: String): List<Int> {
    val cleaned = raw.replace("周", "").replace("（", "(").replace("）", ")").replace("，", ",").replace(" ", "")
    val result = mutableSetOf<Int>()
    cleaned.split(',').filter { it.isNotBlank() }.forEach { part ->
        val match = Regex("^(\\d+)(?:-(\\d+))?(?:\\(([单双])\\))?$").matchEntire(part)
            ?: error("无法识别上课周次：$raw")
        val start = match.groupValues[1].toInt()
        val end = match.groupValues[2].toIntOrNull() ?: start
        check(start in 1..60 && end in start..60) { "上课周次超出范围" }
        (start..end).filter { when(match.groupValues[3]) { "单" -> it % 2 == 1; "双" -> it % 2 == 0; else -> true } }.forEach(result::add)
    }
    check(result.isNotEmpty()) { "课程周次为空，原课表保持不变" }
    return result.sorted()
}
internal fun parseCourse(row: JsonObject): ImportCourseJsonModel {
    val name = row.text("kcmc")
    val day = row.text("xqj").toIntOrNull() ?: 0
    val sections = Regex("^(\\d+)(?:-(\\d+))?$").matchEntire(row.text("jcs")) ?: error("无法识别 $name 的节次")
    val start = sections.groupValues[1].toInt()
    val end = sections.groupValues[2].toIntOrNull() ?: start
    check(name.isNotBlank() && day in 1..7 && start in 1..30 && end in start..30) { "课程字段异常，原课表保持不变" }
    return ImportCourseJsonModel(name, row.text("xm"), row.text("cdmc"), day, start, end, parseWeeks(row.text("zcd")), credit = row.text("xf").toFloatOrNull())
}
