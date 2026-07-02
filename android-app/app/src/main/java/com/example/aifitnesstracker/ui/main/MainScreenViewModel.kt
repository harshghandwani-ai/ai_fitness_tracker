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
    val distanceKm: Double = 0.0,
    val speedKmh: Double = 0.0,
    val exerciseSessionsCount: Int = 0,
    val activeCaloriesBurned: Long = 0,
    val totalCaloriesBurned: Long = 0,
    val sleepDurationMinutes: Long = 0,
    val averageHeartRate: Int = 0,
    val latestHeartRate: Int = 0,
    val hrvRmssdMs: Double = 0.0,
    val respiratoryRateBpm: Double = 0.0,
    val restingHeartRateBpm: Int = 0,
    val skinTempCelsius: Double = 0.0,
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
            
            // Query Distance
            val distance = healthConnectManager.readDistance(startOfDay, now)

            // Query Speed
            val speed = healthConnectManager.readLatestSpeed(startOfDay, now)

            // Query Exercise Sessions
            val exercises = healthConnectManager.readExerciseSessionsCount(startOfDay, now)

            // Query Active Calories
            val activeCalories = healthConnectManager.readActiveCaloriesBurned(startOfDay, now)

            // Query Total Calories (Active + Resting)
            val totalCalories = healthConnectManager.readTotalCaloriesBurned(startOfDay, now)

            // Query Sleep (Past 24 Hours)
            val sleepMins = healthConnectManager.readSleepDuration(past24Hours, now)
            
            // Query Heart Rate
            val heartRates = healthConnectManager.readHeartRate(startOfDay, now)
            val avgBpm = if (heartRates.isNotEmpty()) heartRates.average().toInt() else 0
            val lastBpm = if (heartRates.isNotEmpty()) heartRates.last() else 0

            // Query HRV
            val hrv = healthConnectManager.readLatestHrv(startOfDay, now)

            // Query Respiratory Rate
            val respiratory = healthConnectManager.readLatestRespiratoryRate(startOfDay, now)

            // Query Resting Heart Rate
            val restingHr = healthConnectManager.readLatestRestingHeartRate(startOfDay, now)

            // Query Skin Temperature
            val skinTemp = healthConnectManager.readLatestSkinTemperature(startOfDay, now)

            // Query Hydration
            val hydration = healthConnectManager.readHydration(startOfDay, now)

            // Query Weight
            val weight = healthConnectManager.readLatestWeight(startOfDay, now)
            
            _uiState.update { 
                it.copy(
                    stepsCount = steps,
                    distanceKm = distance,
                    speedKmh = speed,
                    exerciseSessionsCount = exercises,
                    activeCaloriesBurned = activeCalories,
                    totalCaloriesBurned = totalCalories,
                    sleepDurationMinutes = sleepMins,
                    averageHeartRate = avgBpm,
                    latestHeartRate = lastBpm,
                    hrvRmssdMs = hrv,
                    respiratoryRateBpm = respiratory,
                    restingHeartRateBpm = restingHr,
                    skinTempCelsius = skinTemp,
                    hydrationMl = hydration,
                    weightKg = weight,
                    isSynced = false
                ) 
            }

            // Sync metrics with AWS DynamoDB
            _uiState.update { it.copy(isSyncing = true) }
            val success = networkService.syncHealthData(
                steps = steps,
                distanceKm = distance,
                speedKmh = speed,
                exerciseSessionsCount = exercises,
                activeCaloriesBurned = activeCalories,
                totalCaloriesBurned = totalCalories,
                sleepMinutes = sleepMins,
                avgHr = avgBpm,
                latestHr = lastBpm,
                hrvRmssdMs = hrv,
                respiratoryRateBpm = respiratory,
                restingHeartRateBpm = restingHr,
                skinTempCelsius = skinTemp,
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
            val distance = _uiState.value.distanceKm
            val speed = _uiState.value.speedKmh
            val exercises = _uiState.value.exerciseSessionsCount
            val activeCalories = _uiState.value.activeCaloriesBurned
            val totalCalories = _uiState.value.totalCaloriesBurned
            val sleepMins = _uiState.value.sleepDurationMinutes
            val avgBpm = _uiState.value.averageHeartRate
            val lastBpm = _uiState.value.latestHeartRate
            val hrv = _uiState.value.hrvRmssdMs
            val respiratory = _uiState.value.respiratoryRateBpm
            val restingHr = _uiState.value.restingHeartRateBpm
            val skinTemp = _uiState.value.skinTempCelsius
            val hydration = _uiState.value.hydrationMl
            val weight = _uiState.value.weightKg

            val advice = networkService.fetchAiAdvice(
                steps = steps,
                distanceKm = distance,
                speedKmh = speed,
                exerciseSessionsCount = exercises,
                activeCaloriesBurned = activeCalories,
                totalCaloriesBurned = totalCalories,
                sleepMinutes = sleepMins,
                avgHr = avgBpm,
                latestHr = lastBpm,
                hrvRmssdMs = hrv,
                respiratoryRateBpm = respiratory,
                restingHeartRateBpm = restingHr,
                skinTempCelsius = skinTemp,
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
