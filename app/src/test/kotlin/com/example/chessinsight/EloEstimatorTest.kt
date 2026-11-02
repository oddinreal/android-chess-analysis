package com.example.chessinsight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EloEstimatorTest {
    @Test
    fun requiresEnoughMovesForAnEstimate() {
        assertNull(EloEstimator.estimate(List(7) { 0 }))
    }

    @Test
    fun calibratesAverageCentipawnLossToRatingBands() {
        assertEquals(2250, EloEstimator.estimate(List(30) { 20 }))
        assertEquals(1450, EloEstimator.estimate(List(30) { 100 }))
    }

    @Test
    fun discountsShortSamplesTowardBaseline() {
        val shortSample = EloEstimator.estimate(List(8) { 0 })!!
        val fullSample = EloEstimator.estimate(List(30) { 0 })!!

        assertTrue(shortSample in 2100..2200)
        assertEquals(2800, fullSample)
    }

    @Test
    fun higherMoveLossLowersTheEstimate() {
        val carefulPlay = EloEstimator.estimate(List(30) { 20 })!!
        val inaccuratePlay = EloEstimator.estimate(List(30) { 70 })!!

        assertTrue(carefulPlay > inaccuratePlay)
    }
}
