package com.example.lumora

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.delay

class GeminiHelper(private val apiKey: String) {

    private fun createModel(modelName: String): GenerativeModel {
        return GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.4f
                maxOutputTokens = 500
            }
        )
    }

    suspend fun analyzeImage(bitmap: Bitmap, prompt: String): String {
        val modelNames = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash"
        )

        for (modelName in modelNames) {
            val model = createModel(modelName)

            repeat(3) { attempt ->
                try {
                    val inputContent = content {
                        image(bitmap)
                        text(prompt)
                    }

                    val response = model.generateContent(inputContent)
                    val result = response.text?.trim()

                    if (!result.isNullOrEmpty()) {
                        return result
                    } else {
                        return "Could not generate a description."
                    }
                } catch (e: Exception) {
                    val msg = e.message?.lowercase() ?: ""

                    val retryable =
                        "high demand" in msg ||
                                "resource exhausted" in msg ||
                                "quota" in msg ||
                                "503" in msg ||
                                "429" in msg ||
                                "unavailable" in msg

                    if (retryable && attempt < 2) {
                        delay((attempt + 1) * 2000L)
                    } else {
                        return@repeat
                    }
                }
            }
        }

        return "AI service is busy right now. Please try again in a moment."
    }
}