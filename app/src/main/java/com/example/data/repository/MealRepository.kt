package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.MealLog
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MealRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing meal logs.")
    }

    fun observeMeals(userId: String, date: String): Flow<List<MealLog>> = flow {
        val path = "users/$userId/meals"
        val query = db.collection("users").document(userId).collection("meals")
            .whereEqualTo("userId", userId)
            .whereEqualTo("loggedDate", date)
            .orderBy("createdAt", Query.Direction.DESCENDING)

        query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(MealLog::class.java)
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun addMeal(meal: MealLog): String {
        val uid = requireUserId()
        val mealsCollection = db.collection("users").document(uid).collection("meals")
        val path = mealsCollection.path

        try {
            val payload = meal.toMap() + mapOf(
                "userId" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val docRef = mealsCollection.document()
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            throw e
        }
    }

    suspend fun deleteMeal(mealId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("meals").document(mealId)
        val path = docRef.path

        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }
}
