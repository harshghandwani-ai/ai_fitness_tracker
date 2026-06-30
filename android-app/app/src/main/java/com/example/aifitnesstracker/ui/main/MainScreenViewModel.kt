package com.example.aifitnesstracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aifitnesstracker.data.GeminiService
import com.example.aifitnesstracker.data.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DashboardUiState(
    val stepsCount: Long = 0,
    val averageHeartRate: Int = 0,
    val latestHeartRate: Int = 0,
    val sleepDurationMinutes: Long = 0,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthPermissions: Boolean = false,
    val aiRecommendation: String? = null,
    val isAiLoading: Boolean = false
)

class MainScreenViewModel(
    private val healthConnectManager: HealthConnectManager,
    private val geminiService: GeminiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        checkHealthConnectStatus()
    }

    fun checkHealthConnectStatus() {
        val status = healthConnectManager.availabilityStatus
        val available = status == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE
        _uiState.update { it.copy(isHealthConnectAvailable = available) }
        if (available) {
            viewModelScope.launch {
                val hasPerms = healthConnectManager.hasAllPermissions()
                _uiState.update { it.copy(hasHealthPermissions = hasPerms) }
                if (hasPerms) {
                    fetchTodayHealthData()
                }
            }
        }
    }

    fun fetchTodayHealthData() {
        viewModelScope.launch {
            val now = Instant.now()
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val past24Hours = now.minus(java.time.Duration.ofDays(1))
            
            // Query Steps
            val steps = healthConnectManager.readDailySteps(startOfDay, now)
            
            // Query Heart Rate
            val heartRates = healthConnectManager.readHeartRate(startOfDay, now)
            val avgBpm = if (heartRates.isNotEmpty()) heartRates.average().toInt() else 0
            val lastBpm = if (heartRates.isNotEmpty()) heartRates.last() else 0
            
            // Query Sleep (Past 24 Hours)
            val sleepMins = healthConnectManager.readSleepDuration(past24Hours, now)
            
            _uiState.update { 
                it.copy(
                    stepsCount = steps,
                    averageHeartRate = avgBpm,
                    latestHeartRate = lastBpm,
                    sleepDurationMinutes = sleepMins
                ) 
            }
        }
    }

    fun generateAiFitnessAdvice(topic: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true, aiRecommendation = null) }
            val steps = _uiState.value.stepsCount
            val avgBpm = _uiState.value.averageHeartRate
            val lastBpm = _uiState.value.latestHeartRate
            val sleepMinsTotal = _uiState.value.sleepDurationMinutes
            
            val heartRatePart = if (avgBpm > 0) {
                "My average heart rate today is $avgBpm BPM (with a latest reading of $lastBpm BPM)."
            } else {
                "My heart rate data is not logged yet."
            }

            val sleepHours = sleepMinsTotal / 60
            val sleepRemainingMins = sleepMinsTotal % 60
            val sleepPart = if (sleepMinsTotal > 0) {
                "Last night I slept for $sleepHours hours and $sleepRemainingMins minutes."
            } else {
                "My sleep data is not logged yet."
            }

            val prompt = when (topic) {
                "steps" -> "My step count today is $steps. $heartRatePart. $sleepPart. Suggest a quick, personalized fitness recommendation based on these activity, heart rate, and sleep rest levels to keep me healthy."
                "workout" -> "Design a quick 10-minute workout routine. Currently I have completed $steps steps today. $heartRatePart. $sleepPart. Tune the intensity of the workout depending on how much sleep/rest I got."
                "nutrition" -> "Suggest a post-workout recovery snack or meal suggestion. Today I have completed $steps steps. $heartRatePart. $sleepPart."
                else -> "Give me a quick general fitness motivation advice based on my step count of $steps steps, $heartRatePart, and $sleepPart today."
            }
            val advice = geminiService.generateFitnessAdvice(prompt)
            _uiState.update { it.copy(isAiLoading = false, aiRecommendation = advice) }
        }
    }
}
