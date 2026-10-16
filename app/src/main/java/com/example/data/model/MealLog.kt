package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class MealLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val mealType: String = "LUNCH", // BREAKFAST, LUNCH, DINNER, SNACK, DRINK
    val calories: Long = 0L,
    val proteinGrams: Double = 0.0,
    val carbsGrams: Double = 0.0,
    val fatGrams: Double = 0.0,
    val healthScore: Long = 80L,
    val analysisSummary: String = "",
    val ingredients: List<String> = emptyList(),
    val loggedDate: String = "", // YYYY-MM-DD
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "name" to name,
        "mealType" to mealType,
        "calories" to calories,
        "proteinGrams" to proteinGrams,
        "carbsGrams" to carbsGrams,
        "fatGrams" to fatGrams,
        "healthScore" to healthScore,
        "analysisSummary" to analysisSummary,
        "ingredients" to ingredients,
        "loggedDate" to loggedDate,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    ).filterValues { it != null }
}
