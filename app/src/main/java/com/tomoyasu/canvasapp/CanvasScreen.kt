package com.tomoyasu.canvasapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Studio Web版キャンバスの見た目トークン（studio/index.html の :root と .cv-* から採ったもの）
private val ColBg = Color(0xFF171615)          // --s
private val ColCard = Color(0xFF1F1F27)        // --s-c
private val ColCardBorder = Color(0xFF413D4A)  // --outline-var
private val ColOnS = Color(0xFFF3EFF7)         // --on-s
private val ColOutline = Color(0xFF968FA3)     // --outline / 線の色
private val ColOnColored = Color(0xFF17161A)   // 色付きカードの文字色

private fun hexToColor(hex: String, alpha: Float = 1f): Color {
    return try {
        val c = android.graphics.Color.parseColor(hex)
        Color(
            red = android.graphics.Color.red(c) / 255f,
            green = android.graphics.Color.green(c) / 255f,
            blue = android.graphics.Color.blue(c) / 255f,
            alpha = alpha
        )
    } catch (e: IllegalArgumentException) {
        ColOutline.copy(alpha = alpha)
    }
}

@Composable
fun CanvasScreen(host: String, port: String, onBack: () -> Unit) {
    var board by remember { mutableStateOf<CanvasBoard?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(host, port) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) { StudioClient.fetchBoard(host, port) }
        when (result) {
            is StudioClient.BoardResult.Success -> board = result.board
            is StudioClient.BoardResult.Failure -> error = result.message
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(ColBg)) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            TextButton(onClick = onBack) { Text("< 設定") }
            Text(
                board?.title ?: "キャンバス",
                color = ColOnS,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error ?: "", color = ColOnS)
                }
            }
            board != null -> CanvasBoardView(board!!)
        }
    }
}

@Composable
private fun CanvasBoardView(board: CanvasBoard) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val cardHeights = remember { mutableStateMapOf<Int, Float>() }

    val cardRight = board.cards.maxOfOrNull { it.x + it.w } ?: 0f
    val zoneRight = board.zones.maxOfOrNull { it.x + it.w } ?: 0f
    val cardBottom = board.cards.maxOfOrNull { it.y + (cardHeights[it.id] ?: 60f) } ?: 0f
    val zoneBottom = board.zones.maxOfOrNull { it.y + it.h } ?: 0f
    val maxX = max(800f, max(cardRight, zoneRight) + 400f)
    val maxY = max(800f, max(cardBottom, zoneBottom) + 400f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.2f, 3f)
                    offset += pan
                }
            }
    ) {
        Box(
            modifier = Modifier
                .size(maxX.dp, maxY.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    transformOrigin = TransformOrigin(0f, 0f)
                )
        ) {
            // ---- 領域（一番奥） ----
            board.zones.forEach { zone ->
                Box(
                    modifier = Modifier
                        .graphicsLayer(translationX = zone.x, translationY = zone.y)
                        .size(zone.w.dp, zone.h.dp)
                ) {
                    ZoneBackground(zone)
                    Text(
                        zone.name,
                        color = hexToColor(zone.color),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(start = 11.dp, top = 7.dp)
                    )
                }
            }

            // ---- 線（領域の上、カードの下） ----
            WireLayer(board, cardHeights)

            // ---- カード（一番手前） ----
            board.cards.forEach { card ->
                CardView(card, onSize = { h -> cardHeights[card.id] = h })
            }
        }
    }
}

@Composable
private fun ZoneBackground(zone: CanvasZone) {
    val borderColor = hexToColor(zone.color, 0.4f)
    val fillColor = hexToColor(zone.color, 0.07f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = 14.dp.toPx()
        drawRoundRect(
            color = fillColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )
        drawRoundRect(
            color = borderColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
        )
    }
}

@Composable
private fun CardView(card: CanvasCard, onSize: (Float) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val hasColor = card.color != null
    val bg = if (hasColor) hexToColor(card.color!!) else ColCard
    val textColor = if (hasColor) ColOnColored else ColOnS

    Box(
        modifier = Modifier
            .graphicsLayer(translationX = card.x, translationY = card.y)
            .width(card.w.dp)
            .background(bg, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .then(
                if (!hasColor)
                    Modifier.border(1.5.dp, ColCardBorder, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                else Modifier
            )
            .padding(vertical = 10.dp, horizontal = 12.dp)
            .onSizeChanged { onSize(it.height / density.density) }
    ) {
        Text(
            card.text.ifBlank { " " },
            color = textColor,
            fontSize = 12.5.sp,
            lineHeight = 19.4.sp
        )
    }
}

@Composable
private fun WireLayer(board: CanvasBoard, cardHeights: Map<Int, Float>) {
    val cardById = remember(board) { board.cards.associateBy { it.id } }
    Canvas(modifier = Modifier.fillMaxSize()) {
        board.links.forEach { link ->
            val a = cardById[link.from] ?: return@forEach
            val b = cardById[link.to] ?: return@forEach
            val ah = cardHeights[a.id] ?: 60f
            val bh = cardHeights[b.id] ?: 60f
            val ax = (a.x + a.w / 2).dp.toPx()
            val ay = (a.y + ah / 2).dp.toPx()
            val bx = (b.x + b.w / 2).dp.toPx()
            val by = (b.y + bh / 2).dp.toPx()

            val dx = bx - ax
            val dy = by - ay
            val path = Path()
            path.moveTo(ax, ay)
            if (abs(dx) >= abs(dy)) {
                val o = abs(dx) * .5f
                path.cubicTo(ax + o, ay, bx - o, by, bx, by)
            } else {
                val o = abs(dy) * .5f
                path.cubicTo(ax, ay + o, bx, by - o, bx, by)
            }
            drawPath(path, color = ColOutline, style = Stroke(width = 1.5.dp.toPx()), alpha = 0.65f)
        }
    }
}
