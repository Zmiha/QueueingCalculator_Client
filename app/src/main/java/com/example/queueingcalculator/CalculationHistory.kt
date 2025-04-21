package com.example.queueingcalculator

data class CalculationHistory(
    val timestamp: Long,
    val serverTimestamp: Long,
    val userId: String,
    val request: RequestData,
    val result: CalculationResult?
)