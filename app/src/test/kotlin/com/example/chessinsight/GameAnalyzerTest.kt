package com.example.chessinsight

import com.github.bhlangonijr.chesslib.Side
import org.junit.Assert.assertEquals
import org.junit.Test

class GameAnalyzerTest {
    @Test
    fun summarizesMoveLabelsByActualMover() {
        val review = GameReview(
            moves = listOf(
                move(ply = 1, mover = Side.BLACK, label = MoveLabel.BLUNDER),
                move(ply = 2, mover = Side.WHITE, label = MoveLabel.BEST),
                move(ply = 3, mover = Side.BLACK, label = MoveLabel.EXCELLENT),
                move(ply = 4, mover = Side.WHITE, label = MoveLabel.BRILLIANT),
                move(ply = 5, mover = Side.BLACK, label = MoveLabel.BEST)
            ),
            evaluationsWhite = emptyList(),
            whiteEstimatedElo = null,
            blackEstimatedElo = null,
            depth = 12
        )

        assertEquals(
            PlayerMoveSummary(
                moveCount = 2,
                brilliantMoves = 1,
                bestMoves = 1,
                excellentMoves = 0,
                blunders = 0
            ),
            review.summaryFor(Side.WHITE)
        )
        assertEquals(
            PlayerMoveSummary(
                moveCount = 3,
                brilliantMoves = 0,
                bestMoves = 1,
                excellentMoves = 1,
                blunders = 1
            ),
            review.summaryFor(Side.BLACK)
        )
    }

    private fun move(ply: Int, mover: Side, label: MoveLabel) = MoveReview(
        ply = ply,
        mover = mover,
        move = "1.",
        playedMove = "e2e4",
        label = label,
        centipawnLoss = 0,
        bestMove = "e2e4",
        bestLine = listOf("e2e4", "e7e5"),
        evaluationAfterWhite = 0
    )
}
