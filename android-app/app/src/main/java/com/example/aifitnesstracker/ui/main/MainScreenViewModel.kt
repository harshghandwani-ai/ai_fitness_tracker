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
                    fetchTodaySteps()
                }
            }
        }
    }

    fun fetchTodaySteps() {
        viewModelScope.launch {
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = Instant.now()
            val steps = healthConnectManager.readDailySteps(startOfDay, endOfDay)
            _uiState.update { it.copy(stepsCount = steps) }
        }
    }

    fun generateAiFitnessAdvice(topic: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true, aiRecommendation = null) }
            val steps = _uiState.value.stepsCount
            val prompt = when (topic) {
                "steps" -> "My step count today is $steps. Suggest a quick, personalized fitness recommendation based on this level of activity to keep me healthy."
                "workout" -> "Design a quick 10-minute workout routine. Currently I have completed $steps steps today."
                "nutrition" -> "Suggest a post-workout recovery snack or meal suggestion. Today I have completed $steps steps."
                else -> "Give me a quick general fitness motivation advice based on my step count of $steps steps today."
            }
            val advice = geminiService.generateFitnessAdvice(prompt)
            _uiState.update { it.copy(isAiLoading = false, aiRecommendation = advice) }
        }
    }
}
