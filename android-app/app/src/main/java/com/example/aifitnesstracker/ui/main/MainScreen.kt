package com.example.aifitnesstracker.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.aifitnesstracker.data.NetworkService
import com.example.aifitnesstracker.data.HealthConnectManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Premium Harmonious Color Palette
val DarkBg = Color(0xFF0F172A)
val CardBg = Color(0xFF1E293B)
val PrimaryPurple = Color(0xFF6366F1)
val SecondaryIndigo = Color(0xFF4F46E5)
val AccentGreen = Color(0xFF10B981)
val LightText = Color(0xFFF8FAFC)
val MutedText = Color(0xFF94A3B8)

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val viewModel: MainScreenViewModel = viewModel {
        MainScreenViewModel(
            healthConnectManager = HealthConnectManager(context),
            networkService = NetworkService()
        )
    }
    val state by viewModel.uiState.collectAsState()

    // Set of permissions to request (all health dimensions)
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

    // Launcher for Health Connect permission requests
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.checkHealthConnectStatus()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Header Section with Cloud Sync Indicator
            HeaderSection(
                onRefreshSteps = { viewModel.fetchTodayHealthData() },
                hasPermissions = state.hasHealthPermissions,
                isSyncing = state.isSyncing,
                isSynced = state.isSynced
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Activity Card: Steps & Distance
            StepsProgressCard(
                steps = state.stepsCount,
                distanceKm = state.distanceKm,
                hasPermissions = state.hasHealthPermissions,
                isAvailable = state.isHealthConnectAvailable,
                onSyncClick = {
                    requestPermissionsLauncher.launch(permissions)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Row 2: Heart Rate & Sleep (2 Columns)
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    HeartRateCard(
                        avgBpm = state.averageHeartRate,
                        lastBpm = state.latestHeartRate,
                        hasPermissions = state.hasHealthPermissions,
                        isAvailable = state.isHealthConnectAvailable,
                        onSyncClick = { requestPermissionsLauncher.launch(permissions) }
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    SleepCard(
                        sleepDurationMinutes = state.sleepDurationMinutes,
                        hasPermissions = state.hasHealthPermissions,
                        isAvailable = state.isHealthConnectAvailable,
                        onSyncClick = { requestPermissionsLauncher.launch(permissions) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Row 3: Active Calories & Hydration (2 Columns)
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    CaloriesCard(
                        caloriesKcal = state.caloriesBurned,
                        hasPermissions = state.hasHealthPermissions,
                        isAvailable = state.isHealthConnectAvailable,
                        onSyncClick = { requestPermissionsLauncher.launch(permissions) }
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    HydrationCard(
                        hydrationMl = state.hydrationMl,
                        hasPermissions = state.hasHealthPermissions,
                        isAvailable = state.isHealthConnectAvailable,
                        onSyncClick = { requestPermissionsLauncher.launch(permissions) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Row 4: Weight Card (Full Width)
            WeightCard(
                weightKg = state.weightKg,
                hasPermissions = state.hasHealthPermissions,
                isAvailable = state.isHealthConnectAvailable,
                onSyncClick = { requestPermissionsLauncher.launch(permissions) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // AI Coach Advice Panel
            AICoachSection(
                aiRecommendation = state.aiRecommendation,
                isAiLoading = state.isAiLoading,
                onAskCoach = { topic -> viewModel.generateAiFitnessAdvice(topic) }
            )
        }
    }
}

@Composable
fun HeaderSection(
    onRefreshSteps: () -> Unit,
    hasPermissions: Boolean,
    isSyncing: Boolean,
    isSynced: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Welcome Back!",
                    color = LightText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isSyncing) {
                    Text(text = "☁️🔄", fontSize = 14.sp)
                } else if (isSynced) {
                    Text(text = "☁️✅", fontSize = 14.sp)
                }
            }
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                color = MutedText,
                fontSize = 14.sp
            )
        }
        if (hasPermissions) {
            Button(
                onClick = onRefreshSteps,
                colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
            ) {
                Text(
                    text = "🔄",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StepsProgressCard(
    steps: Long,
    distanceKm: Double,
    hasPermissions: Boolean,
    isAvailable: Boolean,
    onSyncClick: () -> Unit
) {
    val targetSteps = 10000L
    val progress = if (steps >= targetSteps) 1f else steps.toFloat() / targetSteps.toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "👣",
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Daily Activity",
                        color = LightText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (hasPermissions) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Synced",
                            color = AccentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!isAvailable) {
                Text(
                    text = "Health Connect is not supported on this device.",
                    color = MutedText,
                    fontSize = 14.sp
                )
            } else if (!hasPermissions) {
                Text(
                    text = "Sync Health Connect to display steps and distance.",
                    color = MutedText,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onSyncClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Sync Health Connect", color = LightText, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = String.format("%,d steps", steps),
                            color = LightText,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = String.format("📏 %.2f km traveled", distanceKm),
                            color = MutedText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        text = String.format("%.0f%%", progress * 100),
                        color = PrimaryPurple,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = PrimaryPurple,
                    trackColor = DarkBg
                )
            }
        }
    }
}

@Composable
fun HeartRateCard(
    avgBpm: Int,
    lastBpm: Int,
    hasPermissions: Boolean,
    isAvailable: Boolean,
    onSyncClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "❤️ Heart Rate",
                color = LightText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermissions || !isAvailable) {
                Text(text = "-- BPM", color = MutedText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    text = if (lastBpm > 0) "$lastBpm BPM" else "-- BPM",
                    color = LightText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (avgBpm > 0) "Avg: $avgBpm BPM" else "Avg: --",
                    color = AccentGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SleepCard(
    sleepDurationMinutes: Long,
    hasPermissions: Boolean,
    isAvailable: Boolean,
    onSyncClick: () -> Unit
) {
    val hours = sleepDurationMinutes / 60
    val minutes = sleepDurationMinutes % 60
    
    val statusText = when {
        sleepDurationMinutes >= 480 -> "Optimal"
        sleepDurationMinutes >= 360 -> "Moderate"
        sleepDurationMinutes > 0 -> "Low"
        else -> "--"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "😴 Sleep Rest",
                color = LightText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermissions || !isAvailable) {
                Text(text = "--h --m", color = MutedText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    text = if (sleepDurationMinutes > 0) "${hours}h ${minutes}m" else "--h --m",
                    color = LightText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Rest: $statusText",
                    color = PrimaryPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CaloriesCard(
    caloriesKcal: Long,
    hasPermissions: Boolean,
    isAvailable: Boolean,
    onSyncClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "🔥 Calories",
                color = LightText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermissions || !isAvailable) {
                Text(text = "-- kcal", color = MutedText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    text = "$caloriesKcal kcal",
                    color = LightText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Active Burn",
                    color = MutedText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun HydrationCard(
    hydrationMl: Double,
    hasPermissions: Boolean,
    isAvailable: Boolean,
    onSyncClick: () -> Unit
) {
    val dailyTarget = 2500.0
    val progress = if (hydrationMl >= dailyTarget) 1f else (hydrationMl / dailyTarget).toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "💧 Hydration",
                color = LightText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermissions || !isAvailable) {
                Text(text = "-- ml", color = MutedText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    text = String.format("%.0f ml", hydrationMl),
                    color = LightText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = PrimaryPurple,
                    trackColor = DarkBg
                )
            }
        }
    }
}

@Composable
fun WeightCard(
    weightKg: Double,
    hasPermissions: Boolean,
    isAvailable: Boolean,
    onSyncClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⚖️",
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Body Weight Profile",
                    color = LightText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = if (weightKg > 0) String.format("%.1f kg", weightKg) else "--.- kg",
                color = AccentGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AICoachSection(
    aiRecommendation: String?,
    isAiLoading: Boolean,
    onAskCoach: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "AI Fitness Coach",
            color = LightText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Topic chips row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TopicChip(label = "Daily Advice", onClick = { onAskCoach("steps") })
            TopicChip(label = "10m Workout", onClick = { onAskCoach("workout") })
            TopicChip(label = "Nutrition", onClick = { onAskCoach("nutrition") })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Coach response card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isAiLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(color = PrimaryPurple)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Consulting AI Coach...", color = MutedText, fontSize = 14.sp)
                    }
                } else if (aiRecommendation != null) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "✨",
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Coach Recommendation",
                                color = LightText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = aiRecommendation,
                            color = LightText,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                } else {
                    Text(
                        text = "Select an option above to generate a health workout schedule or nutrition tip from your AI Fitness Coach.",
                        color = MutedText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TopicChip(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryIndigo.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Text(
            text = label,
            color = LightText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
