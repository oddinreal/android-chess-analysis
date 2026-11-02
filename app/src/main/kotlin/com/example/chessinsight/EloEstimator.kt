package com.example.chessinsight

import kotlin.math.ln1p
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object EloEstimator {
    const val MIN_MOVES = 8
    private const val FULL_CONFIDENCE_MOVES = 30
    private const val BASELINE_ELO = 1500

    private val calibration = listOf(
        0.0 to 2800.0,
        10.0 to 2500.0,
        20.0 to 2250.0,
        40.0 to 1950.0,
        70.0 to 1650.0,
        120.0 to 1350.0,
        200.0 to 1050.0,
        350.0 to 800.0,
        500.0 to 600.0
    )

    fun estimate(centipawnLosses: List<Int>): Int? {
        val losses = centipawnLosses.filter { it >= 0 }
        if (losses.size < MIN_MOVES) return null

        val averageLoss = losses.average()
        val rawEstimate = ratingForAverageLoss(averageLoss)
        val confidence = sqrt(
            (losses.size.toDouble() / FULL_CONFIDENCE_MOVES).coerceIn(0.0, 1.0)
        )
        val adjustedEstimate = BASELINE_ELO + (rawEstimate - BASELINE_ELO) * confidence
        return (adjustedEstimate / 50.0).roundToInt()
            .times(50)
            .coerceIn(600, 2800)
    }

    private fun ratingForAverageLoss(averageLoss: Double): Double {
        val boundedLoss = averageLoss.coerceIn(calibration.first().first, calibration.last().first)
        val upperIndex = calibration.indexOfFirst { it.first >= boundedLoss }
        if (upperIndex <= 0) return calibration.first().second

        val lower = calibration[upperIndex - 1]
        val upper = calibration[upperIndex]
        val progress = (ln1p(boundedLoss) - ln1p(lower.first)) /
            (ln1p(upper.first) - ln1p(lower.first))
        return lower.second + (upper.second - lower.second) * progress
    }
}
