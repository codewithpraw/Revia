package com.contextswitch.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ContextSwitchApi {

    @POST("/resume")
    suspend fun postResume(@Body event: InterruptionEvent): SummaryResponse

    @GET("/history")
    suspend fun getHistory(@Query("limit") limit: Int = 20): List<HistoryItemResponse>

    @GET("/health")
    suspend fun getHealth(): HealthResponse

    companion object {
        fun create(baseUrl: String): ContextSwitchApi {
            val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            val client = OkHttpClient.Builder().addInterceptor(logging).build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ContextSwitchApi::class.java)
        }
    }
}
