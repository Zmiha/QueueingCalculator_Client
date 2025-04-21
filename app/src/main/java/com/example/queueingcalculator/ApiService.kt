package com.example.queueingcalculator

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("calculate")
    suspend fun calculate(@Body request: CalculationRequest): Response<CalculationResult>

    @GET("history/{user_id}")
    suspend fun getHistory(@Path("user_id") userId: String): List<CalculationHistory>
}