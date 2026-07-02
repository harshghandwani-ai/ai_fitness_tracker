package com.example.aifitnesstracker.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

class HealthConnectManager(private val context: Context) {
    
    // Check Health Connect SDK status on the device
    val availabilityStatus: Int
        get() = HealthConnectClient.getSdkStatus(context)

    // Lazy load the HealthConnectClient instance if the SDK is available
    val healthConnectClient: HealthConnectClient? by lazy {
        if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    // Set of permissions requested for our AI fitness tracking (all 11 dimensions)
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getWritePermission(SpeedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getWritePermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getWritePermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SkinTemperatureRecord::class),
        HealthPermission.getWritePermission(SkinTemperatureRecord::class)
    )

    // Verify if all required Health Connect permissions are granted
    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(permissions)
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error checking permissions status", e)
            false
        }
    }

    // 1. Steps
    suspend fun readDailySteps(startTime: Instant, endTime: Instant): Long {
        val client = healthConnectClient ?: return 0L
        if (!hasAllPermissions()) return 0L
        return try {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error aggregating steps count", e)
            0L
        }
    }

    // 2. Distance
    suspend fun readDistance(startTime: Instant, endTime: Instant): Double {
        val client = healthConnectClient ?: return 0.0
        if (!hasAllPermissions()) return 0.0
        return try {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val meters = response[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
            meters / 1000.0
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error aggregating distance", e)
            0.0
        }
    }

    // 3. Speed
    suspend fun readLatestSpeed(startTime: Instant, endTime: Instant): Double {
        val client = healthConnectClient ?: return 0.0
        if (!hasAllPermissions()) return 0.0
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SpeedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val samples = response.records.flatMap { it.samples }
            val latest = samples.maxByOrNull { it.time }
            val mps = latest?.speed?.inMetersPerSecond ?: 0.0
            mps * 3.6 // convert to km/h
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading speed", e)
            0.0
        }
    }

    // 4. Exercise Sessions
    suspend fun readExerciseSessionsCount(startTime: Instant, endTime: Instant): Int {
        val client = healthConnectClient ?: return 0
        if (!hasAllPermissions()) return 0
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records.size
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading exercise sessions count", e)
            0
        }
    }

    // 5. Active Calories (Retained for backwards compatibility/display)
    suspend fun readActiveCaloriesBurned(startTime: Instant, endTime: Instant): Long {
        val client = healthConnectClient ?: return 0L
        if (!hasAllPermissions()) return 0L
        return try {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toLong() ?: 0L
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error aggregating active calories", e)
            0L
        }
    }

    // 5b. Total Calories Burned (Active + Resting)
    suspend fun readTotalCaloriesBurned(startTime: Instant, endTime: Instant): Long {
        val client = healthConnectClient ?: return 0L
        if (!hasAllPermissions()) return 0L
        return try {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.toLong() ?: 0L
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error aggregating total calories", e)
            0L
        }
    }

    // 6. Sleep Duration
    suspend fun readSleepDuration(startTime: Instant, endTime: Instant): Long {
        val client = healthConnectClient ?: return 0L
        if (!hasAllPermissions()) return 0L
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records.sumOf { record ->
                java.time.Duration.between(record.startTime, record.endTime).toMinutes()
            }
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading sleep session records", e)
            0L
        }
    }

    // 7. Heart Rate Samples
    suspend fun readHeartRate(startTime: Instant, endTime: Instant): List<Int> {
        val client = healthConnectClient ?: return emptyList()
        if (!hasAllPermissions()) return emptyList()
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
                .flatMap { record -> record.samples }
                .sortedBy { it.time }
                .map { it.beatsPerMinute.toInt() }
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading heart rate records", e)
            emptyList()
        }
    }

    // 8. HRV (Heart Rate Variability RMSSD in milliseconds)
    suspend fun readLatestHrv(startTime: Instant, endTime: Instant): Double {
        val client = healthConnectClient ?: return 0.0
        if (!hasAllPermissions()) return 0.0
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateVariabilityRmssdRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val latest = response.records.maxByOrNull { it.time }
            latest?.heartRateVariabilityMillis ?: 0.0
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading HRV", e)
            0.0
        }
    }

    // 9. Respiratory Rate
    suspend fun readLatestRespiratoryRate(startTime: Instant, endTime: Instant): Double {
        val client = healthConnectClient ?: return 0.0
        if (!hasAllPermissions()) return 0.0
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = RespiratoryRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val latest = response.records.maxByOrNull { it.time }
            latest?.rate ?: 0.0
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading respiratory rate", e)
            0.0
        }
    }

    // 10. Resting Heart Rate
    suspend fun readLatestRestingHeartRate(startTime: Instant, endTime: Instant): Int {
        val client = healthConnectClient ?: return 0
        if (!hasAllPermissions()) return 0
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val latest = response.records.maxByOrNull { it.time }
            (latest?.beatsPerMinute ?: 0L).toInt()
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading resting heart rate", e)
            0
        }
    }

    // 11. Skin Temperature
    suspend fun readLatestSkinTemperature(startTime: Instant, endTime: Instant): Double {
        val client = healthConnectClient ?: return 0.0
        if (!hasAllPermissions()) return 0.0
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SkinTemperatureRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val latest = response.records.maxByOrNull { it.startTime }
            val temp = latest?.baseline?.inCelsius
            if (temp != null && temp > 0.0) {
                temp
            } else {
                latest?.deltas?.firstOrNull()?.delta?.inCelsius ?: 0.0
            }
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading skin temperature", e)
            0.0
        }
    }

    // Other core metrics: Hydration & Weight
    suspend fun readHydration(startTime: Instant, endTime: Instant): Double {
        val client = healthConnectClient ?: return 0.0
        if (!hasAllPermissions()) return 0.0
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HydrationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records.sumOf { it.volume.inMilliliters }
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading hydration", e)
            0.0
        }
    }

    suspend fun readLatestWeight(startTime: Instant, endTime: Instant): Double {
        val client = healthConnectClient ?: return 0.0
        if (!hasAllPermissions()) return 0.0
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val latest = response.records.maxByOrNull { it.time }
            latest?.weight?.inKilograms ?: 0.0
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading weight", e)
            0.0
        }
    }
}
