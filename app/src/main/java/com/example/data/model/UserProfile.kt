package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val dailyCalorieGoal: Long = 2000L,
    val dailyProteinGoal: Long = 130L,
    val dailyCarbsGoal: Long = 220L,
    val dailyFatGoal: Long = 65L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "email" to email,
        "displayName" to displayName,
        "dailyCalorieGoal" to dailyCalorieGoal,
        "dailyProteinGoal" to dailyProteinGoal,
        "dailyCarbsGoal" to dailyCarbsGoal,
        "dailyFatGoal" to dailyFatGoal,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    ).filterValues { it != null }
}
