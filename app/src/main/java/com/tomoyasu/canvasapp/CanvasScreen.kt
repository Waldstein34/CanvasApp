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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
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

// Studio Web版キャンバスの「ガラス」テーマ（data-theme="glass"）の見た目トークン。
// studio/index.html の :root[data-theme="glass"] と .cv-* 系のCSSから採った。
private val GlassBgBase = Color(0xFF0A0911)     // rgba(11,10,18,.94) を不透明下地の上に合成した近似値
private val ColCard = Color(0x12FFFFFF)         // --s-c: rgba(255,255,255,.07)
private val ColCardBorder = Color(0x29FFFFFF)   // --outline-var: rgba(255,255,255,.16)
private val ColOnS = Color(0xFFF6F3FC)          // --on-s
private val ColOutline = Color(0xFFA8A1BD)      // --outline / 線の色
private val ColOnColored = Color(0xFFF0EDF7)    // 色付きカードの文字色（ガラス版は白系）

private fun rgba(r: Int, g: Int, b: Int, a: Float) =
    Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = a)

// カードの色は付箋の色見本5色＋既定のみ（CV_PALETTE）。ガラステーマでは
// この5色だけ、うっすら色のついた半透明に上書きされる（index.html:2251〜2260）。
private data class GlassCardStyle(val bg: Color, val border: Color, val text: Color)
private val GlassCardStyles = mapOf(
    "#9dc0f5" to GlassCardStyle(rgba(66, 133, 244, .20f), rgba(66, 133, 244, .55f), Color(0xFFEAF1FF)),
    "#9fe0bd" to GlassCardStyle(rgba(52, 168, 83, .20f), rgba(52, 168, 83, .55f), Color(0xFFEAFFF2)),
    "#f5dc9b" to GlassCardStyle(rgba(251, 188, 5, .18f), rgba(251, 188, 5, .50f), Color(0xFFFFF7E6)),
    "#f5b3ac" to GlassCardStyle(rgba(234, 67, 53, .18f), rgba(234, 67, 53, .50f), Color(0xFFFFEEED)),
    "#c3b6e8" to GlassCardStyle(rgba(154, 107, 216, .20f), rgba(154, 107, 216, .55f), Color(0xFFF4EEFF))
)

// 画面の四隅ににじむ光（body::before / .cv-ov::before と同じ4色・同じ位置）。
private data class GlowSpot(val cx: Float, val cy: Float, val color: Color, val alpha: Float)
private val GlowSpots = listOf(
    GlowSpot(0.14f, 0.08f, Color(0xFF4285F4), .33f),
    GlowSpot(0.86f, 0.14f, Color(0xFFEA4335), .19f),
    GlowSpot(0.74f, 0.92f, Color(0xFF34A853), .24f),
    GlowSpot(0.20f, 0.90f, Color(0xFFFBBC05), .15f)
)

private fun drawGlow(scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    with(scope) {
        val radius = kotlin.math.max(size.width, size.height) * 0.65f
        GlowSpots.forEach { spot ->
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(spot.color.copy(alpha = spot.alpha), spot.color.copy(alpha = 0f)),
                    center = Offset(size.width * spot.cx, size.height * spot.cy),
                    radius = radius
                ),
                radius = radius,
                center = Offset(size.width * spot.cx, size.height * spot.cy)
            )
        }
    }
}

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

    Column(modifier = Modifier.fillMaxSize().background(GlassBgBase).drawBehind { drawGlow(this) }) {
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
                        .offset(x = zone.x.dp, y = zone.y.dp)
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
    val colorHex = card.color
    val glassStyle = colorHex?.lowercase()?.let { GlassCardStyles[it] }
    val bg = glassStyle?.bg ?: colorHex?.let { hexToColor(it) } ?: ColCard
    val borderColor = glassStyle?.border ?: ColCardBorder
    val textColor = glassStyle?.text ?: (if (colorHex != null) ColOnColored else ColOnS)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .offset(x = card.x.dp, y = card.y.dp)
            .width(card.w.dp)
            .background(bg, shape = shape)
            .border(1.5.dp, borderColor, shape)
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
