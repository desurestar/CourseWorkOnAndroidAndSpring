package ru.zagrebin.culinaryblog.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.zagrebin.culinaryblog.data.remote.api.AuthApi
import ru.zagrebin.culinaryblog.data.remote.api.PostApi
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.data.repository.PostRepositoryImpl
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val BASE_URL = "http://192.168.4.109:8080/api/" // 10.0.2.2
    @Provides
    fun provideLogging(): HttpLoggingInterceptor {
        val l = HttpLoggingInterceptor()
        l.level = HttpLoggingInterceptor.Level.BODY
        return l
    }

    @Provides
    fun provideAuthInreceptor(tokenStorage: TokenStorage): Interceptor = Interceptor { chain ->
        val req = chain.request()
        val token = tokenStorage.getToken() // синхронно возвращаем строку или null
        val newReq = token?.let {
            req.newBuilder().addHeader("Authorization", "Bearer $it").build()
        } ?: req
        chain.proceed(newReq)
    }

    @Provides
    @Singleton
    fun provideOkHttp(logging: HttpLoggingInterceptor, authInterceptor: Interceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)


    @Provides
    fun providePostApi(retrofit: Retrofit): PostApi =
        retrofit.create(PostApi::class.java)

    @Provides
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun providePostRepository(api: PostApi): PostRepository = PostRepositoryImpl(api)
}
