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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import ru.zagrebin.culinaryblog.ui.screens.LoginScreen
import ru.zagrebin.culinaryblog.ui.screens.MainScreen
import ru.zagrebin.culinaryblog.ui.screens.RegisterScreen

import ru.zagrebin.culinaryblog.ui.theme.CulinaryBlogTheme
import ru.zagrebin.culinaryblog.viewmodel.AuthViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Опционально: enable edge-to-edge if нужно
        enableEdgeToEdge()
        setContent {
            CulinaryBlogTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    // Главный экран — список карточек постов
                    composable("main") {
                        MainScreen(onPostClick = { id ->
                            navController.navigate("post/$id")
                        })
                    }

                    // Экран полного поста (пока заглушка)
                    composable("post/{id}") { backStackEntry ->
                        val idStr = backStackEntry.arguments?.getString("id")
                        androidx.compose.material.Text(
                            text = "Post full screen for id = $idStr",
                            modifier = Modifier.fillMaxSize().padding(16.dp)
                        )
                    }

                    // Логин / Регистрация — остаются доступными при необходимости
                    composable("login") {
                        val vm: AuthViewModel = hiltViewModel()
                        LoginScreen(
                            vm,
                            onNavigateToRegister = { navController.navigate("register") },
                            onLoginSuccess = {
                                navController.navigate("main") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("register") {
                        val vm: AuthViewModel = hiltViewModel()
                        RegisterScreen(
                            vm,
                            onRegisterSuccess = {
                                navController.navigate("main") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
