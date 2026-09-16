package com.example.coolconnect

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CoolConnectAIManager(
    private val apiKey: String = "YOUR_GEMINI_API_KEY_HERE"
) {
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    suspend fun getTechnicianFix(query: String): String = withContext(Dispatchers.IO) {
        try {
            val response = model.generateContent(content {
                text("You are an expert HVAC and electrical technician assistant. " +
                        "Provide a concise, professional diagnostic step for this issue: $query")
            })
            response.text ?: "AI could not determine a fix. Please inspect the unit manually."
        } catch (e: Exception) {
            "Diagnostic Error: ${e.localizedMessage}. Check if Gemini 1.5 Flash is enabled for your key."
        }
    }

    suspend fun getCustomerRecommendation(history: List<String>): String = withContext(Dispatchers.IO) {
        try {
            val historyString = if (history.isEmpty()) "New User" else history.joinToString(", ")
            val response = model.generateContent(content {
                text("You are a smart home advisor for 'CoolConnect'. " +
                        "Based on this user's service history: [$historyString], " +
                        "provide a single, friendly recommendation for a smart home product or maintenance service. " +
                        "Be very concise (max 2 sentences).")
            })
            response.text ?: "Stay cool with regular maintenance!"
        } catch (e: Exception) {
            "Maintain your home's health with our seasonal cleaning packages. Error: ${e.localizedMessage}"
        }
    }
}
