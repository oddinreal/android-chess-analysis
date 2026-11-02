package com.example.chessinsight

import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move

enum class MoveLabel(val title: String) {
    BRILLIANT("Brilliant"),
    BEST("Best"),
    EXCELLENT("Excellent"),
    GOOD("Good"),
    INACCURACY("Inaccuracy"),
    MISTAKE("Mistake"),
    BLUNDER("Blunder")
}

data class MoveReview(
    val ply: Int,
    val mover: Side,
    val move: String,
    val playedMove: String,
    val label: MoveLabel,
    val centipawnLoss: Int,
    val bestMove: String,
    val bestLine: List<String>,
    val evaluationAfterWhite: Int
)

data class PlayerMoveSummary(
    val moveCount: Int,
    val brilliantMoves: Int,
    val bestMoves: Int,
    val excellentMoves: Int,
    val blunders: Int
)

data class GameReview(
    val moves: List<MoveReview>,
    val evaluationsWhite: List<Int>,
    val whiteEstimatedElo: Int?,
    val blackEstimatedElo: Int?,
    val depth: Int
) {
    fun summaryFor(side: Side): PlayerMoveSummary {
        val playerMoves = moves.filter { it.mover == side }
        return PlayerMoveSummary(
            moveCount = playerMoves.size,
            brilliantMoves = playerMoves.count { it.label == MoveLabel.BRILLIANT },
            bestMoves = playerMoves.count { it.label == MoveLabel.BEST },
            excellentMoves = playerMoves.count { it.label == MoveLabel.EXCELLENT },
            blunders = playerMoves.count { it.label == MoveLabel.BLUNDER }
        )
    }
}

class GameAnalyzer(private val engine: StockfishEngine) {
    suspend fun analyze(
        session: ChessSession,
        depth: Int,
        onProgress: suspend (completed: Int, total: Int) -> Unit
    ): GameReview {
        val reviews = mutableListOf<MoveReview>()
        val board = session.boardAt(0)
        var before = engine.analyze(board.fen, depth)
        val evaluationsWhite = mutableListOf(
            whitePerspective(before.scoreCp, board.sideToMove)
        )
        session.moves.forEachIndexed { index, played ->
            val mover = board.sideToMove
            val move = Move(played, mover)
            check(board.doMove(move, true)) { "Illegal move in imported game: $played" }
            val after = engine.analyze(board.fen, depth)
            val evaluationAfterWhite = whitePerspective(after.scoreCp, board.sideToMove)
            evaluationsWhite += evaluationAfterWhite
            val loss = (before.scoreCp + after.scoreCp).coerceAtLeast(0)
            val afterForMover = -after.scoreCp
            val improvement = afterForMover - before.scoreCp
            val label = classify(
                played = played,
                best = before.bestMove,
                loss = loss,
                improvement = improvement
            )
            reviews += MoveReview(
                ply = index + 1,
                mover = mover,
                move = session.moveNotationAt(index),
                playedMove = played,
                label = label,
                centipawnLoss = loss,
                bestMove = before.bestMove,
                bestLine = before.principalVariation,
                evaluationAfterWhite = evaluationAfterWhite
            )
            onProgress(index + 1, session.moves.size)
            before = after
        }

        return GameReview(
            moves = reviews,
            evaluationsWhite = evaluationsWhite,
            whiteEstimatedElo = EloEstimator.estimate(
                reviews.filter { it.mover == Side.WHITE }.map(MoveReview::centipawnLoss)
            ),
            blackEstimatedElo = EloEstimator.estimate(
                reviews.filter { it.mover == Side.BLACK }.map(MoveReview::centipawnLoss)
            ),
            depth = depth
        )
    }

    private fun whitePerspective(scoreCp: Int, sideToMove: Side): Int =
        if (sideToMove == Side.WHITE) scoreCp else -scoreCp

    private fun classify(played: String, best: String, loss: Int, improvement: Int): MoveLabel =
        when {
            played.equals(best, ignoreCase = true) && improvement >= 150 -> MoveLabel.BRILLIANT
            played.equals(best, ignoreCase = true) && loss <= 10 -> MoveLabel.BEST
            loss <= 25 -> MoveLabel.EXCELLENT
            loss <= 60 -> MoveLabel.GOOD
            loss <= 120 -> MoveLabel.INACCURACY
            loss <= 250 -> MoveLabel.MISTAKE
            else -> MoveLabel.BLUNDER
        }
}
