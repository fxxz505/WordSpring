package com.wordspring.app.sync

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface WordSpringApi {

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): TokenResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("api/v1/sync/push")
    suspend fun push(
        @Header("Authorization") authorization: String,
        @Body body: PushRequest
    ): PushResponse

    @GET("api/v1/sync/pull")
    suspend fun pull(
        @Header("Authorization") authorization: String,
        @Query("since") since: Long
    ): PullResponse
}
