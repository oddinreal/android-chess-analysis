package com.example.chessinsight

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class EngineResult(
    val bestMove: String,
    val scoreCp: Int,
    val mateIn: Int?,
    val principalVariation: List<String>
)

class StockfishEngine(private val context: Context) : AutoCloseable {
    private val lock = Any()
    private var process: Process? = null
    private var input: BufferedWriter? = null
    private var output: BufferedReader? = null

    suspend fun analyze(fen: String, depth: Int): EngineResult {
        require(depth in 1..64) { "Analysis depth must be between 1 and 64." }
        require(fen.isNotBlank() && '\n' !in fen && '\r' !in fen) {
            "The engine requires a single-line FEN position."
        }
        require(fen.trim().split(Regex("\\s+")).size == 6) {
            "The engine requires a six-field FEN position."
        }

        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                ensureStarted()
                send("position fen $fen")
                send("go depth $depth")
                var scoreCp: Int? = null
                var mateIn: Int? = null
                var pv = emptyList<String>()
                while (true) {
                    val line = output?.readLine() ?: error("Stockfish stopped while analyzing.")
                    if (line.startsWith("info ")) {
                        val words = line.split(' ')
                        val scoreIndex = words.indexOf("score")
                        if (scoreIndex >= 0 && scoreIndex + 2 < words.size) {
                            when (words[scoreIndex + 1]) {
                                "cp" -> {
                                    scoreCp = words[scoreIndex + 2].toIntOrNull()
                                        ?: error("Stockfish returned an invalid centipawn score.")
                                    mateIn = null
                                }
                                "mate" -> {
                                    val parsedMate = words[scoreIndex + 2].toIntOrNull()
                                        ?: error("Stockfish returned an invalid mate score.")
                                    mateIn = parsedMate
                                    scoreCp = if (parsedMate > 0) {
                                        10_000 - parsedMate
                                    } else {
                                        -10_000 - parsedMate
                                    }
                                }
                            }
                        }
                        val pvIndex = words.indexOf("pv")
                        if (pvIndex >= 0) pv = words.drop(pvIndex + 1)
                    } else if (line.startsWith("bestmove ")) {
                        val bestMove = line.split(' ').getOrNull(1).orEmpty()
                        return@synchronized EngineResult(
                            bestMove = bestMove,
                            scoreCp = scoreCp ?: error("Stockfish returned no evaluation score."),
                            mateIn = mateIn,
                            principalVariation = pv
                        )
                    }
                }
                error("Stockfish did not return a best move.")
            }
        }
    }

    private fun ensureStarted() {
        if (process?.isAlive == true) return

        val libraryDirectory = context.applicationInfo.nativeLibraryDir
            ?: error("Android did not provide a native library directory.")
        val enginePath = "$libraryDirectory/libstockfish.so"
        check(java.io.File(enginePath).canExecute()) {
            "The packaged Stockfish engine is missing or not executable: $enginePath"
        }

        val started = ProcessBuilder(enginePath)
            .directory(java.io.File(libraryDirectory))
            .redirectErrorStream(true)
            .start()
        process = started
        input = BufferedWriter(OutputStreamWriter(started.outputStream))
        output = BufferedReader(InputStreamReader(started.inputStream))

        send("uci")
        readUntil("uciok")
        send("isready")
        readUntil("readyok")
    }

    private fun send(command: String) {
        val writer = input ?: error("Stockfish engine is not running.")
        writer.write(command)
        writer.newLine()
        writer.flush()
    }

    private fun readUntil(expected: String) {
        while (true) {
            val line = output?.readLine() ?: error("Stockfish exited before $expected.")
            if (line == expected) return
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { send("quit") }
            process?.destroy()
            process = null
            input = null
            output = null
        }
    }
}
