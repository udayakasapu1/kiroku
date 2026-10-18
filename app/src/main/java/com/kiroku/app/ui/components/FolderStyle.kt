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
            motif = Color(0xFF7DD3FC).copy(alpha = 0.22f),
            iconBackground = Color(0xFF7DD3FC).copy(alpha = 0.16f),
            border = Color(0xFF475569),
        )
    } else {
        FolderPalette(
            top = Color(0xFFE0F2FE),
            bottom = Color(0xFFBAE6FD),
            content = Color(0xFF0C4A6E),
            secondary = Color(0xFF075985),
            motif = Color(0xFF0EA5E9).copy(alpha = 0.20f),
            iconBackground = Color.White.copy(alpha = 0.65f),
            border = Color(0xFF7DD3FC),
        )
    }

fun linkCountLabel(count: Int): String = when (count) {
    0 -> "Empty"
    1 -> "1 link"
    else -> "$count links"
}

/** A folder outline: a tab on the top-left, rounded corners. */
class FolderShape(
    private val tabWidthFraction: Float = 0.46f,
    private val tabHeight: Dp = 12.dp,
    private val corner: Dp = 18.dp,
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

/** A folder tile on the home screen. */
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
            .aspectRatio(0.95f)
            .shadow(elevation = 3.dp, shape = shape, clip = false)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
            .border(BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)), shape)
            .clickable(onClickLabel = "Open $name", onClick = onClick),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val flapTop = size.height * 0.40f
            drawRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(0f, flapTop),
                size = Size(size.width, size.height - flapTop),
            )
            drawLine(
                color = palette.border.copy(alpha = 0.6f),
                start = Offset(0f, flapTop),
                end = Offset(size.width, flapTop),
                strokeWidth = 1.dp.toPx(),
            )
            drawMotif(icon.motif, palette.motif)
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 14.dp, top = 24.dp, bottom = 14.dp),
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(palette.iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon.vector, contentDescription = null, tint = palette.content, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = palette.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(linkCountLabel(links), style = MaterialTheme.typography.labelMedium, color = palette.secondary)
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
        Canvas(Modifier.matchParentSize()) { drawMotif(icon.motif, palette.motif) }
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
// Folder illustrations. Drawn mostly on the right-hand side so the text stays readable.
// ---------------------------------------------------------------------------------------------

fun DrawScope.drawMotif(motif: Motif, color: Color) {
    val w = size.width
    val h = size.height
    val unit = minOf(w, h)
    when (motif) {
        Motif.ASSEMBLY -> {
            val step = unit * 0.12f
            var x = w * 0.42f
            while (x < w + h) {
                drawLine(
                    color = color,
                    start = Offset(x, h),
                    end = Offset(x + h * 0.55f, 0f),
                    strokeWidth = step * 0.32f,
                    cap = StrokeCap.Round,
                )
                x += step
            }
        }

        Motif.GEARS -> {
            drawGear(Offset(w * 0.80f, h * 0.36f), unit * 0.22f, 10, color)
            drawGear(Offset(w * 0.95f, h * 0.78f), unit * 0.15f, 8, color)
        }

        Motif.HEX_NUTS -> {
            drawNut(Offset(w * 0.80f, h * 0.36f), unit * 0.19f, color)
            drawNut(Offset(w * 0.92f, h * 0.76f), unit * 0.12f, color)
            drawNut(Offset(w * 0.60f, h * 0.20f), unit * 0.08f, color)
        }

        Motif.CIRCUIT -> {
            val sw = unit * 0.022f
            fun trace(points: List<Offset>) {
                for (i in 0 until points.size - 1) {
                    drawLine(color, points[i], points[i + 1], strokeWidth = sw, cap = StrokeCap.Round)
                }
                drawCircle(color, radius = sw * 2.2f, center = points.last())
                drawCircle(color, radius = sw * 2.2f, center = points.first(), style = Stroke(sw))
            }
            trace(listOf(Offset(w * 0.50f, h * 0.16f), Offset(w * 0.68f, h * 0.16f), Offset(w * 0.76f, h * 0.26f), Offset(w * 0.96f, h * 0.26f)))
            trace(listOf(Offset(w * 0.58f, h * 0.36f), Offset(w * 0.70f, h * 0.36f)))
            trace(listOf(Offset(w * 0.94f, h * 0.50f), Offset(w * 0.94f, h * 0.94f)))
            trace(listOf(Offset(w * 0.62f, h * 0.90f), Offset(w * 0.70f, h * 0.80f), Offset(w * 0.70f, h * 0.68f)))
            val chip = unit * 0.22f
            val chipTopLeft = Offset(w * 0.70f, h * 0.40f)
            drawRoundRect(
                color = color,
                topLeft = chipTopLeft,
                size = Size(chip, chip),
                cornerRadius = CornerRadius(chip * 0.12f),
                style = Stroke(sw * 1.4f),
            )
            for (i in 1..3) {
                val y = chipTopLeft.y + chip * i / 4f
                drawLine(color, Offset(chipTopLeft.x - chip * 0.15f, y), Offset(chipTopLeft.x, y), strokeWidth = sw)
                drawLine(color, Offset(chipTopLeft.x + chip, y), Offset(chipTopLeft.x + chip * 1.15f, y), strokeWidth = sw)
            }
        }

        Motif.RULER -> {
            rotate(degrees = -24f, pivot = Offset(w * 0.78f, h * 0.45f)) {
                val left = w * 0.42f
                val top = h * 0.30f
                val bandH = unit * 0.24f
                val right = w * 1.4f
                val sw = unit * 0.02f
                drawRect(color, topLeft = Offset(left, top), size = Size(right - left, bandH), style = Stroke(sw))
                var x = left + unit * 0.05f
                var i = 0
                while (x < right) {
                    val len = if (i % 5 == 0) bandH * 0.55f else bandH * 0.28f
                    drawLine(color, Offset(x, top), Offset(x, top + len), strokeWidth = sw * 0.8f)
                    x += unit * 0.045f
                    i++
                }
            }
        }

        Motif.ORG_CHART -> {
            val box = Size(unit * 0.17f, unit * 0.11f)
            val sw = unit * 0.022f
            val root = Offset(w * 0.76f, h * 0.20f)
            val kids = listOf(Offset(w * 0.58f, h * 0.56f), Offset(w * 0.76f, h * 0.56f), Offset(w * 0.94f, h * 0.56f))
            val busY = h * 0.38f
            drawLine(color, Offset(root.x, root.y + box.height / 2), Offset(root.x, busY), strokeWidth = sw)
            drawLine(color, Offset(kids.first().x, busY), Offset(kids.last().x, busY), strokeWidth = sw)
            kids.forEach { drawLine(color, Offset(it.x, busY), Offset(it.x, it.y - box.height / 2), strokeWidth = sw) }
            val grandchild = Offset(w * 0.94f, h * 0.84f)
            drawLine(color, Offset(kids.last().x, kids.last().y + box.height / 2), Offset(grandchild.x, grandchild.y - box.height / 2), strokeWidth = sw)
            (listOf(root, grandchild) + kids).forEach { c ->
                drawRoundRect(
                    color = color,
                    topLeft = Offset(c.x - box.width / 2, c.y - box.height / 2),
                    size = box,
                    cornerRadius = CornerRadius(box.height * 0.3f),
                )
            }
        }

        // Concentric arcs from the top-right corner (reading, ideas, learning, world).
        Motif.ARCS -> {
            val center = Offset(w * 1.02f, h * 0.0f)
            val sw = unit * 0.035f
            var r = unit * 0.18f
            while (r < unit * 0.95f) {
                drawCircle(color, radius = r, center = center, style = Stroke(sw))
                r += unit * 0.14f
            }
        }

        // A grid of dots on the right (general folders).
        Motif.DOTS -> {
            val step = unit * 0.11f
            val radius = unit * 0.022f
            var y = h * 0.14f
            while (y < h * 0.98f) {
                var x = w * 0.52f
                while (x < w * 0.98f) {
                    drawCircle(color, radius = radius, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }

        Motif.DASHED -> {
            val inset = unit * 0.07f
            val dash = unit * 0.06f
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, h * 0.16f),
                size = Size(w - inset * 2, h * 0.84f - inset),
                cornerRadius = CornerRadius(unit * 0.08f),
                style = Stroke(width = unit * 0.02f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f))),
            )
        }
    }
}

private fun DrawScope.drawGear(center: Offset, radius: Float, teeth: Int, color: Color) {
    val toothW = radius * 0.34f
    val toothH = radius * 0.30f
    for (i in 0 until teeth) {
        rotate(degrees = i * 360f / teeth, pivot = center) {
            drawRect(
                color = color,
                topLeft = Offset(center.x - toothW / 2, center.y - radius - toothH * 0.7f),
                size = Size(toothW, toothH * 1.4f),
            )
        }
    }
    drawCircle(color, radius = radius, center = center, style = Stroke(width = radius * 0.32f))
    drawCircle(color, radius = radius * 0.22f, center = center)
}

private fun DrawScope.drawNut(center: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 6) {
        val angle = Math.toRadians(60.0 * i + 30.0)
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Stroke(width = radius * 0.22f, join = StrokeJoin.Round))
    drawCircle(color, radius = radius * 0.42f, center = center, style = Stroke(width = radius * 0.18f))
}
