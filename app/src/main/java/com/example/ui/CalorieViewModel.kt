package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.CalorieAiService
import com.example.data.model.FoodAnalysisResult
import com.example.data.model.MealLog
import com.example.data.model.UserProfile
import com.example.data.repository.MealRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val TAG = "CalorieViewModel"

data class DailyNutritionSummary(
    val totalCalories: Long = 0L,
    val totalProtein: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val totalFat: Double = 0.0,
    val calorieGoal: Long = 2000L,
    val proteinGoal: Long = 130L,
    val carbsGoal: Long = 220L,
    val fatGoal: Long = 65L,
    val remainingCalories: Long = 2000L
)

class CalorieViewModel(
    private val userId: String,
    private val userRepository: UserRepository,
    private val mealRepository: MealRepository,
    private val aiService: CalorieAiService = CalorieAiService()
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _selectedDate = MutableStateFlow(getTodayDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Scan & Analysis states
    private val _selectedBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedBitmap: StateFlow<Bitmap?> = _selectedBitmap.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisResult = MutableStateFlow<FoodAnalysisResult?>(null)
    val analysisResult: StateFlow<FoodAnalysisResult?> = _analysisResult.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Observe user profile
    val userProfile: StateFlow<UserProfile> = userRepository.observeProfile(userId)
        .map { it ?: UserProfile(userId = userId) }
        .catch { e ->
            Log.e(TAG, "Error observing profile", e)
            emit(UserProfile(userId = userId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserProfile(userId = userId)
        )

    // Observe meals for currently selected date
    val dailyMeals: StateFlow<List<MealLog>> = _selectedDate
        .flatMapLatest { date ->
            mealRepository.observeMeals(userId, date)
        }
        .catch { e ->
            Log.e(TAG, "Error observing meals", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // Combined summary
    val nutritionSummary: StateFlow<DailyNutritionSummary> = combine(
        dailyMeals,
        userProfile
    ) { meals, profile ->
        val totalCalories = meals.sumOf { it.calories }
        val totalProtein = meals.sumOf { it.proteinGrams }
        val totalCarbs = meals.sumOf { it.carbsGrams }
        val totalFat = meals.sumOf { it.fatGrams }
        val calGoal = if (profile.dailyCalorieGoal > 0) profile.dailyCalorieGoal else 2000L
        val protGoal = if (profile.dailyProteinGoal > 0) profile.dailyProteinGoal else 130L
        val carbGoal = if (profile.dailyCarbsGoal > 0) profile.dailyCarbsGoal else 220L
        val fGoal = if (profile.dailyFatGoal > 0) profile.dailyFatGoal else 65L
        val remaining = (calGoal - totalCalories).coerceAtLeast(0L)

        DailyNutritionSummary(
            totalCalories = totalCalories,
            totalProtein = totalProtein,
            totalCarbs = totalCarbs,
            totalFat = totalFat,
            calorieGoal = calGoal,
            proteinGoal = protGoal,
            carbsGoal = carbGoal,
            fatGoal = fGoal,
            remainingCalories = remaining
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DailyNutritionSummary()
    )

    init {
        // Initialize user profile in Firestore if not already present
        viewModelScope.launch {
            try {
                userRepository.saveOrUpdateProfile()
            } catch (e: Exception) {
                Log.w(TAG, "Init profile check: ${e.message}")
            }
        }
    }

    private fun getTodayDateString(): String = dateFormat.format(Date())

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    fun stepDay(offset: Int) {
        try {
            val cal = Calendar.getInstance()
            val parsed = dateFormat.parse(_selectedDate.value) ?: Date()
            cal.time = parsed
            cal.add(Calendar.DAY_OF_YEAR, offset)
            _selectedDate.value = dateFormat.format(cal.time)
        } catch (e: Exception) {
            _selectedDate.value = getTodayDateString()
        }
    }

    fun resetToToday() {
        _selectedDate.value = getTodayDateString()
    }

    fun onPhotoSelected(bitmap: Bitmap) {
        _selectedBitmap.value = bitmap
        _analysisResult.value = null
        _analysisError.value = null
    }

    fun clearPhotoSelection() {
        _selectedBitmap.value = null
        _analysisResult.value = null
        _analysisError.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun analyzePhoto(userNotes: String? = null) {
        val bitmap = _selectedBitmap.value ?: return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null
            try {
                val result = aiService.analyzeFoodImage(bitmap, userNotes)
                _analysisResult.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed", e)
                _analysisError.value = e.localizedMessage ?: "Failed to analyze photo"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun saveAnalyzedMeal(
        name: String,
        mealType: String,
        calories: Long,
        proteinGrams: Double,
        carbsGrams: Double,
        fatGrams: Double,
        healthScore: Long,
        analysisSummary: String,
        ingredients: List<String>
    ) {
        viewModelScope.launch {
            try {
                val meal = MealLog(
                    name = name.ifBlank { "Scanned Meal" },
                    mealType = mealType,
                    calories = calories,
                    proteinGrams = proteinGrams,
                    carbsGrams = carbsGrams,
                    fatGrams = fatGrams,
                    healthScore = healthScore,
                    analysisSummary = analysisSummary,
                    ingredients = ingredients,
                    loggedDate = _selectedDate.value
                )
                mealRepository.addMeal(meal)
                _statusMessage.value = "Successfully logged $name (${calories} kcal)!"
                clearPhotoSelection()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save meal", e)
                _analysisError.value = e.localizedMessage ?: "Failed to log meal"
            }
        }
    }

    fun addManualMeal(
        name: String,
        mealType: String,
        calories: Long,
        proteinGrams: Double,
        carbsGrams: Double,
        fatGrams: Double
    ) {
        viewModelScope.launch {
            try {
                val meal = MealLog(
                    name = name.ifBlank { "Manual Entry" },
                    mealType = mealType,
                    calories = calories,
                    proteinGrams = proteinGrams,
                    carbsGrams = carbsGrams,
                    fatGrams = fatGrams,
                    healthScore = 80L,
                    analysisSummary = "Manually logged entry",
                    ingredients = emptyList(),
                    loggedDate = _selectedDate.value
                )
                mealRepository.addMeal(meal)
                _statusMessage.value = "Added $name ($calories kcal) to your daily log."
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add manual meal", e)
                _statusMessage.value = "Error adding meal: ${e.message}"
            }
        }
    }

    fun deleteMeal(mealId: String) {
        viewModelScope.launch {
            try {
                mealRepository.deleteMeal(mealId)
                _statusMessage.value = "Meal deleted"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete meal", e)
                _statusMessage.value = "Failed to delete: ${e.message}"
            }
        }
    }

    fun updateGoals(calorieGoal: Long, proteinGoal: Long, carbsGoal: Long, fatGoal: Long) {
        viewModelScope.launch {
            try {
                userRepository.saveOrUpdateProfile(
                    dailyCalorieGoal = calorieGoal,
                    dailyProteinGoal = proteinGoal,
                    dailyCarbsGoal = carbsGoal,
                    dailyFatGoal = fatGoal
                )
                _statusMessage.value = "Daily nutrition targets updated!"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update goals", e)
                _statusMessage.value = "Failed to update goals: ${e.message}"
            }
        }
    }

    class Factory(
        private val context: Context,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val userRepo = UserRepository(context)
            val mealRepo = MealRepository(context)
            return CalorieViewModel(userId, userRepo, mealRepo) as T
        }
    }
}
