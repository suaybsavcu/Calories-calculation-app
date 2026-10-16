package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.UserProfile
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing profile.")
    }

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        val docRef = db.collection("users").document(userId)
        docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.GET, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun saveOrUpdateProfile(
        dailyCalorieGoal: Long = 2000L,
        dailyProteinGoal: Long = 130L,
        dailyCarbsGoal: Long = 220L,
        dailyFatGoal: Long = 65L
    ) {
        val uid = requireUserId()
        val user = auth.currentUser
        val path = "users/$uid"
        val docRef = db.collection("users").document(uid)

        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val newProfile = mapOf(
                    "userId" to uid,
                    "email" to user?.email,
                    "displayName" to user?.displayName,
                    "dailyCalorieGoal" to dailyCalorieGoal,
                    "dailyProteinGoal" to dailyProteinGoal,
                    "dailyCarbsGoal" to dailyCarbsGoal,
                    "dailyFatGoal" to dailyFatGoal,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(newProfile).await()
            } else {
                val updates = mapOf(
                    "email" to user?.email,
                    "displayName" to user?.displayName,
                    "dailyCalorieGoal" to dailyCalorieGoal,
                    "dailyProteinGoal" to dailyProteinGoal,
                    "dailyCarbsGoal" to dailyCarbsGoal,
                    "dailyFatGoal" to dailyFatGoal,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            throw e
        }
    }
}
