package com.example.starlog2.starlog.melody

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Canvas renderer based on the supplied StarLog IVI score screen: dark field, gold score, quiet labels. */
class MelodyCanvasView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var source: List<MelodyStar> = emptyList()
    private var melody: BaseMelody? = null
    private var message: String = "저장된 별자리를 불러오는 중입니다."

    fun render(sourceStars: List<MelodyStar>, value: BaseMelody?, emptyMessage: String? = null) {
        source = sourceStars
        melody = value
        message = emptyMessage ?: ""
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(BACKGROUND)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        label(canvas, "TODAY TRIP CONSTELLATION · MELODY", w * 0.035f, h * 0.07f, h * 0.020f, MUTED)
        label(canvas, "별자리 고유 멜로디 스튜디오", w * 0.035f, h * 0.12f, h * 0.037f, Color.WHITE)
        val current = melody
        if (source.isEmpty() || current == null) {
            label(canvas, message.ifBlank { "표시할 멜로디가 없습니다." }, w * 0.5f, h * 0.48f, h * 0.026f, MUTED, Paint.Align.CENTER)
            return
        }

        val left = RectF(w * 0.035f, h * 0.19f, w * 0.34f, h * 0.76f)
        val right = RectF(w * 0.39f, h * 0.19f, w * 0.965f, h * 0.76f)
        panel(canvas, left)
        panel(canvas, right)
        label(canvas, "ORIGINAL CONSTELLATION", left.left + 24f, left.top + 40f, h * 0.018f, MUTED)
        label(canvas, "ROTATED SCORE · ${current.rotationAngleDeg}°", right.left + 24f, right.top + 40f, h * 0.018f, MUTED)
        drawOriginal(canvas, left, source)
        drawStaff(canvas, right, current)
        drawSummary(canvas, RectF(w * 0.035f, h * 0.80f, w * 0.965f, h * 0.95f), current)
    }

    private fun drawOriginal(canvas: Canvas, bounds: RectF, stars: List<MelodyStar>) {
        val plot = RectF(bounds.left + 34f, bounds.top + 75f, bounds.right - 34f, bounds.bottom - 45f)
        val minX = stars.minOf { it.originalX }
        val maxX = stars.maxOf { it.originalX }
        val minY = stars.minOf { it.originalY }
        val maxY = stars.maxOf { it.originalY }
        val scale = min(plot.width() / max(1e-9, maxX - minX), plot.height() / max(1e-9, maxY - minY)).toFloat()
        fun point(star: MelodyStar): Pair<Float, Float> =
            (plot.centerX() + ((star.originalX - (minX + maxX) / 2.0) * scale).toFloat()) to
                (plot.centerY() + ((star.originalY - (minY + maxY) / 2.0) * scale).toFloat())
        val ordered = stars.sortedBy { it.connectionOrder }
        paint.color = GOLD_DIM
        paint.strokeWidth = 2f
        ordered.zipWithNext().forEach { (a, b) ->
            val ap = point(a)
            val bp = point(b)
            canvas.drawLine(ap.first, ap.second, bp.first, bp.second, paint)
        }
        ordered.forEach { star ->
            val p = point(star)
            drawStar(canvas, p.first, p.second, 6f)
            label(canvas, (star.connectionOrder + 1).toString(), p.first, p.second + 28f, 18f, MUTED, Paint.Align.CENTER)
        }
    }

    private fun drawStaff(canvas: Canvas, bounds: RectF, melody: BaseMelody) {
        val plotLeft = bounds.left + 55f
        val plotRight = bounds.right - 30f
        val staffBottom = bounds.centerY() + 72f
        val spacing = min(42f, bounds.height() / 10f)
        paint.strokeWidth = 1.5f
        paint.color = GRID
        repeat(5) { line ->
            val y = staffBottom - line * spacing
            canvas.drawLine(plotLeft, y, plotRight, y, paint)
        }
        label(canvas, "E4", plotLeft - 16f, staffBottom + 6f, 16f, MUTED, Paint.Align.RIGHT)
        label(canvas, "F5", plotLeft - 16f, staffBottom - 4 * spacing + 6f, 16f, MUTED, Paint.Align.RIGHT)

        val ordered = melody.notes.sortedWith(compareBy<MelodyNote> { it.startBeat }.thenBy { it.connectionOrder })
        val maxBeat = (ordered.maxOfOrNull { it.startBeat } ?: 1.0).coerceAtLeast(1.0)
        fun x(note: MelodyNote) = plotLeft + 35f + (note.startBeat / maxBeat).toFloat() * (plotRight - plotLeft - 70f)
        fun y(midi: Int): Float {
            val e4Index = diatonicIndex(64)
            return staffBottom - (diatonicIndex(midi) - e4Index) * (spacing / 2f)
        }
        paint.color = GOLD_DIM
        paint.strokeWidth = 2f
        ordered.zipWithNext().forEach { (a, b) -> canvas.drawLine(x(a), y(a.correctedMidi), x(b), y(b.correctedMidi), paint) }
        ordered.forEach { note ->
            val px = x(note)
            val rawY = y(note.originalMidi)
            val correctedY = y(note.correctedMidi)
            if (rawY != correctedY) {
                paint.style = Paint.Style.STROKE
                paint.color = CYAN_DIM
                canvas.drawCircle(px, rawY, 9f, paint)
                paint.style = Paint.Style.FILL
                paint.strokeWidth = 1f
                canvas.drawLine(px, rawY, px, correctedY, paint)
            }
            drawLedgerLines(canvas, px, correctedY, staffBottom, spacing)
            drawStar(canvas, px, correctedY, if (note.chordGroupId == null) 6f else 9f)
            label(
                canvas = canvas,
                text = ArrangementPromptBuilder.midiName(note.correctedMidi),
                x = px,
                y = bounds.bottom - 28f,
                size = 16f,
                color = if (note.chordGroupId == null) MUTED else GOLD,
                align = Paint.Align.CENTER,
            )
        }
    }

    private fun drawLedgerLines(canvas: Canvas, x: Float, y: Float, staffBottom: Float, spacing: Float) {
        paint.color = GRID
        paint.strokeWidth = 1.5f
        val top = staffBottom - 4 * spacing
        if (y > staffBottom + spacing / 2f) {
            var line = staffBottom + spacing
            while (line <= y + 2f) {
                canvas.drawLine(x - 16f, line, x + 16f, line, paint)
                line += spacing
            }
        } else if (y < top - spacing / 2f) {
            var line = top - spacing
            while (line >= y - 2f) {
                canvas.drawLine(x - 16f, line, x + 16f, line, paint)
                line -= spacing
            }
        }
    }

    private fun drawSummary(canvas: Canvas, bounds: RectF, melody: BaseMelody) {
        panel(canvas, bounds)
        label(
            canvas, "${melody.tempoBpm} BPM · ${melody.beatsPerBar}/${melody.beatUnit}",
            bounds.left + 28f, bounds.top + 38f, 18f, GOLD,
        )
        label(
            canvas, "${melody.notes.size} NOTES  ·  ${melody.chords.size} CHORDS",
            bounds.left + 28f, bounds.top + 75f, 17f, Color.WHITE,
        )
        label(
            canvas, "자연스러움 ${(melody.naturalnessScore * 100).toInt()}%",
            bounds.centerX(), bounds.top + 45f, 18f, Color.WHITE, Paint.Align.CENTER,
        )
        label(
            canvas, "최대 유사도 ${(melody.maxSimilarity * 100).toInt()}%",
            bounds.centerX(), bounds.top + 79f, 16f, MUTED, Paint.Align.CENTER,
        )
        label(
            canvas, melody.generationStatus.name,
            bounds.right - 28f, bounds.top + 58f, 20f, GOLD, Paint.Align.RIGHT,
        )
    }

    private fun panel(canvas: Canvas, bounds: RectF) {
        paint.style = Paint.Style.FILL
        paint.color = PANEL
        canvas.drawRoundRect(bounds, 22f, 22f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = BORDER
        canvas.drawRoundRect(bounds, 22f, 22f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawStar(canvas: Canvas, x: Float, y: Float, radius: Float) {
        paint.color = Color.argb(35, 229, 200, 136)
        canvas.drawCircle(x, y, radius * 3.4f, paint)
        paint.color = GOLD
        canvas.drawCircle(x, y, radius, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, radius * 0.34f, paint)
    }

    private fun label(canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        canvas.drawText(text, x, y, paint)
    }

    private fun diatonicIndex(midi: Int): Int {
        val octave = midi / 12 - 1
        val pitchClass = Math.floorMod(midi, 12)
        val degree = when (pitchClass) {
            0 -> 0
            2 -> 1
            4 -> 2
            5 -> 3
            7 -> 4
            9 -> 5
            11 -> 6
            else -> nearestDegree(pitchClass)
        }
        return octave * 7 + degree
    }

    private fun nearestDegree(pitchClass: Int): Int = listOf(0, 2, 4, 5, 7, 9, 11)
        .indices.minBy { abs(listOf(0, 2, 4, 5, 7, 9, 11)[it] - pitchClass) }

    private companion object {
        val BACKGROUND = Color.rgb(7, 9, 14)
        val PANEL = Color.rgb(12, 15, 23)
        val BORDER = Color.rgb(35, 39, 50)
        val GRID = Color.rgb(35, 39, 47)
        val MUTED = Color.rgb(119, 124, 140)
        val GOLD = Color.rgb(229, 200, 136)
        val GOLD_DIM = Color.rgb(125, 108, 72)
        val CYAN_DIM = Color.rgb(64, 132, 151)
    }
}
