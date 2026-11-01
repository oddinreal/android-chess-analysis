package com.example.chessinsight

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

data class ChessSession(
    val startFen: String = STARTING_FEN,
    val moves: List<String> = emptyList(),
    val ply: Int = 0,
    val whiteName: String = "White",
    val blackName: String = "Black"
) {
    fun boardAt(targetPly: Int = ply): Board {
        require(targetPly in 0..moves.size)
        val board = Board()
        board.loadFromFen(startFen)
        moves.take(targetPly).forEach { uci ->
            val move = Move(uci, board.sideToMove)
            check(board.doMove(move, true)) { "Illegal move in imported game: $uci" }
        }
        return board
    }

    fun fenAt(targetPly: Int = ply): String = boardAt(targetPly).fen

    fun legalMovesFrom(squareName: String): List<String> {
        val board = boardAt()
        val origin = Square.valueOf(squareName.uppercase())
        return board.legalMoves()
            .filter { it.from == origin }
            .map(Move::toString)
    }

    fun play(uci: String): ChessSession {
        val board = boardAt()
        val move = Move(uci, board.sideToMove)
        require(board.legalMoves().contains(move)) { "That is not a legal move." }
        check(board.doMove(move, true)) { "The move could not be applied." }
        return copy(moves = moves.take(ply) + uci.lowercase(), ply = ply + 1)
    }

    fun jumpTo(targetPly: Int): ChessSession = copy(ply = targetPly.coerceIn(0, moves.size))

    fun pieceAt(squareName: String): Piece {
        val board = boardAt()
        return board.getPiece(Square.valueOf(squareName.uppercase()))
    }

    companion object {
        const val STARTING_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }
}
