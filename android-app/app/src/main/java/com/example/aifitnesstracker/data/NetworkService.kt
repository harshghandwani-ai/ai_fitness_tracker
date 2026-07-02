package com.example.aifitnesstracker.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class NetworkService {

    private val baseUrl = "https://8bld7jc1jg.execute-api.ap-south-1.amazonaws.com"

    suspend fun syncHealthData(
        steps: Long,
        avgHr: Int,
        latestHr: Int,
        sleepMinutes: Long,
        caloriesBurned: Long,
        distanceKm: Double,
        hydrationMl: Double,
        weightKg: Double
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/sync")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("userId", "athlete_user")
                put("date", LocalDate.now().toString())
                put("steps", steps)
                put("avgHeartRate", avgHr)
                put("latestHeartRate", latestHr)
                put("sleepMinutes", sleepMinutes)
                put("caloriesBurned", caloriesBurned)
                put("distanceKm", distanceKm)
                put("hydrationMl", hydrationMl)
                put("weightKg", weightKg)
                put("timestamp", System.currentTimeMillis() / 1000)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                Log.d("NetworkService", "Sync metrics success")
                true
            } else {
                Log.e("NetworkService", "Sync metrics failed: $responseCode")
                false
            }
        } catch (e: Exception) {
            Log.e("NetworkService", "Sync metrics exception", e)
            false
        }
    }

    suspend fun fetchAiAdvice(
        steps: Long,
        avgHr: Int,
        latestHr: Int,
        sleepMinutes: Long,
        caloriesBurned: Long,
        distanceKm: Double,
        hydrationMl: Double,
        weightKg: Double,
        topic: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/advice")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("steps", steps)
                put("avgHeartRate", avgHr)
                put("latestHeartRate", latestHr)
                put("sleepMinutes", sleepMinutes)
                put("caloriesBurned", caloriesBurned)
                put("distanceKm", distanceKm)
                put("hydrationMl", hydrationMl)
                put("weightKg", weightKg)
                put("topic", topic)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                json.optString("advice")
            } else {
                Log.e("NetworkService", "Fetch advice failed: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e("NetworkService", "Fetch advice exception", e)
            null
        }
    }
}
