package com.example.aifitnesstracker.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
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

    // Set of permissions requested for our AI fitness tracking (Steps & Heart Rate)
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class)
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
}
