package com.example.queueingcalculator

data class CalculationResult(
    val queueLength: List<Double>,
    val waitingTime: List<Double>,
    val residenceTime: List<Double>,
    val flowRate: List<Double>,
    val serverTimestamp: Long
)