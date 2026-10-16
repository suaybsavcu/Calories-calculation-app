package com.example.data.model

data class FoodAnalysisResult(
    val foodName: String,
    val mealType: String,
    val calories: Long,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val healthScore: Long,
    val analysisSummary: String,
    val ingredients: List<String>
)
