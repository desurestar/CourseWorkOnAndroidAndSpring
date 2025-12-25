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
import ru.zagrebin.culinaryblog.BuildConfig
import ru.zagrebin.culinaryblog.data.remote.api.AdminApi
import ru.zagrebin.culinaryblog.data.local.dao.DraftDao
import ru.zagrebin.culinaryblog.data.local.dao.PostDao
import ru.zagrebin.culinaryblog.data.local.dao.StepDao
import ru.zagrebin.culinaryblog.data.local.dao.IngredientDao
import ru.zagrebin.culinaryblog.data.local.dao.UserProfileDao
import ru.zagrebin.culinaryblog.data.remote.api.AuthApi
import ru.zagrebin.culinaryblog.data.remote.api.PostApi
import ru.zagrebin.culinaryblog.data.remote.api.UserApi
import ru.zagrebin.culinaryblog.data.repository.ProfileRepository
import ru.zagrebin.culinaryblog.data.repository.ProfileRepositoryImpl
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.data.repository.PostRepositoryImpl
import ru.zagrebin.culinaryblog.data.repository.AdminRepository
import ru.zagrebin.culinaryblog.data.repository.AdminRepositoryImpl
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    fun provideLogging(): HttpLoggingInterceptor {
        val l = HttpLoggingInterceptor()
        l.redactHeader("Authorization")
        l.level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.BASIC
        }
        return l
    }

    @Provides
    fun provideAuthInterceptor(tokenStorage: TokenStorage): Interceptor = Interceptor { chain ->
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
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    fun provideUserApi(retrofit: Retrofit): UserApi =
        retrofit.create(UserApi::class.java)

    @Provides
    fun provideAdminApi(retrofit: Retrofit): AdminApi =
        retrofit.create(AdminApi::class.java)


    @Provides
    fun providePostApi(retrofit: Retrofit): PostApi =
        retrofit.create(PostApi::class.java)

    @Provides
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun providePostRepository(
        api: PostApi,
        postDao: PostDao,
        draftDao: DraftDao,
        stepDao: StepDao,
        ingredientDao: IngredientDao,
        gson: Gson
    ): PostRepository = PostRepositoryImpl(api, postDao, draftDao, stepDao, ingredientDao, gson)

    @Provides
    @Singleton
    fun provideChecklistRepository(
        checklistDao: ru.zagrebin.culinaryblog.data.local.dao.ChecklistDao,
        gson: Gson
    ): ru.zagrebin.culinaryblog.data.repository.ChecklistRepository =
        ru.zagrebin.culinaryblog.data.repository.ChecklistRepositoryImpl(checklistDao, gson)

    @Provides
    @Singleton
    fun provideAdminRepository(
        api: AdminApi
    ): AdminRepository = AdminRepositoryImpl(api)

    @Provides
    @Singleton
    fun provideProfileRepository(
        authApi: AuthApi,
        postRepository: PostRepository,
        userProfileDao: UserProfileDao,
        userApi: UserApi
    ): ProfileRepository = ProfileRepositoryImpl(authApi, postRepository, userProfileDao, userApi)
}
