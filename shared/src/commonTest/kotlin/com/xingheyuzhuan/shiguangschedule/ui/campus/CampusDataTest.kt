package com.xingheyuzhuan.shiguangschedule.ui.campus
import kotlin.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
class CampusDataTest {
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
