package com.xingheyuzhuan.shiguangschedule.ui.campus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xingheyuzhuan.shiguangschedule.data.model.CourseImportExport.ImportCourseJsonModel
import com.xingheyuzhuan.shiguangschedule.data.repository.AppSettingsRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseConversionRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseTableRepository
import com.xingheyuzhuan.shiguangschedule.ui.components.LocalScaffoldPadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import org.koin.compose.koinInject
import kotlin.time.Clock

private fun currentSchoolYear(): Int {
    val today=Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return if (today.month.number >= 8) today.year else today.year-1
}
private fun currentSemester(): Int = if (Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).month.number in 2..7) 2 else 1
@Composable private fun SemesterPicker(year: Int, semester: Int, enabled: Boolean, onChange: (Int,Int)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={expanded=true},enabled=enabled) { Text("$year—${year+1} / 第${semester}学期") }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            for (y in currentSchoolYear()+1 downTo currentSchoolYear()-6) for (s in 1..2) {
                DropdownMenuItem(text={Text("$y—${y+1} 第${s}学期")},onClick={onChange(y,s);expanded=false})
            }
        }
    }
}
private fun errorMessage(error: Exception): String = when(error) {
    is CampusLoginRequired -> error.message!!
    else -> error.message?.take(180) ?: "网络请求失败，请稍后重试"
}
@Composable fun CampusScreen() {
    val client=rememberCampusClient()
    val repository=remember(client){CampusRepository(client)}
    val scope=rememberCoroutineScope()
    var year by rememberSaveable{mutableStateOf(currentSchoolYear())}
    var semester by rememberSaveable{mutableStateOf(currentSemester())}
    var login by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("广东海洋大学 · 阳江校区。登录后可持续刷新，登录过期时重新登录。")}
    var exams by remember{mutableStateOf<List<CampusExam>>(emptyList())}
    var scores by remember{mutableStateOf<List<CampusScore>>(emptyList())}
    var queried by remember{mutableStateOf(false)}
    var scoreQueried by remember{mutableStateOf(false)}
    var details by remember{mutableStateOf<CampusExam?>(null)}
    fun refresh() { scope.launch {
        busy=true
        try {
            val result=repository.exams(year,semester)
            exams=result; queried=true
            message="考试同步成功，共 ${result.size} 场；${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time.toString().take(8)} 更新"
        } catch(e: CancellationException){throw e} catch(e: Exception){message=errorMessage(e);if(e is CampusLoginRequired)login=true} finally{busy=false}
    } }
    Column(Modifier.fillMaxSize().padding(LocalScaffoldPadding.current).padding(horizontal=16.dp)) {
        Text("考试安排",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(vertical=16.dp))
        SemesterPicker(year,semester,!busy){y,s->year=y;semester=s;exams=emptyList();scores=emptyList();queried=false;scoreQueried=false;message="学期已切换，请刷新"}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={login=true},enabled=!busy){Text("登录")}
            Button(onClick={refresh()},enabled=!busy){Text("刷新考试")}
            OutlinedButton(enabled=!busy,onClick={scope.launch {
                busy=true
                try{scores=repository.scores(year,semester);scoreQueried=true;message="成绩同步成功，共 ${scores.size} 条"}
                catch(e: CancellationException){throw e} catch(e: Exception){message=errorMessage(e);if(e is CampusLoginRequired)login=true} finally{busy=false}
            }}){Text("刷新成绩")}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(message,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=12.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.weight(1f)) {
            if(queried&&exams.isEmpty())item{Text("本学期暂无已公布考试安排")}
            items(exams){exam->
                Card(onClick={details=exam},modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(exam.course,style=MaterialTheme.typography.titleMedium)
                        Text(exam.time.ifBlank{"时间待公布"})
                        Text("${exam.location.ifBlank{"考场待公布"}} · ${exam.campus}")
                        Text("座位：${exam.seat.ifBlank{"待公布"}}",style=MaterialTheme.typography.bodySmall)
                        val related=scores.filter{ if(exam.courseId.isNotBlank())it.courseId==exam.courseId else it.course==exam.course }
                        if(related.isNotEmpty())Text("成绩：${related.joinToString(" / "){it.score.ifBlank{"待公布"}}}",color=MaterialTheme.colorScheme.primary)
                        else if(scoreQueried)Text("成绩待公布",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if(scoreQueried)item{Text("本学期成绩",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=12.dp))}
            if(scoreQueried&&scores.isEmpty())item{Text("本学期暂无已公布成绩")}
            items(scores){score->Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(score.course,style=MaterialTheme.typography.titleMedium)
                Text("成绩：${score.score.ifBlank{"待公布"}}   学分：${score.credit}   绩点：${score.point}")
            } } }
            item{Spacer(Modifier.height(16.dp))}
        }
    }
    if(login)CampusLogin{login=false;message="登录窗口已关闭，点击刷新验证登录状态"}
    details?.let { exam -> AlertDialog(onDismissRequest={details=null},title={Text(exam.course)},text={Text("${exam.name}\n时间：${exam.time}\n考场：${exam.location}\n校区：${exam.campus}\n座位：${exam.seat.ifBlank{"待公布"}}\n课程代码：${exam.courseId}")},confirmButton={TextButton(onClick={details=null}){Text("关闭")}}) }
}

@Composable fun CampusScheduleRefreshButton() {
    var show by remember{mutableStateOf(false)}
    TextButton(onClick={show=true}){Text("刷新")}
    if(show)CampusScheduleRefreshDialog{show=false}
}
@Composable private fun CampusScheduleRefreshDialog(onClose:()->Unit) {
    val client=rememberCampusClient()
    val repository=remember(client){CampusRepository(client)}
    val settings: AppSettingsRepository=koinInject()
    val tables: CourseTableRepository=koinInject()
    val conversion: CourseConversionRepository=koinInject()
    val scope=rememberCoroutineScope()
    var year by rememberSaveable{mutableStateOf(currentSchoolYear())}
    var semester by rememberSaveable{mutableStateOf(currentSemester())}
    var tableId by remember{mutableStateOf("")}
    var tableName by remember{mutableStateOf("")}
    var message by remember{mutableStateOf("获取最新课表后确认覆盖；保留当前课表的作息和学期设置。")}
    var pending by remember{mutableStateOf<List<ImportCourseJsonModel>?>(null)}
    var busy by remember{mutableStateOf(false)}
    var login by remember{mutableStateOf(false)}
    LaunchedEffect(Unit) {
        val all=tables.getAllCourseTables().first()
        val selected=settings.getAppSettings().first().currentCourseTableId
        val table=all.find{it.id==selected}?:all.firstOrNull()
        tableId=table?.id.orEmpty();tableName=table?.name.orEmpty()
        settings.getCourseConfigOnce(tableId)?.semesterStartDate?.let { date ->
            val start=LocalDate.parse(date)
            year=if(start.month.number>=8)start.year else start.year-1
            semester=if(start.month.number>=8)1 else 2
        }
    }
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("刷新课表 · $tableName")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("广东海洋大学 · 与考试页共用登录态",style=MaterialTheme.typography.bodySmall)
        SemesterPicker(year,semester,!busy){y,s->year=y;semester=s;pending=null}
        Text(message)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick={login=true},enabled=!busy){Text("登录教务系统")}
    }},confirmButton={TextButton(enabled=!busy&&tableId.isNotEmpty(),onClick={scope.launch {
        busy=true
        try {
            val courses=pending
            if(courses==null){
                val result=repository.schedule(year,semester)
                check(result.isNotEmpty()){ "本学期课表为空，原课表保持不变" }
                pending=result
                message="已获取 ${result.size} 条排课。确认后将覆盖「$tableName」中的课程，手动修改的课程也会被替换。"
            }else{
                conversion.importCourses(tableId,courses)
                pending=null;message="课表刷新成功，共 ${courses.size} 条排课。"
            }
        } catch(e: CancellationException){throw e} catch(e: Exception){message=errorMessage(e);if(e is CampusLoginRequired)login=true} finally{busy=false}
    }}){Text(if(pending==null)"获取最新课表" else "确认覆盖")}},dismissButton={TextButton(onClick=onClose,enabled=!busy){Text("关闭")}})
    if(login)CampusLogin{login=false}
}
