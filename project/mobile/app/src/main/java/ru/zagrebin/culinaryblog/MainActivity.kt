package ru.zagrebin.culinaryblog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import ru.zagrebin.culinaryblog.ui.screens.LoginScreen
import ru.zagrebin.culinaryblog.ui.screens.RegisterScreen
import ru.zagrebin.culinaryblog.ui.theme.CulinaryBlogTheme
import ru.zagrebin.culinaryblog.viewmodel.AuthViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "login") {
                    composable("login") {
                        val vm: AuthViewModel = hiltViewModel()
                        LoginScreen(vm,
                            onNavigateToRegister = { navController.navigate("register") },
                            onLoginSuccess = {
                                navController.navigate("main") {
                                    popUpTo("login") { inclusive = true }
                                }
                            })
                    }
                    composable("register") {
                        val vm: AuthViewModel = hiltViewModel()
                        RegisterScreen(vm,
                            onRegisterSuccess = {
                                navController.navigate("main") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            onNavigateBack = { navController.popBackStack() })
                    }
                    composable("main") {
                        // TODO: главный экран приложения
                        androidx.compose.material.Text("Вы вошли — главный экран")
                    }
                }
            }
        }
    }
}
