package ru.zagrebin.culinaryblog.data.remote.dto

data class AuthRequest(val username: String, val password: String)
data class RegisterRequest(val username: String, val email: String, val password: String)

data class AuthResponse(val accessToken: String, val refreshToken: String? = null, val expiresIn: Long? = null)

data class ErrorResponse(val message: String?)