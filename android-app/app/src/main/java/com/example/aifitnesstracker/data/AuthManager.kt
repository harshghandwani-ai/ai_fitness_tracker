package com.example.aifitnesstracker.data

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.example.aifitnesstracker.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSession(
    val displayName: String = "",
    val email: String = "",
    val profilePictureUrl: String = "",
    val isLoggedIn: Boolean = false
)

class AuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)
    private val sharedPrefs = context.getSharedPreferences("user_session_prefs", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<UserSession> = _session.asStateFlow()

    private fun loadSession(): UserSession {
        val isLoggedIn = sharedPrefs.getBoolean("is_logged_in", false)
        if (!isLoggedIn) return UserSession()
        return UserSession(
            displayName = sharedPrefs.getString("display_name", "") ?: "",
            email = sharedPrefs.getString("email", "") ?: "",
            profilePictureUrl = sharedPrefs.getString("profile_picture", "") ?: "",
            isLoggedIn = true
        )
    }

    private fun saveSession(user: UserSession) {
        sharedPrefs.edit().apply {
            putBoolean("is_logged_in", user.isLoggedIn)
            putString("display_name", user.displayName)
            putString("email", user.email)
            putString("profile_picture", user.profilePictureUrl)
            apply()
        }
        _session.value = user
    }

    fun clearSession() {
        sharedPrefs.edit().clear().apply()
        _session.value = UserSession()
    }

    suspend fun signInWithGoogle(): Boolean {
        // Prepare Google ID Options
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )
            val credential = result.credential
            // Extract tokens using class validation
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val user = UserSession(
                displayName = googleIdTokenCredential.displayName ?: "User",
                email = googleIdTokenCredential.id,
                profilePictureUrl = googleIdTokenCredential.profilePictureUri?.toString() ?: "",
                isLoggedIn = true
            )
            saveSession(user)
            true
        } catch (e: Exception) {
            Log.e("AuthManager", "Google Sign-In failed: ${e.message}")
            // Graceful fallback for local development if client ID is dummy or missing
            if (BuildConfig.GOOGLE_CLIENT_ID.contains("dummy")) {
                Log.w("AuthManager", "Fallback: Performing mock developer sign-in since dummy Client ID is active")
                val mockUser = UserSession(
                    displayName = "Developer Harsh",
                    email = "harsh@example.com",
                    profilePictureUrl = "",
                    isLoggedIn = true
                )
                saveSession(mockUser)
                return true
            }
            false
        }
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e("AuthManager", "Failed to clear credential state: ${e.message}")
        }
        clearSession()
    }
}
