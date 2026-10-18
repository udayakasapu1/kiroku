package com.kiroku.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** The illustration drawn on a folder; picked by the folder's icon. */
enum class Motif { ASSEMBLY, GEARS, HEX_NUTS, CIRCUIT, RULER, ORG_CHART, ARCS, DOTS, DASHED }

data class FolderIcon(val key: String, val label: String, val vector: ImageVector, val motif: Motif)

/** Icons a folder can use. The key is what's stored in the database. */
object FolderIcons {
    val all = listOf(
        FolderIcon("folder", "Folder", Icons.Filled.Folder, Motif.DOTS),
        FolderIcon("car", "Vehicle", Icons.Filled.DirectionsCar, Motif.ASSEMBLY),
        FolderIcon("factory", "Manufacturing", Icons.Filled.PrecisionManufacturing, Motif.GEARS),
        FolderIcon("wrench", "Service", Icons.Filled.Build, Motif.HEX_NUTS),
        FolderIcon("code", "Code", Icons.Filled.Code, Motif.CIRCUIT),
        FolderIcon("toolbox", "Tools", Icons.Filled.Handyman, Motif.RULER),
        FolderIcon("org", "Organisation", Icons.Filled.AccountTree, Motif.ORG_CHART),
        FolderIcon("bolt", "Energy", Icons.Filled.Bolt, Motif.ASSEMBLY),
        FolderIcon("battery", "Battery", Icons.Filled.BatteryChargingFull, Motif.CIRCUIT),
        FolderIcon("science", "Research", Icons.Filled.Science, Motif.HEX_NUTS),
        FolderIcon("book", "Reading", Icons.Filled.AutoStories, Motif.ARCS),
        FolderIcon("lightbulb", "Ideas", Icons.Filled.Lightbulb, Motif.ARCS),
        FolderIcon("work", "Work", Icons.Filled.Work, Motif.DOTS),
        FolderIcon("school", "Learning", Icons.Filled.School, Motif.ARCS),
        FolderIcon("globe", "World", Icons.Filled.Public, Motif.ARCS),
        FolderIcon("star", "Favourites", Icons.Filled.Star, Motif.DOTS),
    )

    val unsorted = FolderIcon("unsorted", "Unsorted", Icons.Outlined.FolderOpen, Motif.DASHED)

    fun of(key: String?): FolderIcon = all.firstOrNull { it.key == key } ?: all.first()
}

/** One shade for every folder: sky blue on light, slate + sky on dark. */
data class FolderPalette(
    val top: Color,
    val bottom: Color,
    val content: Color,
    val secondary: Color,
    val motif: Color,
    val iconBackground: Color,
    val border: Color,
)

@Composable
fun folderPalette(): FolderPalette =
    if (isSystemInDarkTheme()) {
        FolderPalette(
            top = Color(0xFF334155),
            bottom = Color(0xFF2A3647),
            content = Color(0xFFF8FAFC),
            secondary = Color(0xFFCBD5E1),
            motif = Color(0xFF7DD3FC).copy(alpha = 0.30f),
            iconBackground = Color(0xFF7DD3FC).copy(alpha = 0.16f),
            border = Color(0xFF475569),
        )
    } else {
        FolderPalette(
            top = Color(0xFFF0F9FF),
            bottom = Color(0xFFE0F2FE),
            content = Color(0xFF0C4A6E),
            secondary = Color(0xFF075985),
            motif = Color(0xFF0EA5E9).copy(alpha = 0.32f),
            iconBackground = Color.White.copy(alpha = 0.65f),
            border = Color(0xFFBAE6FD),
        )
    }

fun linkCountLabel(count: Int): String = when (count) {
    0 -> "Empty"
    1 -> "1 link"
    else -> "$count links"
}

/** A folder outline: a tab on the top-left, rounded corners. */
class FolderShape(
    private val tabWidthFraction: Float = 0.42f,
    private val tabHeight: Dp = 7.dp,
    private val corner: Dp = 14.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val th = with(density) { tabHeight.toPx() }
        val r = with(density) { corner.toPx() }.coerceAtMost(minOf(w, h) / 4f)
        val tw = w * tabWidthFraction
        val path = Path().apply {
            moveTo(0f, r)
            cubicTo(0f, 0f, 0f, 0f, r, 0f)
            lineTo(tw - th * 1.5f, 0f)
            cubicTo(tw - th * 0.5f, 0f, tw - th * 0.5f, th, tw + th * 0.5f, th)
            lineTo(w - r, th)
            cubicTo(w, th, w, th, w, th + r)
            lineTo(w, h - r)
            cubicTo(w, h, w, h, w - r, h)
            lineTo(r, h)
            cubicTo(0f, h, 0f, h, 0f, h - r)
            close()
        }
        return Outline.Generic(path)
    }
}

/** A compact folder tile on the home screen (three per row on a phone). */
@Composable
fun FolderCard(
    name: String,
    icon: FolderIcon,
    links: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = folderPalette()
    val shape = remember { FolderShape() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
            .border(BorderStroke(1.dp, palette.border), shape)
            .clickable(onClickLabel = "Open $name", onClick = onClick),
    ) {
        Canvas(Modifier.matchParentSize()) { drawMotif(icon.motif, palette.motif, 1.2.dp.toPx()) }
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 10.dp),
        ) {
            Icon(icon.vector, contentDescription = null, tint = palette.content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.weight(1f))
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = palette.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(linkCountLabel(links), style = MaterialTheme.typography.labelSmall, color = palette.secondary)
        }
    }
}

/** Banner at the top of a folder screen, in the same shade as the folder tiles. */
@Composable
fun FolderHeader(
    name: String,
    icon: FolderIcon,
    links: Int,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val palette = folderPalette()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom))),
    ) {
        Canvas(Modifier.matchParentSize()) { drawMotif(icon.motif, palette.motif, 1.4.dp.toPx()) }
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 4.dp, bottom = 20.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides palette.content) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.weight(1f))
                    actions()
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(palette.iconBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon.vector, contentDescription = null, tint = palette.content, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.headlineSmall, color = palette.content)
                    Text(linkCountLabel(links), style = MaterialTheme.typography.bodySmall, color = palette.secondary)
                }
            }
        }
    }
}

/** Small "icon + folder name" label used on cards in search results and the archive. */
@Composable
fun FolderLabel(name: String, icon: FolderIcon, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon.vector, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.size(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------------------------
// Folder illustrations: thin, faint line drawings tucked into the top-right corner.
// ---------------------------------------------------------------------------------------------

/** [stroke] is the line width in px. */
fun DrawScope.drawMotif(motif: Motif, color: Color, stroke: Float) {
    val w = size.width
    val h = size.height
    // Drawing box: top-right corner, square, about 40% of the tile's width.
    val box = minOf(w * 0.40f, h * 0.55f)
    val left = w - box - minOf(w, h) * 0.08f
    val top = minOf(w, h) * 0.14f
    fun p(x: Float, y: Float) = Offset(left + box * x, top + box * y)
    val line = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (motif) {
        Motif.ASSEMBLY -> for (i in 0..2) {
            val o = i * 0.28f
            drawLine(color, p(0.15f + o, 0.95f), p(0.45f + o, 0.05f), stroke, StrokeCap.Round)
        }

        Motif.GEARS -> {
            val c = p(0.5f, 0.5f)
            val r = box * 0.30f
            drawCircle(color, r, c, style = line)
            drawCircle(color, r * 0.35f, c, style = line)
            for (i in 0 until 8) {
                val a = Math.toRadians(45.0 * i)
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                drawLine(color, Offset(c.x + ca * r, c.y + sa * r), Offset(c.x + ca * r * 1.35f, c.y + sa * r * 1.35f), stroke, StrokeCap.Round)
            }
        }

        Motif.HEX_NUTS -> {
            val c = p(0.5f, 0.5f)
            val r = box * 0.40f
            val path = Path()
            for (i in 0 until 6) {
                val a = Math.toRadians(60.0 * i + 30.0)
                val x = c.x + r * cos(a).toFloat()
                val y = c.y + r * sin(a).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color, style = line)
            drawCircle(color, r * 0.42f, c, style = line)
        }

        Motif.CIRCUIT -> {
            fun trace(points: List<Offset>) {
                for (i in 0 until points.size - 1) drawLine(color, points[i], points[i + 1], stroke, StrokeCap.Round)
                drawCircle(color, stroke * 1.8f, points.last())
            }
            trace(listOf(p(0.05f, 0.25f), p(0.45f, 0.25f), p(0.65f, 0.45f), p(0.95f, 0.45f)))
            trace(listOf(p(0.05f, 0.65f), p(0.35f, 0.65f), p(0.5f, 0.85f), p(0.95f, 0.85f)))
        }

        Motif.RULER -> {
            drawLine(color, p(0.0f, 0.75f), p(1.0f, 0.75f), stroke, StrokeCap.Round)
            for (i in 0..8) {
                val x = 0.05f + i * 0.1125f
                val len = if (i % 4 == 0) 0.40f else 0.20f
                drawLine(color, p(x, 0.75f), p(x, 0.75f - len), stroke, StrokeCap.Round)
            }
        }

        Motif.ORG_CHART -> {
            val bw = box * 0.26f
            val bh = box * 0.16f
            fun node(c: Offset) = drawRoundRect(
                color, Offset(c.x - bw / 2, c.y - bh / 2), Size(bw, bh), CornerRadius(bh * 0.3f), style = line,
            )
            val root = p(0.5f, 0.15f)
            val a = p(0.17f, 0.80f)
            val b = p(0.83f, 0.80f)
            val busY = top + box * 0.48f
            drawLine(color, Offset(root.x, root.y + bh / 2), Offset(root.x, busY), stroke)
            drawLine(color, Offset(a.x, busY), Offset(b.x, busY), stroke)
            drawLine(color, Offset(a.x, busY), Offset(a.x, a.y - bh / 2), stroke)
            drawLine(color, Offset(b.x, busY), Offset(b.x, b.y - bh / 2), stroke)
            node(root); node(a); node(b)
        }

        Motif.ARCS -> {
            val corner = Offset(w, 0f)
            for (i in 1..3) drawCircle(color, box * 0.35f * i, corner, style = line)
        }

        Motif.DOTS -> for (row in 0..2) for (col in 0..2) {
            drawCircle(color, stroke * 1.4f, p(0.2f + col * 0.3f, 0.2f + row * 0.3f))
        }

        Motif.DASHED -> {
            val inset = minOf(w, h) * 0.06f
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, inset * 2f),
                size = Size(w - inset * 2, h - inset * 3f),
                cornerRadius = CornerRadius(inset * 1.5f),
                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 5, stroke * 4))),
            )
        }
    }
}
