package com.example.starlog2

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.demo.SubmissionDemoJourney
import com.example.starlog2.starlog.gleo.GleoCallbackSender
import com.example.starlog2.starlog.gleo.GleoContract
import com.example.starlog2.starlog.gleo.GleoJourneyActionRequest
import com.example.starlog2.starlog.gleo.GleoJourneyCallbackData
import com.example.starlog2.starlog.journey.JourneyController
import com.example.starlog2.starlog.journey.JourneyControlResponse
import com.example.starlog2.starlog.journey.JourneyRecord
import com.example.starlog2.starlog.journey.JourneyStatus
import java.util.concurrent.Executors
import org.json.JSONObject
import org.json.JSONTokener

/** Submission launcher: deterministic REPLAY, explicit start, and explicit finish. */
class StarLogDemoActivity : AppCompatActivity() {
    private lateinit var controller: JourneyController
    private lateinit var state: TextView
    private lateinit var progress: TextView
    private lateinit var journeyButton: Button
    private lateinit var finishButton: Button
    private lateinit var melodyButton: Button
    private lateinit var memoryButton: Button
    private lateinit var constellation: DemoConstellationView
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var speed = 8
    private var running = false
    private var displayedRecord: JourneyRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        controller = (application as StarlogApplication).journeyController
        setContentView(buildUi())
        render(controller.current())
        handleGleoIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGleoIntent(intent)
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(12))
            setBackgroundColor(BACKGROUND)
        }
        root.addView(buildTopBar(), LinearLayout.LayoutParams(-1, dp(52)))

        val stage = FrameLayout(this).apply {
            background = panelBackground(Color.rgb(5, 9, 17), BORDER, 16)
        }
        constellation = DemoConstellationView()
        stage.addView(constellation, FrameLayout.LayoutParams(-1, -1))
        val stageCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(12), 0)
            addView(label("CONSTELLATION FORGING · DEMO REPLAY", 11f, MUTED).apply {
                letterSpacing = 0.12f
            })
            addView(label("오늘의 별자리 여정", 25f, Color.WHITE).apply {
                typeface = Typeface.DEFAULT_BOLD
            })
        }
        stage.addView(
            stageCopy,
            FrameLayout.LayoutParams(dp(390), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START),
        )
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = panelBackground(PANEL, BORDER, 14)
        }
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        state = label("시연 여정을 시작해 주세요.", 15f, Color.WHITE).apply {
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        progress = label("기록 0 / 9", 13f, GOLD).apply { gravity = Gravity.END }
        statusRow.addView(state, LinearLayout.LayoutParams(0, -2, 1f))
        statusRow.addView(progress, LinearLayout.LayoutParams(dp(300), -2))
        dock.addView(statusRow, LinearLayout.LayoutParams(-1, dp(36)))

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actionRow.addView(label("SPEED", 11f, MUTED), LinearLayout.LayoutParams(dp(54), -2))
        listOf(4, 8, 16).forEach { value ->
            actionRow.addView(demoButton("${value}×", compact = true) {
                speed = value
                state.text = "${value}× 시연 속도 · 음성 START 준비"
            }, LinearLayout.LayoutParams(dp(58), dp(38)).apply { marginEnd = dp(6) })
        }

        journeyButton = demoButton("여정 시작") { startOrContinue() }
        finishButton = demoButton("명시적 종료", accent = true) { finishJourney() }.apply {
            isEnabled = false
        }
        melodyButton = demoButton("별자리 음악") {
            val record = displayedRecord ?: return@demoButton
            startActivity(Intent(this@StarLogDemoActivity, MelodyStudioActivity::class.java).apply {
                putExtra(MelodyStudioActivity.EXTRA_CONSTELLATION_ID, record.constellationId)
                putExtra(MelodyStudioActivity.EXTRA_SOURCE_MODE, record.sourceMode.name)
            })
        }.apply {
            isEnabled = false
        }
        memoryButton = demoButton("기억의 숲") {
            startActivity(Intent(this@StarLogDemoActivity, StarMemoryActivity::class.java))
        }.apply {
            isEnabled = false
        }
        listOf(journeyButton, finishButton, melodyButton, memoryButton).forEach { button ->
            actionRow.addView(button, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(7) })
        }
        dock.addView(actionRow, LinearLayout.LayoutParams(-1, dp(44)))
        root.addView(dock, LinearLayout.LayoutParams(-1, dp(96)).apply { topMargin = dp(8) })
        return root
    }

    private fun buildTopBar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label("✦ StarLog", 18f, Color.WHITE).apply {
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(navTab("여정 · 별자리", selected = true))
        addView(navTab("주행 · 기억의 숲", selected = false))
        addView(navTab("여정 종료 · 오토 음악", selected = false))
        addView(label("●  Ambient Sync · Gold #E5C888", 11f, GOLD).apply {
            gravity = Gravity.CENTER
            background = panelBackground(Color.rgb(10, 15, 24), BORDER, 18)
            setPadding(dp(14), 0, dp(14), 0)
        }, LinearLayout.LayoutParams(dp(238), dp(34)).apply { marginStart = dp(18) })
    }

    private fun navTab(value: String, selected: Boolean) = TextView(this).apply {
        text = value
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(if (selected) Color.WHITE else MUTED)
        background = if (selected) panelBackground(Color.rgb(17, 22, 32), GOLD_DIM, 18) else null
        setPadding(dp(15), 0, dp(15), 0)
    }

    private fun demoButton(
        value: String,
        accent: Boolean = false,
        compact: Boolean = false,
        action: () -> Unit,
    ) = Button(this).apply {
        text = value
        textSize = if (compact) 11f else 12f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(8), 0, dp(8), 0)
        setTextColor(if (accent) Color.rgb(10, 12, 16) else Color.WHITE)
        background = panelBackground(if (accent) GOLD else Color.rgb(12, 17, 27), if (accent) GOLD else BORDER, 12)
        setOnClickListener { action() }
    }

    private fun panelBackground(fill: Int, stroke: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        setStroke(dp(1), stroke)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun startOrContinue() {
        if (running) return
        running = true
        journeyButton.isEnabled = false
        io.execute {
            val started = controller.start(SourceMode.REPLAY)
            runOnUiThread {
                if (started is JourneyControlResponse.Failure) {
                    running = false
                    journeyButton.isEnabled = true
                    state.text = "시연 시작 실패 · ${started.error.messageKey}"
                } else {
                    render((started as JourneyControlResponse.Success).record)
                    scheduleNext()
                }
            }
        }
    }

    private fun scheduleNext() {
        if (!running) return
        val current = controller.current()
        if (current == null || current.points.size >= SubmissionDemoJourney.STOPS.size) {
            running = false
            render(current)
            return
        }
        handler.postDelayed({
            io.execute {
                val result = controller.advance(SourceMode.REPLAY)
                runOnUiThread {
                    if (result is Outcome.Ok) {
                        render(result.value)
                        if (running) scheduleNext()
                    } else {
                        running = false
                        journeyButton.isEnabled = true
                        state.text = "주행 기록 실패 · ${(result as Outcome.Err).error.messageKey}"
                    }
                }
            }
        }, (1600L / speed).coerceAtLeast(80L))
    }

    private fun finishJourney() {
        finishButton.isEnabled = false
        state.text = "별자리 자동 생성 중…"
        io.execute {
            val result = controller.end(SourceMode.REPLAY)
            runOnUiThread {
                when (result) {
                    is JourneyControlResponse.Success -> render(result.record)
                    is JourneyControlResponse.Asking -> {
                        finishButton.isEnabled = result.snapshot.recordedPointCount == SubmissionDemoJourney.STOPS.size
                        state.text = result.message
                    }
                    is JourneyControlResponse.Failure -> {
                        finishButton.isEnabled = true
                        state.text = "종료 실패 · ${result.error.messageKey}"
                    }
                }
            }
        }
    }

    private fun render(record: JourneyRecord?) {
        displayedRecord = record
        if (record == null) {
            progress.text = "기록 0 / ${SubmissionDemoJourney.STOPS.size}"
            constellation.render(null)
            return
        }
        progress.text = "기록 ${record.points.size} / ${SubmissionDemoJourney.STOPS.size} · ${record.points.lastOrNull()?.label ?: "READY"}"
        constellation.render(record)
        when {
            record.status == JourneyStatus.COMPLETED -> {
                state.text = "COMPLETED · 편도 5별 + 복귀 신규 가지 1별"
                journeyButton.text = "완료된 여정 복원됨"
                journeyButton.isEnabled = false
                finishButton.isEnabled = false
                melodyButton.isEnabled = true
                memoryButton.isEnabled = true
            }
            record.points.size == SubmissionDemoJourney.STOPS.size -> {
                state.text = "9개 지점 기록 완료 · 명시적 종료를 눌러 주세요"
                journeyButton.isEnabled = false
                finishButton.isEnabled = true
            }
            else -> {
                state.text = if (running) "ACTIVE · VisitDetector 순차 기록 중 (${speed}×)" else "ACTIVE · 이어서 실행 가능"
                journeyButton.text = if (record.points.isEmpty()) "여정 실행" else "여정 이어서 실행"
                journeyButton.isEnabled = !running
                finishButton.isEnabled = false
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        io.shutdownNow()
        super.onDestroy()
    }

    private fun handleGleoIntent(sourceIntent: Intent) {
        if (sourceIntent.action != GleoContract.ACTION_CONTROL_STARLOG_JOURNEY) return
        val request = runCatching {
            val raw = sourceIntent.getStringExtra(GleoContract.EXTRA_ACTION_PARAM) ?: "{}"
            val decoded = JSONTokener(raw).nextValue()
            val parameters = when (decoded) {
                is JSONObject -> decoded
                is String -> JSONObject(decoded)
                else -> JSONObject()
            }
            GleoJourneyActionRequest.from(
                sourceIntent.action,
                parameters.optString("operation", null),
                parameters.optString("sourceMode", null),
            )
        }.getOrNull()
        if (request == null) {
            val snapshot = controller.status(SourceMode.REPLAY).snapshot
            sendJourneyCallback(
                JourneyControlResponse.Failure(
                    snapshot,
                    "The journey control request parameters are invalid.",
                    com.example.starlog2.contracts.AppError("INVALID_INPUT", "gleo.invalid_journey_request", false, null),
                ),
            )
            return
        }
        if (request.operation.name == "END") {
            running = false
            handler.removeCallbacksAndMessages(null)
        }
        io.execute {
            val response = controller.control(request.operation, request.sourceMode)
            sendJourneyCallback(response)
            runOnUiThread {
                when (response) {
                    is JourneyControlResponse.Success -> {
                        render(response.record)
                        if (request.operation.name == "START" && response.record?.status == JourneyStatus.ACTIVE) {
                            val shouldSchedule = !running
                            running = true
                            journeyButton.isEnabled = false
                            if (shouldSchedule) scheduleNext()
                        }
                    }
                    is JourneyControlResponse.Asking -> {
                        render(response.record)
                        state.text = response.message
                    }
                    is JourneyControlResponse.Failure -> state.text = response.message
                }
            }
        }
    }

    private fun sendJourneyCallback(response: JourneyControlResponse) {
        val data = GleoJourneyCallbackData.from(response)
        GleoCallbackSender.send(
            context = this,
            functionName = GleoContract.ACTION_CONTROL_STARLOG_JOURNEY,
            status = when (response) {
                is JourneyControlResponse.Success -> GleoCallbackSender.Status.SUCCESS
                is JourneyControlResponse.Asking -> GleoCallbackSender.Status.ASKING
                is JourneyControlResponse.Failure -> GleoCallbackSender.Status.FAIL
            },
            message = response.message,
            data = listOf(data),
        )
    }

    override fun onPause() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, dp(6), dp(12), dp(6))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class DemoConstellationView : View(this@StarLogDemoActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var record: JourneyRecord? = null

        fun render(value: JourneyRecord?) { record = value; invalidate() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(5, 9, 17))
            drawStarField(canvas)
            val points = record?.points?.filter { it.createsNewStar }.orEmpty()
            if (points.isEmpty()) {
                drawHome(canvas)
                return
            }
            val padX = width * 0.18f
            val padTop = height * 0.25f
            val padBottom = height * 0.18f
            val minLon = points.minOf { it.point.lon }
            val maxLon = points.maxOf { it.point.lon }
            val minLat = points.minOf { it.point.lat }
            val maxLat = points.maxOf { it.point.lat }
            fun x(lon: Double) = padX + ((lon - minLon) / (maxLon - minLon).coerceAtLeast(.001) * (width - padX * 2)).toFloat()
            fun y(lat: Double) = height - padBottom - ((lat - minLat) / (maxLat - minLat).coerceAtLeast(.001) * (height - padTop - padBottom)).toFloat()
            paint.color = GOLD_DIM
            paint.strokeWidth = dp(2).toFloat()
            points.filter { it.leg.name == "OUTBOUND" }.zipWithNext().forEach { (a, b) ->
                canvas.drawLine(x(a.point.lon), y(a.point.lat), x(b.point.lon), y(b.point.lat), paint)
            }
            points.firstOrNull { it.label == "부산" }?.let { busan ->
                points.firstOrNull { it.label == "김해" }?.let { gimhae ->
                    paint.color = CYAN
                    canvas.drawLine(x(busan.point.lon), y(busan.point.lat), x(gimhae.point.lon), y(gimhae.point.lat), paint)
                }
            }
            points.forEach {
                paint.color = Color.argb(38, 229, 200, 136)
                canvas.drawCircle(x(it.point.lon), y(it.point.lat), dp(18).toFloat(), paint)
                paint.color = GOLD
                canvas.drawCircle(x(it.point.lon), y(it.point.lat), dp(6).toFloat(), paint)
                paint.color = Color.WHITE
                canvas.drawCircle(x(it.point.lon), y(it.point.lat), dp(2).toFloat(), paint)
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = dp(11).toFloat()
                paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                canvas.drawText(it.label, x(it.point.lon), y(it.point.lat) + dp(28), paint)
            }
        }

        private fun drawStarField(canvas: Canvas) {
            paint.typeface = Typeface.DEFAULT
            repeat(86) { index ->
                val x = ((index * 83 + 29) % 997) / 997f * width
                val y = ((index * 47 + 71) % 541) / 541f * height
                val bright = 45 + (index * 17 % 95)
                paint.color = Color.argb(bright, 205, 220, 236)
                canvas.drawCircle(x, y, if (index % 13 == 0) 2.2f else 1.1f, paint)
            }
        }

        private fun drawHome(canvas: Canvas) {
            val cx = width * 0.58f
            val cy = height * 0.53f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.argb(130, 125, 108, 72)
            canvas.drawCircle(cx, cy, height * 0.25f, paint)
            canvas.drawCircle(cx, cy, height * 0.18f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(45, 229, 200, 136)
            canvas.drawCircle(cx, cy, dp(28).toFloat(), paint)
            paint.color = GOLD
            canvas.drawCircle(cx, cy, dp(7).toFloat(), paint)
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            paint.textSize = dp(31).toFloat()
            paint.color = Color.WHITE
            canvas.drawText("StarLog", cx, cy + dp(52), paint)
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.textSize = dp(12).toFloat()
            paint.color = MUTED
            canvas.drawText("오늘의 밤하늘을 드라이브합니다", cx, cy + dp(78), paint)
        }
    }

    private companion object {
        val BACKGROUND = Color.rgb(4, 7, 13)
        val PANEL = Color.rgb(9, 13, 21)
        val BORDER = Color.rgb(36, 42, 54)
        val MUTED = Color.rgb(132, 142, 158)
        val GOLD = Color.rgb(229, 200, 136)
        val GOLD_DIM = Color.rgb(118, 101, 67)
        val CYAN = Color.rgb(115, 207, 221)
    }
}
