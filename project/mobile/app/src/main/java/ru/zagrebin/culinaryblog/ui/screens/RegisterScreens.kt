package ru.zagrebin.culinaryblog.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ru.zagrebin.culinaryblog.viewmodel.AuthViewModel
import ru.zagrebin.culinaryblog.viewmodel.UiState

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onRegisterSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val username by viewModel.regUsername.collectAsState()
    val email by viewModel.regEmail.collectAsState()
    val password by viewModel.regPassword.collectAsState()
    val confirm by viewModel.regConfirmPassword.collectAsState()
    val state by viewModel.registerState.collectAsState()

    val scaffoldState = rememberScaffoldState()
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (state) {
            is UiState.Success -> onRegisterSuccess()
            is UiState.Error -> scaffoldState.snackbarHostState.showSnackbar((state as UiState.Error).message)
            else -> {}
        }
    }

    Scaffold(scaffoldState = scaffoldState) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(padding),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Регистрация", style = MaterialTheme.typography.h4)

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(value = username, onValueChange = { viewModel.regUsername.value = it }, label = { Text("Имя пользователя") }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(value = email, onValueChange = { viewModel.regEmail.value = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.regPassword.value = it },
                label = { Text("Пароль") },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val icon = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    IconButton(onClick = { showPassword = !showPassword }) { Icon(icon, contentDescription = null) }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = confirm,
                onValueChange = { viewModel.regConfirmPassword.value = it },
                label = { Text("Подтвердите пароль") },
                visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val icon = if (showConfirm) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    IconButton(onClick = { showConfirm = !showConfirm }) { Icon(icon, contentDescription = null) }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(onClick = { viewModel.register() }, modifier = Modifier.fillMaxWidth(), enabled = state !is UiState.Loading) {
                if (state is UiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Регистрация...")
                } else {
                    Text("Зарегистрироваться")
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onNavigateBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Уже есть аккаунт? Войти")
            }
        }
    }
}
