package com.xingheyuzhuan.shiguangschedule.ui.campus

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CampusTerm(val year: Int, val semester: Int)

@Serializable
data class CampusSnapshot(
    val exams: List<CampusExam>? = null,
    val scores: List<CampusScore>? = null,
    val examsUpdatedAt: String? = null,
    val scoresUpdatedAt: String? = null,
)

/** DataStore commits to disk before refresh reports success. Each semester has its own record. */
class CampusCache(private val store: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }
    private val selectedKey = stringPreferencesKey("campus_selected_term")
    private fun key(term: CampusTerm) = stringPreferencesKey("campus_results_v1_${term.year}_${term.semester}")
    private fun decode(raw: String?) = raw?.let { json.decodeFromString<CampusSnapshot>(it) } ?: CampusSnapshot()

    suspend fun selectedTerm(): CampusTerm? = store.data.first()[selectedKey]?.let { json.decodeFromString<CampusTerm>(it) }
    suspend fun select(term: CampusTerm) { store.edit { it[selectedKey] = json.encodeToString(term) } }
    suspend fun read(term: CampusTerm): CampusSnapshot = decode(store.data.first()[key(term)])
    suspend fun saveExams(term: CampusTerm, exams: List<CampusExam>, updatedAt: String) {
        store.edit { values ->
            val next = decode(values[key(term)]).copy(exams = exams, examsUpdatedAt = updatedAt)
            values[key(term)] = json.encodeToString(next)
            values[selectedKey] = json.encodeToString(term)
        }
    }
    suspend fun saveScores(term: CampusTerm, scores: List<CampusScore>, updatedAt: String) {
        store.edit { values ->
            val next = decode(values[key(term)]).copy(scores = scores, scoresUpdatedAt = updatedAt)
            values[key(term)] = json.encodeToString(next)
            values[selectedKey] = json.encodeToString(term)
        }
    }
}
