package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.FoodAnalysisResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "CalorieAiService"

class CalorieAiService {

    private val foodAnalysisSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "foodName" to Schema.string(description = "Accurate name of the food, dish, or beverage"),
                "mealType" to Schema.enumeration(
                    values = listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK", "DRINK"),
                    description = "Most suitable meal category"
                ),
                "calories" to Schema.integer(description = "Estimated total calories in kcal based on the visible portion"),
                "proteinGrams" to Schema.double(description = "Estimated protein in grams"),
                "carbsGrams" to Schema.double(description = "Estimated carbohydrates in grams"),
                "fatGrams" to Schema.double(description = "Estimated total fat in grams"),
                "healthScore" to Schema.integer(description = "Overall nutrition density score from 1 to 100"),
                "analysisSummary" to Schema.string(description = "Detailed rationale covering detected ingredients, estimated portion weight/volume, and nutrition insights"),
                "ingredients" to Schema.array(
                    items = Schema.string(),
                    description = "Detected ingredients with estimated quantities"
                )
            ),
            optionalProperties = emptyList()
        )
    }

    private val generativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = foodAnalysisSchema
            }
        )
    }

    suspend fun analyzeFoodImage(
        bitmap: Bitmap,
        userNotes: String? = null
    ): FoodAnalysisResult = withContext(Dispatchers.IO) {
        val promptText = buildString {
            append("You are an expert clinical dietitian and food calorie calculator. ")
            append("Analyze this food or drink photograph carefully. Identify all items, estimate total portion size, calculate calories (kcal), macronutrients (protein, carbs, fat in grams), health score (1-100), ingredients list with estimated amounts, and provide a clear, concise analysis summary.")
            if (!userNotes.isNullOrBlank()) {
                append("\nUser context / notes: ").append(userNotes)
            }
        }

        try {
            val response = withTimeout(60_000L) {
                generativeModel.generateContent(
                    content {
                        image(bitmap)
                        text(promptText)
                    }
                )
            }

            val jsonString = response.text
                ?: throw IllegalStateException("Gemini returned an empty response.")

            Log.d(TAG, "Raw Gemini JSON: $jsonString")
            val json = JSONObject(jsonString)

            val foodName = json.optString("foodName", "Unknown Meal")
            val mealType = json.optString("mealType", "LUNCH")
            val calories = json.optLong("calories", 0L)
            val proteinGrams = json.optDouble("proteinGrams", 0.0)
            val carbsGrams = json.optDouble("carbsGrams", 0.0)
            val fatGrams = json.optDouble("fatGrams", 0.0)
            val healthScore = json.optLong("healthScore", 75L).coerceIn(1L, 100L)
            val analysisSummary = json.optString("analysisSummary", "Nutritious meal analyzed by CaloSnap.")

            val ingredientsArray = json.optJSONArray("ingredients")
            val ingredientsList = mutableListOf<String>()
            if (ingredientsArray != null) {
                for (i in 0 until ingredientsArray.length()) {
                    val item = ingredientsArray.optString(i)
                    if (item.isNotBlank()) {
                        ingredientsList.add(item)
                    }
                }
            }

            FoodAnalysisResult(
                foodName = foodName,
                mealType = mealType,
                calories = calories,
                proteinGrams = proteinGrams,
                carbsGrams = carbsGrams,
                fatGrams = fatGrams,
                healthScore = healthScore,
                analysisSummary = analysisSummary,
                ingredients = ingredientsList
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Analysis timed out", e)
            throw IllegalStateException("Food analysis timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini food analysis failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to analyze food photo.")
        }
    }
}
