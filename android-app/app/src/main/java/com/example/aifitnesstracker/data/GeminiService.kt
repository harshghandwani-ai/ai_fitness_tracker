package com.example.aifitnesstracker.data

import android.util.Log
import com.example.aifitnesstracker.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel

class GeminiService {
    // Initialize the generative model using the API Key from BuildConfig
    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    suspend fun generateFitnessAdvice(prompt: String): String? {
        return try {
            val response = model.generateContent(prompt)
            response.text
        } catch (e: Exception) {
            Log.e("GeminiService", "Error generating content", e)
            "Error generating response: ${e.message}"
        }
    }
}
