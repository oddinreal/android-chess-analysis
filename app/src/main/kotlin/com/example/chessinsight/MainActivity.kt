package com.example.chessinsight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlinx.coroutines.launch

private val Ink = Color(0xFF202B3C)
private val Background = Color(0xFFF3F5F9)
private val Panel = Color(0xFFFFFFFF)
private val Accent = Color(0xFF5C63D8)
private val Muted = Color(0xFF68758A)
private val PlayedMoveColor = Color(0xFFE97845)
private val BestMoveColor = Color(0xFF2479C8)

private data class PositionEvaluation(
    val fen: String,
    val scoreWhiteCp: Int,
    val mateWhiteIn: Int?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Accent,
                    onPrimary = Color.White,
                    secondary = Color(0xFF5279B7),
                    background = Background,
                    surface = Panel,
                    onSurface = Ink
                )
            ) {
                ChessInsightApp()
            }
        }
    }
}

@Composable
private fun ChessInsightApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { StockfishEngine(context.applicationContext) }
    var session by remember { mutableStateOf(ChessSession()) }
    var selectedOrigin by remember { mutableStateOf<String?>(null) }
    var review by remember { mutableStateOf<GameReview?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var engineMessage by remember { mutableStateOf<String?>(null) }
    var engineBusy by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var notationEntry by remember { mutableStateOf("") }
    var positionEvaluation by remember { mutableStateOf<PositionEvaluation?>(null) }
    var analysisDepth by remember { mutableStateOf(20) }
    var showNotationInput by remember { mutableStateOf(false) }
    var autoAnalyzeImportedGames by remember { mutableStateOf(true) }

    suspend fun analyzeGame(targetSession: ChessSession) {
        engineBusy = true
        analysisProgress = 0 to targetSession.moves.size
        review = null
        errorMessage = null
        engineMessage = "Analyzing game at depth $analysisDepth…"
        try {
            review = GameAnalyzer(engine).analyze(targetSession, analysisDepth) { done, total ->
                analysisProgress = done to total
            }
            engineMessage = "Game analysis complete · depth $analysisDepth."
        } catch (exception: Exception) {
            errorMessage = exception.message ?: "Game analysis failed."
            engineMessage = null
        } finally {
            analysisProgress = null
            engineBusy = false
        }
    }

    fun playMove(uci: String): ChessSession? {
        val current = session
        val followsGame = current.moves.getOrNull(current.ply)
            ?.equals(uci, ignoreCase = true) == true
        val updated = current.play(uci)
        session = updated
        selectedOrigin = null
        positionEvaluation = null
        errorMessage = null
        engineMessage = null
        if (followsGame) return null

        review = null
        return updated
    }

    DisposableEffect(engine) {
        onDispose { engine.close() }
    }

    val board = remember(session) { session.boardAt() }
    val legalMoves = remember(session, selectedOrigin) {
        selectedOrigin?.let(session::legalMovesFrom).orEmpty()
    }
    val legalTargets = remember(legalMoves) { legalMoves.map { it.substring(2, 4) }.toSet() }
    val positionFen = remember(session) { session.fenAt() }
    val currentEvaluation = positionEvaluation?.takeIf { it.fen == positionFen }
        ?: review?.evaluationsWhite?.getOrNull(session.ply)?.let {
            PositionEvaluation(positionFen, it, null)
        }
    val selectedReviewMove = review?.moves?.getOrNull(session.ply - 1)

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !engineBusy && analysisProgress == null,
                    onClick = { showNotationInput = !showNotationInput }
                ) {
                    Text(if (showNotationInput) "Hide PGN / FEN input" else "Import PGN / FEN")
                }
            }

            if (showNotationInput) item {
                OutlinedTextField(
                    value = notationEntry,
                    onValueChange = { notationEntry = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !engineBusy && analysisProgress == null,
                    label = { Text("Paste PGN or FEN") },
                    minLines = 4,
                    maxLines = 8,
                    trailingIcon = {
                        Text(
                            "LOAD",
                            color = Accent,
                            modifier = Modifier.clickable(enabled = !engineBusy && analysisProgress == null) {
                                try {
                                    val imported = GameImport.fromText("input.pgn", notationEntry)
                                    session = imported
                                    review = null
                                    selectedOrigin = null
                                    positionEvaluation = null
                                    errorMessage = null
                                    engineMessage = null
                                    notationEntry = ""
                                    showNotationInput = false
                                    if (autoAnalyzeImportedGames && imported.moves.isNotEmpty()) {
                                        scope.launch { analyzeGame(imported) }
                                    }
                                } catch (exception: Exception) {
                                    errorMessage = exception.message ?: "Could not read PGN or FEN."
                                }
                            }.padding(horizontal = 12.dp)
                        )
                    }
                )
            }

            item {
                EvaluationBar(
                    scoreWhiteCp = currentEvaluation?.scoreWhiteCp,
                    mateWhiteIn = currentEvaluation?.mateWhiteIn
                )
            }

            selectedReviewMove?.let { moveReview ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "${moveReview.move} ${moveReview.playedMove} · ${moveReview.label.title}",
                                color = labelColor(moveReview.label),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Best ${moveReview.bestMove} · ${evaluationSummary(moveReview.evaluationAfterWhite)}",
                                color = Ink,
                                fontSize = 12.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Played move", color = PlayedMoveColor, fontSize = 11.sp)
                                Text("Best move", color = BestMoveColor, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            item {
                BoardView(
                    session = session,
                    selectedOrigin = selectedOrigin,
                    legalTargets = legalTargets,
                    reviewMove = selectedReviewMove,
                    enabled = !engineBusy && analysisProgress == null,
                    onSquareTap = { square ->
                        val selected = selectedOrigin
                        val move = legalMoves.firstOrNull { it.substring(2, 4) == square }
                        if (selected != null && move != null) {
                            try {
                                val variation = playMove(move)
                                if (variation != null) {
                                    scope.launch { analyzeGame(variation) }
                                }
                            } catch (exception: Exception) {
                                errorMessage = exception.message
                            }
                            selectedOrigin = null
                        } else {
                            val piece = session.pieceAt(square)
                            if (piece != Piece.NONE && piece.pieceSide == board.sideToMove) {
                                selectedOrigin = square
                            } else if (selected != null) {
                                selectedOrigin = null
                            }
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${session.whiteName}  ·  ${session.blackName}", fontWeight = FontWeight.SemiBold)
                    Text(if (board.sideToMove == Side.WHITE) "White to move" else "Black to move", color = Muted)
                }
            }

            item {
                Text(
                    if (engineBusy) "Analyzing… you can move again when the analysis finishes."
                    else "Tap a piece, then a highlighted square to play. Alternate moves are analyzed automatically.",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !engineBusy && analysisProgress == null,
                    onClick = {
                        scope.launch {
                            engineBusy = true
                            engineMessage = "Evaluating this position…"
                            errorMessage = null
                            try {
                                val result = engine.analyze(positionFen, analysisDepth)
                                val whiteToMove = board.sideToMove == Side.WHITE
                                positionEvaluation = PositionEvaluation(
                                    fen = positionFen,
                                    scoreWhiteCp = if (whiteToMove) result.scoreCp else -result.scoreCp,
                                    mateWhiteIn = result.mateIn?.let { if (whiteToMove) it else -it }
                                )
                                engineMessage = "Position evaluated at depth $analysisDepth."
                            } catch (exception: Exception) {
                                errorMessage = exception.message ?: "Stockfish could not analyze this position."
                                engineMessage = null
                            } finally {
                                engineBusy = false
                            }
                        }
                    }
                ) { Text("Analyze position") }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !engineBusy && analysisProgress == null,
                        onClick = { session = session.jumpTo(0); selectedOrigin = null }
                    ) { Text("First") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !engineBusy && analysisProgress == null,
                        onClick = { session = session.jumpTo(session.ply - 1); selectedOrigin = null }
                    ) { Text("‹ Back") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !engineBusy && analysisProgress == null,
                        onClick = { session = session.jumpTo(session.ply + 1); selectedOrigin = null }
                    ) { Text("Forward ›") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !engineBusy && analysisProgress == null,
                        onClick = { session = session.jumpTo(session.moves.size); selectedOrigin = null }
                    ) { Text("Latest") }
                }
            }

            if (session.moves.isNotEmpty()) {
                item {
                    Text(
                        "MOVE HISTORY  ·  tap a move to jump there",
                        color = Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        session.moves.forEachIndexed { index, move ->
                            val active = session.ply == index + 1
                            Text(
                                text = "${index / 2 + 1}${if (index % 2 == 0) "." else "..."} $move",
                                color = if (active) Color.White else Ink,
                                modifier = Modifier
                                    .background(if (active) Accent else Panel, RoundedCornerShape(8.dp))
                                    .clickable(enabled = !engineBusy && analysisProgress == null) {
                                        session = session.jumpTo(index + 1)
                                        selectedOrigin = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("GAME ANALYSIS", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Choose engine depth", color = Muted, fontSize = 12.sp)
                            }
                            Text("DEPTH $analysisDepth", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(12, 16, 20, 25).forEach { depth ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (analysisDepth == depth) Accent else Color(0xFFE8ECF3))
                                        .clickable(enabled = !engineBusy && analysisProgress == null) {
                                            analysisDepth = depth
                                        }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        depth.toString(),
                                        color = if (analysisDepth == depth) Color.White else Ink,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            "Imported PGN games are analyzed automatically. You can also analyze again below.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-analyze imported PGN", color = Ink, fontSize = 13.sp)
                            Switch(
                                checked = autoAnalyzeImportedGames,
                                onCheckedChange = { autoAnalyzeImportedGames = it },
                                enabled = !engineBusy && analysisProgress == null
                            )
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = session.moves.isNotEmpty() && !engineBusy && analysisProgress == null,
                            onClick = { scope.launch { analyzeGame(session) } }
                        ) { Text("Analyze / reanalyze game") }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !engineBusy && analysisProgress == null,
                        onClick = {
                            scope.launch {
                                engineBusy = true
                                engineMessage = "Stockfish is thinking…"
                                errorMessage = null
                                var variationToAnalyze: ChessSession? = null
                                try {
                                    val result = engine.analyze(session.fenAt(), analysisDepth)
                                    if (result.bestMove.isNotBlank() && result.bestMove != "(none)") {
                                        val evaluation = result.mateIn?.let { "M$it" } ?: run {
                                            val pawns = result.scoreCp / 100.0
                                            "${if (pawns > 0) "+" else ""}${"%.2f".format(pawns)}"
                                        }
                                        val pv = result.principalVariation.take(5).joinToString(" ")
                                        variationToAnalyze = playMove(result.bestMove)
                                        engineMessage = "Stockfish played ${result.bestMove} · $evaluation for side to move" +
                                            if (pv.isBlank()) "" else " · PV $pv"
                                    } else {
                                        engineMessage = "No legal moves in this position."
                                    }
                                } catch (exception: Exception) {
                                    errorMessage = exception.message ?: "Stockfish could not analyze this position."
                                    engineMessage = null
                                } finally {
                                    engineBusy = false
                                }
                                variationToAnalyze?.let { analyzeGame(it) }
                            }
                        }
                    ) { Text("Play Stockfish best move") }
                }
            }

            engineMessage?.let { message ->
                item { Text(message, color = Accent) }
            }
            errorMessage?.let { message ->
                item {
                    Text(
                        message,
                        color = Color(0xFFB42318),
                        modifier = Modifier.background(Panel, RoundedCornerShape(10.dp)).padding(12.dp)
                    )
                }
            }

            analysisProgress?.let { (done, total) ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Analyzing move $done of $total · depth $analysisDepth", color = Muted)
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { if (total == 0) 0f else done.toFloat() / total },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            review?.let { gameReview ->
                item { ReviewSummary(gameReview, session.whiteName, session.blackName) }
                item {
                    Text(
                        "MOVE-BY-MOVE REVIEW",
                        color = Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(gameReview.moves) { moveReview ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        modifier = Modifier.fillMaxWidth().clickable {
                            session = session.jumpTo(moveReview.ply)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${moveReview.move} ${moveReview.playedMove}", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Best: ${moveReview.bestMove}  ·  loss ${moveReview.centipawnLoss} cp",
                                    color = Muted,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "After: ${evaluationSummary(moveReview.evaluationAfterWhite)}",
                                    color = Ink,
                                    fontSize = 12.sp
                                )
                            }
                            Text(moveReview.label.title, color = labelColor(moveReview.label), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun EvaluationBar(scoreWhiteCp: Int?, mateWhiteIn: Int?) {
    val whiteAdvantage = scoreWhiteCp?.let { it > 0 } ?: false
    val blackAdvantage = scoreWhiteCp?.let { it < 0 } ?: false
    val whiteShare = ((scoreWhiteCp ?: 0) / 800f + 0.5f).coerceIn(0.04f, 0.96f)
    val evaluationText = when {
        scoreWhiteCp == null -> "Analyze position to see who is better"
        mateWhiteIn != null -> "${if (whiteAdvantage) "White" else "Black"} mates in ${kotlin.math.abs(mateWhiteIn)}"
        scoreWhiteCp == 0 -> "Equal · 0.00"
        else -> "${if (whiteAdvantage) "White" else "Black"} is better · " +
            "+${"%.2f".format(kotlin.math.abs(scoreWhiteCp) / 100.0)}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("WHITE", color = if (whiteAdvantage) Accent else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(evaluationText, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("BLACK", color = if (blackAdvantage) Accent else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF26334A))
        ) {
            Box(
                modifier = Modifier.weight(whiteShare).fillMaxSize().background(Color(0xFFF5F0E5))
            )
            Box(
                modifier = Modifier.weight(1f - whiteShare).fillMaxSize().background(Color(0xFF26334A))
            )
        }
    }
}

@Composable
private fun BoardView(
    session: ChessSession,
    selectedOrigin: String?,
    legalTargets: Set<String>,
    reviewMove: MoveReview?,
    enabled: Boolean,
    onSquareTap: (String) -> Unit
) {
    val board = remember(session) { session.boardAt() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, Color(0xFFCBD3DF), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (rank in 8 downTo 1) {
                Row(modifier = Modifier.weight(1f)) {
                    for (file in 'a'..'h') {
                        val square = "$file$rank"
                        val piece = board.getPiece(Square.valueOf(square.uppercase()))
                        val light = (file.code - 'a'.code + rank) % 2 == 0
                        val squareColor = when {
                            square == selectedOrigin -> Color(0xFFF2CE78)
                            light -> Color(0xFFF1E8D8)
                            else -> Color(0xFF788CA8)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(squareColor)
                                .clickable(enabled = enabled) { onSquareTap(square) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (square in legalTargets && piece == Piece.NONE) {
                                Box(
                                    Modifier.size(12.dp).background(Color(0x887A876E), CircleShape)
                                )
                            }
                            if (piece != Piece.NONE) {
                                Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                    drawChessPiece(piece)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (reviewMove != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellWidth = size.width / 8f
                val cellHeight = size.height / 8f

                fun drawMoveArrow(uci: String, color: Color, laneOffsetDp: Float) {
                    if (uci.length < 4) return
                    val fromFile = uci[0] - 'a'
                    val fromRank = uci[1] - '1'
                    val toFile = uci[2] - 'a'
                    val toRank = uci[3] - '1'
                    if (fromFile !in 0..7 || fromRank !in 0..7 || toFile !in 0..7 || toRank !in 0..7) {
                        return
                    }

                    val rawStart = Offset(
                        (fromFile + 0.5f) * cellWidth,
                        (7 - fromRank + 0.5f) * cellHeight
                    )
                    val rawEnd = Offset(
                        (toFile + 0.5f) * cellWidth,
                        (7 - toRank + 0.5f) * cellHeight
                    )
                    val dx = rawEnd.x - rawStart.x
                    val dy = rawEnd.y - rawStart.y
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (distance == 0f) return

                    val ux = dx / distance
                    val uy = dy / distance
                    val lane = laneOffsetDp.dp.toPx()
                    val perpendicular = Offset(-uy * lane, ux * lane)
                    val start = rawStart + perpendicular
                    val end = rawEnd + perpendicular
                    val headLength = minOf(cellWidth, cellHeight) * 0.3f
                    val baseX = end.x - ux * headLength
                    val baseY = end.y - uy * headLength
                    val halfWidth = headLength * 0.62f
                    val strokeWidth = 8.dp.toPx()
                    drawLine(
                        color = Color(0xDD111914),
                        start = start,
                        end = Offset(baseX, baseY),
                        strokeWidth = strokeWidth + 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = color,
                        start = start,
                        end = Offset(baseX, baseY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    val arrowHead = Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(baseX - uy * halfWidth, baseY + ux * halfWidth)
                        lineTo(baseX + uy * halfWidth, baseY - ux * halfWidth)
                        close()
                    }
                    drawPath(arrowHead, Color(0xDD111914))
                    val coloredHead = Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(baseX - uy * halfWidth * 0.78f, baseY + ux * halfWidth * 0.78f)
                        lineTo(baseX + uy * halfWidth * 0.78f, baseY - ux * halfWidth * 0.78f)
                        close()
                    }
                    drawPath(coloredHead, color)
                    drawCircle(Color(0xDD111914), radius = strokeWidth * 0.9f, center = start)
                    drawCircle(color, radius = strokeWidth * 0.58f, center = start)
                }

                if (reviewMove.bestMove.equals(reviewMove.playedMove, ignoreCase = true)) {
                    drawMoveArrow(reviewMove.bestMove, BestMoveColor, 0f)
                } else {
                    drawMoveArrow(reviewMove.bestMove, BestMoveColor, -5f)
                    drawMoveArrow(reviewMove.playedMove, PlayedMoveColor, 5f)
                }
            }
        }
    }
}

private fun evaluationSummary(scoreWhiteCp: Int): String = when {
    scoreWhiteCp in -10..10 -> "Equal · 0.00"
    scoreWhiteCp > 0 -> "White better · +${"%.2f".format(scoreWhiteCp / 100.0)}"
    else -> "Black better · +${"%.2f".format(kotlin.math.abs(scoreWhiteCp) / 100.0)}"
}

@Composable
private fun ReviewSummary(review: GameReview, whiteName: String, blackName: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ESTIMATED PERFORMANCE", color = Accent, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "$whiteName\n${review.whiteEstimatedElo?.let { "$it Elo" } ?: "No moves"}",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "$blackName\n${review.blackEstimatedElo?.let { "$it Elo" } ?: "No moves"}",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "Rough estimates from average centipawn loss, not official ratings. " +
                    "Engine depth ${review.depth}; move labels are heuristic.",
                color = Muted,
                fontSize = 12.sp
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChessPiece(piece: Piece) {
    val scale = size.minDimension / 100f
    val left = (size.width - 100f * scale) / 2f
    val top = (size.height - 100f * scale) / 2f
    fun point(x: Float, y: Float) = Offset(left + x * scale, top + y * scale)
    fun oval(path: Path, x1: Float, y1: Float, x2: Float, y2: Float) {
        val first = point(x1, y1)
        val second = point(x2, y2)
        path.addOval(Rect(first.x, first.y, second.x, second.y))
    }

    val silhouette = Path()
    fun polygon(vararg coordinates: Float) {
        silhouette.moveTo(point(coordinates[0], coordinates[1]).x, point(coordinates[0], coordinates[1]).y)
        for (index in 2 until coordinates.size step 2) {
            val next = point(coordinates[index], coordinates[index + 1])
            silhouette.lineTo(next.x, next.y)
        }
        silhouette.close()
    }

    when (piece.name.substringAfter('_')) {
        "PAWN" -> {
            oval(silhouette, 39f, 13f, 61f, 35f)
            polygon(44f, 37f, 56f, 37f, 57f, 45f, 62f, 54f, 67f, 69f, 33f, 69f, 38f, 54f, 43f, 45f)
        }
        "ROOK" -> polygon(
            32f, 20f, 43f, 20f, 43f, 30f, 48f, 30f, 48f, 20f, 53f, 20f,
            53f, 30f, 58f, 30f, 58f, 20f, 69f, 20f, 67f, 40f, 62f, 44f,
            66f, 69f, 34f, 69f, 38f, 44f, 33f, 40f
        )
        "KNIGHT" -> {
            silhouette.moveTo(point(31f, 75f).x, point(31f, 75f).y)
            silhouette.cubicTo(
                point(37f, 59f).x, point(37f, 59f).y,
                point(32f, 48f).x, point(32f, 48f).y,
                point(38f, 38f).x, point(38f, 38f).y
            )
            silhouette.lineTo(point(34f, 26f).x, point(34f, 26f).y)
            silhouette.lineTo(point(52f, 34f).x, point(52f, 34f).y)
            silhouette.cubicTo(
                point(63f, 29f).x, point(63f, 29f).y,
                point(72f, 39f).x, point(72f, 39f).y,
                point(69f, 52f).x, point(69f, 52f).y
            )
            silhouette.lineTo(point(63f, 64f).x, point(63f, 64f).y)
            silhouette.lineTo(point(70f, 75f).x, point(70f, 75f).y)
            silhouette.close()
        }
        "BISHOP" -> {
            oval(silhouette, 45f, 13f, 55f, 23f)
            polygon(43f, 27f, 57f, 27f, 60f, 39f, 66f, 55f, 61f, 69f, 39f, 69f, 34f, 55f, 40f, 39f)
        }
        "QUEEN" -> {
            oval(silhouette, 28f, 22f, 42f, 36f)
            oval(silhouette, 43f, 12f, 57f, 26f)
            oval(silhouette, 58f, 22f, 72f, 36f)
            polygon(32f, 38f, 68f, 38f, 64f, 53f, 60f, 67f, 40f, 67f, 36f, 53f)
        }
        else -> {
            polygon(46f, 12f, 54f, 12f, 54f, 24f, 65f, 24f, 65f, 32f, 54f, 32f, 54f, 39f, 59f, 45f, 66f, 58f, 62f, 69f, 38f, 69f, 34f, 58f, 41f, 45f, 46f, 39f, 46f, 32f, 35f, 32f, 35f, 24f, 46f, 24f)
        }
    }
    polygon(27f, 70f, 73f, 70f, 78f, 80f, 22f, 80f)
    polygon(19f, 82f, 81f, 82f, 85f, 91f, 15f, 91f)

    val white = piece.pieceSide == Side.WHITE
    val fillColor = if (white) Color(0xFFFFFFFF) else Color(0xFF202B3C)
    val outlineColor = if (white) Color(0xFF26354A) else Color(0xFFFFF3D8)
    val detailColor = if (white) Color(0xFF64748B) else Color(0xFFF0C978)
    drawPath(silhouette, Color(0x44000000), style = Stroke(width = 7f * scale))
    drawPath(silhouette, outlineColor, style = Stroke(width = 5f * scale))
    drawPath(silhouette, fillColor)

    fun detailLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 2.4f) {
        drawLine(
            color = detailColor,
            start = point(x1, y1),
            end = point(x2, y2),
            strokeWidth = width * scale,
            cap = StrokeCap.Round
        )
    }
    when (piece.name.substringAfter('_')) {
        "PAWN" -> {
            drawCircle(detailColor, radius = 2.8f * scale, center = point(50f, 24f))
            detailLine(44f, 49f, 56f, 49f)
        }
        "ROOK" -> {
            detailLine(40f, 42f, 60f, 42f)
            detailLine(43f, 48f, 57f, 48f)
        }
        "KNIGHT" -> {
            drawCircle(detailColor, radius = 2.2f * scale, center = point(56f, 41f))
            detailLine(40f, 57f, 52f, 61f, 2f)
        }
        "BISHOP" -> {
            detailLine(47f, 31f, 54f, 37f, 3f)
            detailLine(45f, 51f, 55f, 61f)
        }
        "QUEEN" -> detailLine(40f, 48f, 60f, 48f)
        else -> {
            detailLine(50f, 48f, 50f, 64f)
            detailLine(43f, 56f, 57f, 56f)
        }
    }
    detailLine(35f, 75f, 65f, 75f)
}

private fun labelColor(label: MoveLabel): Color = when (label) {
    MoveLabel.BRILLIANT -> Color(0xFF007C83)
    MoveLabel.BEST -> Accent
    MoveLabel.EXCELLENT -> Color(0xFF2878BC)
    MoveLabel.GOOD -> Color(0xFF5F6F86)
    MoveLabel.INACCURACY -> Color(0xFF9A6900)
    MoveLabel.MISTAKE -> Color(0xFFC65D22)
    MoveLabel.BLUNDER -> Color(0xFFC53636)
}
