package com.xingheyuzhuan.shiguangschedule.ui.campus

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.*

class CampusCacheTest {
    @Test fun persistedResultsSurviveStoreRestartAndStaySeparatedByTerm() = runBlocking {
        val directory = Files.createTempDirectory("campus-cache-test").toFile()
        val file = directory.resolve("results.preferences_pb").absolutePath.toPath()
        var job = SupervisorJob()
        fun open() = CampusCache(PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }
        ))
        val first = CampusTerm(2026,1)
        val second = CampusTerm(2026,2)
        val exam = CampusExam("数学","M1","2027-01-07(08:30-10:30)","101","2","阳江","期末")
        val score = CampusScore("数学","M1","90","4","4.0")
        try {
            var cache = open()
            assertNull(cache.read(first).exams)
            cache.saveExams(first,listOf(exam),"2026-11-27 12:00:00")
            cache.saveScores(first,listOf(score),"2026-11-27 12:01:00")
            cache.saveExams(second,emptyList(),"2026-11-27 12:02:00")
            cache.select(first)
            job.cancelAndJoin()
            job = SupervisorJob()
            cache = open()
            assertEquals(first,cache.selectedTerm())
            assertEquals(listOf(exam),cache.read(first).exams)
            assertEquals(listOf(score),cache.read(first).scores)
            assertEquals("2026-11-27 12:01:00",cache.read(first).scoresUpdatedAt)
            assertEquals(emptyList(),cache.read(second).exams)
            assertNull(cache.read(second).scores)
            // A successful empty response clears stale exams but retains grades.
            cache.saveExams(first,emptyList(),"2026-11-28 12:00:00")
            assertEquals(emptyList(),cache.read(first).exams)
            assertEquals(listOf(score),cache.read(first).scores)
            job.cancelAndJoin()
            job = SupervisorJob()
            cache = open()
            assertEquals(emptyList(),cache.read(first).exams)
            assertEquals(listOf(score),cache.read(first).scores)
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
