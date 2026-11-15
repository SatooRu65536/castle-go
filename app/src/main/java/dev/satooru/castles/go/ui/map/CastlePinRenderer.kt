package dev.satooru.castles.go.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import androidx.core.graphics.PathParser
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlin.math.max

/** 地図のピンの画像．[anchorU] は画像の幅に対する，円の中心の位置（0〜1）． */
class PinImage(val descriptor: BitmapDescriptor, val anchorU: Float)

/**
 * castle-wiki の地図に合わせた城のピン．規模（1〜6）に応じて円の半径を 5.5〜14 dp で変え，
 * 規模 5 以上の城だけ，円の中に城のアイコンを白で描く．城名は円の右に，白い縁取りつきで描く．
 */
class CastlePinRenderer(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val cache = LruCache<String, PinImage>(700)
    private val glyph: List<Pair<Path, Boolean>> = CASTLE_ICON_PATHS.map { (d, evenOdd) ->
        PathParser.createPathFromPathData(d).apply {
            fillType = if (evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
        } to evenOdd
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = WHITE
        strokeWidth = STROKE_DP * density
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = WHITE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TEXT_DP * density
        color = TEXT_COLOR
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TEXT_DP * density
        color = WHITE
        style = Paint.Style.STROKE
        strokeWidth = HALO_DP * 2 * density
        strokeJoin = Paint.Join.ROUND
    }

    /** [label] が NULL のときは城名を描かない． */
    fun image(scale: Int, selected: Boolean, label: String?): PinImage {
        val key = "$scale|$selected|${label.orEmpty()}"
        return cache.get(key) ?: render(scale, selected, label).also { cache.put(key, it) }
    }

    private fun render(scale: Int, selected: Boolean, label: String?): PinImage {
        val s = scale.coerceIn(1, 6)
        val radius = (MIN_RADIUS_DP + (MAX_RADIUS_DP - MIN_RADIUS_DP) * (s - 1) / 5f) * density
        val stroke = STROKE_DP * density
        val diameter = 2 * (radius + stroke)
        val gap = LABEL_GAP_DP * density
        val halo = HALO_DP * density

        val textWidth = if (label != null) textPaint.measureText(label) else 0f
        val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
        val width = if (label != null) diameter + gap + textWidth + halo * 2 else diameter
        val height = max(diameter, if (label != null) textHeight + halo * 2 else 0f)

        val bitmap = Bitmap.createBitmap(width.toInt() + 1, height.toInt() + 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = diameter / 2
        val cy = height / 2

        fillPaint.color = if (selected) SELECTED_COLOR else CIRCLE_COLOR
        canvas.drawCircle(cx, cy, radius + stroke / 2, fillPaint)
        canvas.drawCircle(cx, cy, radius, strokePaint)

        // 規模 5 以上の城だけ，円の中に城のアイコンを描く．
        if (s >= GLYPH_MIN_SCALE) {
            val side = radius * 1.5f
            canvas.save()
            canvas.translate(cx - side / 2, cy - side / 2)
            canvas.scale(side / 100f, side / 100f)
            glyph.forEach { (path, _) -> canvas.drawPath(path, glyphPaint) }
            canvas.restore()
        }

        if (label != null) {
            val x = diameter + gap
            val y = cy - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2
            canvas.drawText(label, x, y, haloPaint)
            canvas.drawText(label, x, y, textPaint)
        }
        return PinImage(BitmapDescriptorFactory.fromBitmap(bitmap), cx / bitmap.width)
    }

    private companion object {
        const val MIN_RADIUS_DP = 5.5f
        const val MAX_RADIUS_DP = 14f
        const val STROKE_DP = 2f
        const val GLYPH_MIN_SCALE = 5
        const val LABEL_GAP_DP = 4f
        const val TEXT_DP = 13f
        const val HALO_DP = 2f
        const val WHITE = 0xFFFFFFFF.toInt()
        const val CIRCLE_COLOR = 0xFF292524.toInt()
        const val SELECTED_COLOR = 0xFFB91C1C.toInt()
        const val TEXT_COLOR = 0xFF1C1917.toInt()
    }
}
