package com.example.aifitnesstracker

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.aifitnesstracker.data.AuthManager
import com.example.aifitnesstracker.ui.login.LoginScreen
import com.example.aifitnesstracker.ui.main.MainScreen

@Composable
fun MainNavigation() {
  val context = LocalContext.current.applicationContext
  val authManager = remember { AuthManager(context) }
  val session by authManager.session.collectAsState()

  // Pick initial screen depending on login state
  val initialRoute = if (session.isLoggedIn) Main else Login
  val backStack = rememberNavBackStack(initialRoute)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Login> {
          LoginScreen(
            authManager = authManager,
            onLoginSuccess = {
              // Switch navigation to main dashboard
              backStack.add(Main)
            }
          )
        }
        entry<Main> {
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            authManager = authManager,
            onSignOutSuccess = {
              // Redirect back to login screen
              backStack.add(Login)
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
      },
  )
}
