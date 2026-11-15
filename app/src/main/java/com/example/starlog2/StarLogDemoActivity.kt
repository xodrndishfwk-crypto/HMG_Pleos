package com.example.starlog2

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
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
            setPadding(dp(24), dp(18), dp(24), dp(18))
            setBackgroundColor(Color.rgb(7, 12, 26))
        }
        root.addView(TextView(this).apply {
            text = "STARLOG · 제출 시연"
            textSize = 30f
            setTextColor(Color.rgb(241, 212, 140))
        })
        root.addView(TextView(this).apply {
            text = "DEMO REPLAY · 구미 → 부산 → 복귀"
            textSize = 15f
            setTextColor(Color.LTGRAY)
        })
        state = label("시연 여정을 시작해 주세요.", 21f, Color.WHITE)
        progress = label("기록 0 / 9", 16f, Color.rgb(170, 200, 235))
        root.addView(state)
        root.addView(progress)

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("재생 속도", 14f, Color.LTGRAY))
            listOf(4, 8, 16).forEach { value ->
                addView(Button(this@StarLogDemoActivity).apply {
                    text = "${value}×"
                    setOnClickListener { speed = value; state.text = "${value}× 시연 속도 선택" }
                })
            }
        })

        journeyButton = Button(this).apply {
            text = "여정 시작"
            setOnClickListener { startOrContinue() }
        }
        finishButton = Button(this).apply {
            text = "여정 명시적 종료"
            isEnabled = false
            setOnClickListener { finishJourney() }
        }
        melodyButton = Button(this).apply {
            text = "별자리 회전 · 오선지 · DEMO MOCK 음악"
            isEnabled = false
            setOnClickListener {
                val record = displayedRecord ?: return@setOnClickListener
                startActivity(Intent(this@StarLogDemoActivity, MelodyStudioActivity::class.java).apply {
                    putExtra(MelodyStudioActivity.EXTRA_CONSTELLATION_ID, record.constellationId)
                    putExtra(MelodyStudioActivity.EXTRA_SOURCE_MODE, record.sourceMode.name)
                })
            }
        }
        memoryButton = Button(this).apply {
            text = "별 선택 · 사진 추억"
            isEnabled = false
            setOnClickListener { startActivity(Intent(this@StarLogDemoActivity, StarMemoryActivity::class.java)) }
        }
        root.addView(journeyButton)
        root.addView(finishButton)
        root.addView(melodyButton)
        root.addView(memoryButton)
        constellation = DemoConstellationView()
        root.addView(constellation, LinearLayout.LayoutParams(-1, dp(230)))
        return ScrollView(this).apply { addView(root) }
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
            val points = record?.points?.filter { it.createsNewStar }.orEmpty()
            if (points.isEmpty()) return
            val pad = 28f
            val minLon = points.minOf { it.point.lon }
            val maxLon = points.maxOf { it.point.lon }
            val minLat = points.minOf { it.point.lat }
            val maxLat = points.maxOf { it.point.lat }
            fun x(lon: Double) = pad + ((lon - minLon) / (maxLon - minLon).coerceAtLeast(.001) * (width - pad * 2)).toFloat()
            fun y(lat: Double) = height - pad - ((lat - minLat) / (maxLat - minLat).coerceAtLeast(.001) * (height - pad * 2)).toFloat()
            paint.color = Color.rgb(80, 126, 184); paint.strokeWidth = 4f
            points.filter { it.leg.name == "OUTBOUND" }.zipWithNext().forEach { (a, b) ->
                canvas.drawLine(x(a.point.lon), y(a.point.lat), x(b.point.lon), y(b.point.lat), paint)
            }
            points.firstOrNull { it.label == "부산" }?.let { busan ->
                points.firstOrNull { it.label == "김해" }?.let { gimhae ->
                    paint.color = Color.rgb(229, 200, 136)
                    canvas.drawLine(x(busan.point.lon), y(busan.point.lat), x(gimhae.point.lon), y(gimhae.point.lat), paint)
                }
            }
            points.forEach {
                paint.color = Color.WHITE
                canvas.drawCircle(x(it.point.lon), y(it.point.lat), 9f, paint)
            }
        }
    }
}
