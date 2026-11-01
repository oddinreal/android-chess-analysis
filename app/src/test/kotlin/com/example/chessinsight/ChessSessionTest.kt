package com.example.chessinsight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessSessionTest {
    @Test
    fun playsLegalMovesAndBranchesFromEarlierPosition() {
        val afterTwoMoves = ChessSession()
            .play("e2e4")
            .play("e7e5")

        assertEquals(2, afterTwoMoves.ply)
        assertEquals("e2e4", afterTwoMoves.moves.first())
        assertTrue(afterTwoMoves.legalMovesFrom("g1").contains("g1f3"))

        val branch = afterTwoMoves.jumpTo(1).play("c7c5")
        assertEquals(listOf("e2e4", "c7c5"), branch.moves)
        assertEquals(2, branch.ply)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIllegalMoves() {
        ChessSession().play("e2e5")
    }
}
