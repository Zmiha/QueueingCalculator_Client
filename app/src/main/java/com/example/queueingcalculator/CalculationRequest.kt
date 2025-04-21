package com.example.queueingcalculator

data class CalculationRequest(
    val userId: String,
    val timestamp: Long,
    val matrix: Array<Array<Double>>,
    val serviceRate: Array<Double>,
    val requests: Int,
    val variation: Array<Double>
)