package com.example.aifitnesstracker.ui.main

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
    @Test
    fun uiState_initialState() = runTest {
        val state = DashboardUiState()
        assertEquals(state.stepsCount, 0L)
        assertEquals(state.isAiLoading, false)
        assertEquals(state.hasHealthPermissions, false)
        assertEquals(state.aiRecommendation, null)
    }
}
