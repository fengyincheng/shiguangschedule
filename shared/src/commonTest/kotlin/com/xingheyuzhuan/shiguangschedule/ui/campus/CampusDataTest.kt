package com.xingheyuzhuan.shiguangschedule.ui.campus
import kotlin.test.*
import kotlin.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
class CampusDataTest {
    private fun <T> immediate(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object: Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result=value }
        })
        return result!!.getOrThrow()
    }
    @Test fun paginationAndTermMapping() {
        val pages=mutableListOf<String>()
        val repo=CampusRepository(object: CampusClient {
            override suspend fun post(path: String, fields: Map<String,String>): String {
                assertEquals("12",fields["xqm"])
                val n=fields.getValue("queryModel.currentPage");pages.add(n)
                return """{"items":[{"kcmc":"课程$n"}],"total":2,"records":2}"""
            }
        })
        assertEquals(2,immediate{repo.exams(2026,2)}.size)
        assertEquals(listOf("1","2"),pages)
    }
    @Test fun rejectsTruncatedPagination() {
        val repo=CampusRepository(object: CampusClient {
            override suspend fun post(path: String, fields: Map<String,String>) = """{"items":[],"records":3,"total":1}"""
        })
        assertFails{immediate{repo.exams(2026,1)}}
    }
    @Test fun statusUsesExamEnd() {
        val t="2027-01-07(08:30-10:30)"
        assertEquals("待考试",examStatus(t,"2027-01-06T12:00"))
        assertEquals("考试中",examStatus(t,"2027-01-07T09:00"))
        assertEquals("已结束",examStatus(t,"2027-01-07T11:00"))
    }
    @Test fun reuseSchoolProxyPrefixes() {
        assertEquals("https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn",campusBase("https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn/xtgl/index_initMenu.html"))
        assertEquals("https://wfw.gdou.edu.cn/http/abc123",campusBase("https://wfw.gdou.edu.cn/http/abc123/kbcx/page.html"))
        assertEquals("https://wfw.gdou.edu.cn",campusBase("https://wfw.gdou.edu.cn/xtgl/index_initMenu.html"))
        assertNull(campusBase("https://wfw.gdou.edu.cn/https://authserver.gdou.edu.cn/authserver/login"))
        assertNull(campusBase("https://evil.example/xtgl/index_initMenu.html"))
    }
    @Test fun yangjiangStaggeredTimesAndMerging() {
        val raw=Json.parseToJsonElement("""{"kcmc":"数学","xm":"教师","cdmc":"博学楼A101","xqj":"2","jcs":"第3节","zcd":"第1-16周"}""").jsonObject
        val first=parseCourse(raw)
        val result=mergeYangjiangCourses(listOf(first,first.copy(startSection=4,endSection=4)))
        assertEquals(1,result.size);assertTrue(result.single().isCustomTime)
        assertEquals("10:10",result.single().customStartTime)
        assertEquals("11:40",result.single().customEndTime)
        assertFalse(mergeYangjiangCourses(listOf(first.copy(position="慎思楼A101",endSection=4))).single().isCustomTime)
    }
    @Test fun parityAndRanges() {
        assertEquals(listOf(2,4,6,8,10,12,14,16,18),parseWeeks("2-18周(双)"))
        assertEquals(listOf(3,5,7,9,11,13,15,17),parseWeeks("2-18周(单)"))
        assertEquals(listOf(1,2,3,5,7),parseWeeks("1-3周,5周,7周"))
        assertFails { parseWeeks("待定") }
        assertFails { parseWeeks("8-2周") }
    }
    @Test fun preserveEmptyAndRejectErrors() {
        assertEquals(0,parsePage("""{"items":[],"total":0,"records":0}""").records)
        assertFailsWith<CampusLoginRequired>{parsePage("<html>统一身份认证</html>")}
        assertFails { parsePage("""{"message":"无权限"}""") }
        assertFails { parsePage("<html>维护中</html>") }
    }
    @Test fun courseConversion() {
        val c=parseCourse(Json.parseToJsonElement("""{"kcmc":"数学","xm":"教师","cdmc":"A101","xqj":"2","jcs":"3-4","zcd":"1-16周(双)"}""").jsonObject)
        assertEquals(2,c.day); assertEquals(3,c.startSection);assertEquals(4,c.endSection)
        assertEquals(listOf(2,4,6,8,10,12,14,16),c.weeks)
    }
}
