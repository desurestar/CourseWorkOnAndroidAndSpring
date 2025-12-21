package ru.zagrebin.culinaryblog.data.remote.api

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import ru.zagrebin.culinaryblog.data.remote.dto.SubscriptionDto
import ru.zagrebin.culinaryblog.data.remote.dto.UserDto

interface UserApi {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Long): Response<UserDto>

    @GET("users/{id}/subscription")
    suspend fun getSubscription(@Path("id") id: Long): Response<SubscriptionDto>

    @POST("users/{id}/subscribe")
    suspend fun subscribe(@Path("id") id: Long): Response<SubscriptionDto>

    @DELETE("users/{id}/subscribe")
    suspend fun unsubscribe(@Path("id") id: Long): Response<SubscriptionDto>
}
