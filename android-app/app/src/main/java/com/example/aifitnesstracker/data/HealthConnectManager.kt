package com.example.aifitnesstracker.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
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

    // Set of permissions requested for our AI fitness tracking
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
        HealthPermission.getWritePermission(WeightRecord::class)
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

    // Read cumulative steps count aggregated between a specific start and end time
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

    // Read raw heart rate samples and extract beats per minute list
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

    // Read sleep session records and aggregate total sleep duration in minutes
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

    // Read active calories burned aggregated in kcal
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

    // Read distance aggregated in kilometers
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

    // Read hydration aggregated in milliliters
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

    // Read latest weight entry in kilograms
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
