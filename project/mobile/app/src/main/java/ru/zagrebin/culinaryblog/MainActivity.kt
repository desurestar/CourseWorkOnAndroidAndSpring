package ru.zagrebin.culinaryblog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import ru.zagrebin.culinaryblog.ui.screens.LoginScreen
import ru.zagrebin.culinaryblog.ui.screens.MainScreen
import ru.zagrebin.culinaryblog.ui.screens.PostDetailScreen
import ru.zagrebin.culinaryblog.ui.screens.ArticlesScreen
import ru.zagrebin.culinaryblog.ui.screens.CreatePostScreen
import ru.zagrebin.culinaryblog.ui.screens.MessengerStubScreen
import ru.zagrebin.culinaryblog.ui.screens.ProfileScreen
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
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val showBottomBar = bottomDestinations.any { dest ->
                    currentDestination?.hierarchy?.any { it.route?.startsWith(dest.route) == true } == true
                }

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                bottomDestinations.forEach { destination ->
                                    val selected = currentDestination?.hierarchy?.any { it.route?.startsWith(destination.route) == true } == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(destination.route) {
                                                popUpTo(bottomDestinations.first().route) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                                        label = { Text(destination.label) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = BottomDestination.Recipes.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(BottomDestination.Recipes.route) {
                            MainScreen(onPostClick = { id ->
                                navController.navigate("post/$id")
                            })
                        }
                        composable(BottomDestination.Articles.route) {
                            ArticlesScreen(onOpenPost = { id ->
                                navController.navigate("post/$id")
                            })
                        }
                        composable(BottomDestination.Create.route) {
                            CreatePostScreen()
                        }
                        composable(BottomDestination.Messenger.route) {
                            MessengerStubScreen()
                        }
                        composable(BottomDestination.Profile.route) {
                            ProfileScreen()
                        }
                        composable(
                            route = "post/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getLong("id") ?: -1L
                            PostDetailScreen(
                                postId = id,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // Логин / Регистрация — остаются доступными при необходимости
                        composable("login") {
                            val vm: AuthViewModel = hiltViewModel()
                            LoginScreen(
                                vm,
                                onNavigateToRegister = { navController.navigate("register") },
                                onLoginSuccess = {
                                    navController.navigate(BottomDestination.Recipes.route) {
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
                                    navController.navigate(BottomDestination.Recipes.route) {
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
}

private sealed class BottomDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object Recipes : BottomDestination("recipes", "Рецепты", Icons.Outlined.RestaurantMenu)
    data object Articles : BottomDestination("articles", "Статьи", Icons.Outlined.Article)
    data object Create : BottomDestination("create", "Создать", Icons.Rounded.AddCircle)
    data object Messenger : BottomDestination("messenger", "Мессенджер", Icons.Outlined.ChatBubbleOutline)
    data object Profile : BottomDestination("profile", "Профиль", Icons.Outlined.Person)
}

private val bottomDestinations = listOf(
    BottomDestination.Recipes,
    BottomDestination.Articles,
    BottomDestination.Create,
    BottomDestination.Messenger,
    BottomDestination.Profile
)
