package com.example.aifitnesstracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aifitnesstracker.data.HealthConnectManager
import com.example.aifitnesstracker.data.NetworkService
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
    val caloriesBurned: Long = 0,
    val distanceKm: Double = 0.0,
    val hydrationMl: Double = 0.0,
    val weightKg: Double = 0.0,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthPermissions: Boolean = false,
    val aiRecommendation: String? = null,
    val isAiLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isSynced: Boolean = false
)

class MainScreenViewModel(
    private val healthConnectManager: HealthConnectManager,
    private val networkService: NetworkService = NetworkService()
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

            // Query Calories
            val calories = healthConnectManager.readActiveCaloriesBurned(startOfDay, now)

            // Query Distance
            val distance = healthConnectManager.readDistance(startOfDay, now)

            // Query Hydration
            val hydration = healthConnectManager.readHydration(startOfDay, now)

            // Query Weight
            val weight = healthConnectManager.readLatestWeight(startOfDay, now)
            
            _uiState.update { 
                it.copy(
                    stepsCount = steps,
                    averageHeartRate = avgBpm,
                    latestHeartRate = lastBpm,
                    sleepDurationMinutes = sleepMins,
                    caloriesBurned = calories,
                    distanceKm = distance,
                    hydrationMl = hydration,
                    weightKg = weight,
                    isSynced = false
                ) 
            }

            // Sync metrics with AWS DynamoDB
            _uiState.update { it.copy(isSyncing = true) }
            val success = networkService.syncHealthData(
                steps = steps,
                avgHr = avgBpm,
                latestHr = lastBpm,
                sleepMinutes = sleepMins,
                caloriesBurned = calories,
                distanceKm = distance,
                hydrationMl = hydration,
                weightKg = weight
            )
            _uiState.update { it.copy(isSyncing = false, isSynced = success) }
        }
    }

    fun generateAiFitnessAdvice(topic: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true, aiRecommendation = null) }
            val steps = _uiState.value.stepsCount
            val avgBpm = _uiState.value.averageHeartRate
            val lastBpm = _uiState.value.latestHeartRate
            val sleepMins = _uiState.value.sleepDurationMinutes
            val calories = _uiState.value.caloriesBurned
            val distance = _uiState.value.distanceKm
            val hydration = _uiState.value.hydrationMl
            val weight = _uiState.value.weightKg

            val advice = networkService.fetchAiAdvice(
                steps = steps,
                avgHr = avgBpm,
                latestHr = lastBpm,
                sleepMinutes = sleepMins,
                caloriesBurned = calories,
                distanceKm = distance,
                hydrationMl = hydration,
                weightKg = weight,
                topic = topic
            )
            
            _uiState.update { 
                it.copy(
                    isAiLoading = false, 
                    aiRecommendation = advice ?: "Error: Failed to fetch advice from AWS serverless backend."
                ) 
            }
        }
    }
}
