package com.akash.agni.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Looks up an animated demo GIF for an exercise from the free ExerciseDB API and caches the URL.
 * If the API is unreachable or changes shape, this returns null and the UI falls back to the
 * YouTube demo button, so nothing breaks.
 */
class ExerciseMediaRepository(private val dao: ExerciseMediaDao) {

    private val endpoints = listOf(
        "https://oss.exercisedb.dev/api/v1/exercises?limit=10&search=",
        "https://exercisedb-api.vercel.app/api/v1/exercises?limit=10&search=",
    )

    suspend fun gifUrl(exercise: Exercise): String? {
        val cached = dao.get(exercise.id)
        val weekMs = 7L * 24 * 60 * 60 * 1000
        if (cached != null && (cached.gifUrl != null || System.currentTimeMillis() - cached.fetchedAt < weekMs)) {
            return cached.gifUrl
        }
        val url = withContext(Dispatchers.IO) { fetch(exercise.gifQuery) }
        dao.put(ExerciseMedia(exercise.id, url, System.currentTimeMillis()))
        return url
    }

    private fun fetch(query: String): String? {
        for (base in endpoints) {
            try {
                val conn = URL(base + URLEncoder.encode(query, "UTF-8")).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/json")
                try {
                    if (conn.responseCode !in 200..299) continue
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    pickGif(body, query)?.let { return it }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                // try the next endpoint
            }
        }
        return null
    }

    private fun pickGif(body: String, query: String): String? {
        val items: JSONArray = when {
            body.trimStart().startsWith("[") -> JSONArray(body)
            else -> JSONObject(body).let { it.optJSONArray("data") ?: it.optJSONArray("exercises") } ?: return null
        }
        val candidates = (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val gif = o.optString("gifUrl").ifBlank { o.optString("gif_url") }.ifBlank { null } ?: return@mapNotNull null
            o.optString("name").lowercase() to gif
        }
        val q = query.lowercase()
        return (candidates.firstOrNull { it.first == q }
            ?: candidates.firstOrNull { it.first.startsWith(q) }
            ?: candidates.firstOrNull { it.first.contains(q) }
            ?: candidates.firstOrNull())?.second
    }
}
