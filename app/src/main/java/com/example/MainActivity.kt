package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AuthScreen
import com.example.ui.AuthUiState
import com.example.ui.AuthViewModel
import com.example.ui.CalorieViewModel
import com.example.ui.DailyDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(authViewModel = authViewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(authViewModel: AuthViewModel) {
    val context = LocalContext.current
    val authState by authViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        val credentialManager = CredentialManager.create(context)
        authViewModel.attemptSilentAutoSignIn(context, credentialManager)
    }

    when (val state = authState) {
        is AuthUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        is AuthUiState.Unauthenticated -> {
            AuthScreen(
                isLoading = false,
                errorMessage = state.errorMessage,
                onSignInClicked = { credentialManager ->
                    authViewModel.signInWithGoogle(context, credentialManager)
                }
            )
        }
        is AuthUiState.Authenticated -> {
            val calorieViewModel: CalorieViewModel = viewModel(
                factory = CalorieViewModel.Factory(context, state.user.uid)
            )

            DailyDashboardScreen(
                currentUser = state.user,
                viewModel = calorieViewModel,
                onSignOutClicked = {
                    val credentialManager = CredentialManager.create(context)
                    authViewModel.signOut(context, credentialManager)
                }
            )
        }
    }
}
