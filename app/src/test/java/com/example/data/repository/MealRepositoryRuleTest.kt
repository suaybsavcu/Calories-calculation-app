package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.MealLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun addMeal_authenticatedUser_createsDocumentAndReturnsId(): Unit = runBlocking {
    val uid = signInTestUser("alice_rule@test.com")
    val repository = MealRepository(firestore)

    val meal = MealLog(
      name = "Greek Yogurt & Berries",
      mealType = "BREAKFAST",
      calories = 240,
      proteinGrams = 18.0,
      carbsGrams = 30.0,
      fatGrams = 4.0,
      healthScore = 95,
      analysisSummary = "High protein Greek yogurt with fresh berries.",
      ingredients = listOf("Greek Yogurt 150g", "Blueberries 50g"),
      loggedDate = "2026-10-16"
    )

    val mealId = withTimeout(5000L) {
      repository.addMeal(meal)
    }

    assertNotNull(mealId)
    assertTrue(mealId.isNotBlank())
  }

  @Test
  fun observeMeals_authenticatedOwner_returnsMatchingMeals(): Unit = runBlocking {
    val uid = signInTestUser("bob_rule@test.com")
    val repository = MealRepository(firestore)

    val meal = MealLog(
      name = "Grilled Chicken Wrap",
      mealType = "LUNCH",
      calories = 450,
      proteinGrams = 35.0,
      carbsGrams = 42.0,
      fatGrams = 12.0,
      healthScore = 85,
      analysisSummary = "Lean grilled chicken wrap with lettuce.",
      ingredients = listOf("Tortilla wrap", "Chicken breast 120g", "Lettuce"),
      loggedDate = "2026-10-16"
    )

    val mealId = withTimeout(5000L) {
      repository.addMeal(meal)
    }

    val meals = withTimeout(5000L) {
      repository.observeMeals(uid, "2026-10-16").first { list -> list.any { it.id == mealId } }
    }

    assertTrue(meals.any { it.id == mealId })
    val fetched = meals.first { it.id == mealId }
    assertEquals("Grilled Chicken Wrap", fetched.name)
    assertEquals(450L, fetched.calories)
  }

  @Test(expected = IllegalStateException::class)
  fun addMeal_unauthenticatedUser_failsFast(): Unit = runBlocking {
    auth.signOut()
    val repository = MealRepository(firestore)

    val meal = MealLog(
      name = "Unauth Food",
      calories = 100,
      loggedDate = "2026-10-16"
    )

    repository.addMeal(meal)
  }
}
