package com.example.starlog2

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.starlog2.contracts.MemoryStar
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.melody.BaseMelody
import com.example.starlog2.starlog.melody.ArrangementGenre
import com.example.starlog2.starlog.melody.ArrangementInstrument
import com.example.starlog2.starlog.melody.ArrangementPreferences
import com.example.starlog2.starlog.melody.ArrangementPromptBuilder
import com.example.starlog2.starlog.melody.ArrangementRhythm
import com.example.starlog2.starlog.melody.MelodyCanvasView
import com.example.starlog2.starlog.melody.MelodyConfig
import com.example.starlog2.starlog.melody.MelodyGenerator
import com.example.starlog2.starlog.melody.MelodyKey
import com.example.starlog2.starlog.melody.MelodyPreviewPlayer
import com.example.starlog2.starlog.melody.MelodyStar
import com.example.starlog2.starlog.model.Constellation
import com.example.starlog2.starlog.model.ConstellationMember
import com.example.starlog2.starlog.storage.SqliteMemoryRepository
import java.util.concurrent.Executors
import kotlin.math.cos

class MelodyStudioActivity : AppCompatActivity() {
    private lateinit var canvas: MelodyCanvasView
    private lateinit var stateLabel: TextView
    private lateinit var angleLabel: TextView
    private lateinit var angleSeek: SeekBar
    private lateinit var autoButton: Button
    private lateinit var playButton: Button
    private lateinit var confirmButton: Button
    private lateinit var arrangeButton: Button
    private lateinit var octaveToggle: CheckBox
    private lateinit var chordToggle: CheckBox
    private lateinit var thresholdSeek: SeekBar
    private lateinit var keySpinner: Spinner
    private lateinit var resetButton: Button
    private val repository by lazy { SqliteMemoryRepository(applicationContext) }
    private val io = Executors.newSingleThreadExecutor()
    private val player = MelodyPreviewPlayer()
    private var constellationId: String? = null
    private var stars: List<MelodyStar> = emptyList()
    private var preview: BaseMelody? = null
    private var confirmed: BaseMelody? = null
    private var chordThresholdRatio: Double = 0.25
    private var selectedKey: MelodyKey = MelodyKey.C_MAJOR
    private data class LoadedConstellation(val id: String, val stars: List<MelodyStar>, val confirmed: BaseMelody?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(buildUi())
        loadLatestConstellation()
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 9, 14))
            setPadding(dp(18), dp(12), dp(18), dp(14))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "✦ StarLog  ·  MUSIC BOX SCORE"
            textSize = 22f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        stateLabel = TextView(this).apply {
            text = "불러오는 중"
            textSize = 15f
            setTextColor(GOLD)
        }
        header.addView(stateLabel)
        root.addView(header, LinearLayout.LayoutParams(-1, dp(48)))

        canvas = MelodyCanvasView(this)
        root.addView(canvas, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        angleLabel = TextView(this).apply {
            text = "회전 0°"
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        controls.addView(angleLabel, LinearLayout.LayoutParams(dp(90), -2))
        angleSeek = SeekBar(this).apply {
            max = 359
            progress = 0
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    angleLabel.text = "회전 ${progress}°"
                    if (fromUser) renderManual(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        controls.addView(angleSeek, LinearLayout.LayoutParams(0, -2, 1f))
        octaveToggle = toggle("옥타브 보정", true) { renderManual(angleSeek.progress) }
        chordToggle = toggle("화음", true) { renderManual(angleSeek.progress) }
        controls.addView(octaveToggle)
        controls.addView(chordToggle)
        autoButton = actionButton("자동 회전") { optimize() }
        playButton = actionButton("♪ 미리 듣기") {
            val current = preview
            if (current != null) io.execute { player.play(current) }
        }
        confirmButton = actionButton("기본 멜로디 확정") { confirmCurrent() }
        arrangeButton = actionButton("편곡 설정") { showArrangementDialog() }
        controls.addView(autoButton)
        controls.addView(playButton)
        controls.addView(confirmButton)
        controls.addView(arrangeButton)
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(64)))

        val advanced = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        val thresholdLabel = TextView(this).apply {
            text = "화음 임계값 25%"
            setTextColor(Color.LTGRAY)
        }
        advanced.addView(thresholdLabel, LinearLayout.LayoutParams(dp(130), -2))
        thresholdSeek = SeekBar(this).apply {
            max = 50
            progress = 25
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    chordThresholdRatio = progress / 100.0
                    thresholdLabel.text = "화음 임계값 ${progress}%"
                    if (fromUser) renderManual(angleSeek.progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        advanced.addView(thresholdSeek, LinearLayout.LayoutParams(0, -2, 1f))
        keySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MelodyStudioActivity,
                android.R.layout.simple_spinner_dropdown_item,
                MelodyKey.entries.map { it.displayName },
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    val next = MelodyKey.entries[position]
                    if (selectedKey != next) {
                        selectedKey = next
                        renderManual(angleSeek.progress)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        advanced.addView(keySpinner, LinearLayout.LayoutParams(dp(150), -2))
        resetButton = actionButton("0° 복원") {
            angleSeek.progress = 0
            renderManual(0)
        }
        advanced.addView(resetButton)
        root.addView(advanced, LinearLayout.LayoutParams(-1, dp(52)))
        setControlsEnabled(false)
        return root
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun toggle(label: String, checked: Boolean, action: () -> Unit) = CheckBox(this).apply {
        text = label
        isChecked = checked
        setTextColor(Color.LTGRAY)
        setOnCheckedChangeListener { _, _ -> action() }
    }

    private fun loadLatestConstellation() {
        io.execute {
            val loaded = loadInput()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (loaded == null) {
                    stateLabel.text = "별자리 없음"
                    canvas.render(emptyList(), null, "저장된 여행 별자리가 없습니다. 주행 후 별자리를 먼저 생성해 주세요.")
                } else {
                    constellationId = loaded.id
                    stars = loaded.stars
                    setControlsEnabled(true)
                    if (loaded.confirmed != null) {
                        confirmed = loaded.confirmed
                        preview = loaded.confirmed
                        selectedKey = loaded.confirmed.keySignature
                        keySpinner.setSelection(MelodyKey.entries.indexOf(selectedKey))
                        angleSeek.progress = loaded.confirmed.rotationAngleDeg
                        stateLabel.text = "확정 멜로디 복원 · ${loaded.confirmed.id.takeLast(8)}"
                        canvas.render(stars, loaded.confirmed)
                    } else {
                        stateLabel.text = "${stars.size}개 별 · 준비됨"
                        renderManual(0)
                    }
                }
            }
        }
    }

    private fun loadInput(): LoadedConstellation? {
        for (mode in listOf(SourceMode.LIVE, SourceMode.REPLAY)) {
            val constellationResult = repository.listConstellations(mode)
            val starResult = repository.listStars(mode)
            if (constellationResult !is Outcome.Ok || starResult !is Outcome.Ok) continue
            val pair = constellationResult.value.maxByOrNull { it.first.createdAtMs } ?: continue
            val converted = project(pair.first, pair.second, starResult.value)
            if (converted.size >= 2) {
                val saved = (repository.loadBaseMelody(pair.first.id) as? Outcome.Ok)?.value
                return LoadedConstellation(pair.first.id, converted, saved)
            }
        }
        return null
    }

    private fun project(
        constellation: Constellation,
        members: List<ConstellationMember>,
        storedStars: List<MemoryStar>,
    ): List<MelodyStar> {
        val starsById = storedStars.associateBy { it.id }
        val cosLatitude = cos(Math.toRadians(constellation.centerPoint.lat))
        return members.sortedBy { it.orderIndex }.mapNotNull { member ->
            val star = starsById[member.starId] ?: return@mapNotNull null
            MelodyStar(
                starId = star.id,
                originalX = (star.point.lon - constellation.centerPoint.lon) * cosLatitude * 111_320.0,
                originalY = -(star.point.lat - constellation.centerPoint.lat) * 110_540.0,
                connectionOrder = member.orderIndex,
                createdAtMs = star.createdAtMs,
            )
        }
    }

    private fun config() = MelodyConfig(
        octaveCorrectionEnabled = octaveToggle.isChecked,
        chordGenerationEnabled = chordToggle.isChecked,
        chordThresholdRatio = chordThresholdRatio,
        keySignature = selectedKey,
    )

    private fun renderManual(angle: Int) {
        val id = constellationId ?: return
        if (stars.size < 2) return
        preview = MelodyGenerator(config()).preview(id, stars, angle)
        stateLabel.text = if (confirmed == null) "미확정 미리보기" else "확정 멜로디 유지 · 새 미리보기"
        canvas.render(stars, preview)
    }

    private fun optimize() {
        val id = constellationId ?: return
        setControlsEnabled(false)
        stateLabel.text = "최적 각도 탐색 중…"
        val localStars = stars
        val localConfig = config()
        io.execute {
            val existing = (repository.listBaseMelodies() as? Outcome.Ok)?.value.orEmpty()
                .filterNot { it.constellationId == id }
            val collectionVersion = existing.sortedBy { it.id }.joinToString("|") { "${it.id}:${it.algorithmVersion}" }
            val result = MelodyGenerator(localConfig).generate(id, localStars, existing, collectionVersion)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setControlsEnabled(true)
                preview = result.melody
                result.melody?.let {
                    angleSeek.progress = it.rotationAngleDeg
                    canvas.render(stars, it)
                    stateLabel.text = "${result.status.name} · ${result.evaluatedCandidateCount}개 후보"
                } ?: run {
                    stateLabel.text = result.message ?: result.status.name
                }
            }
        }
    }

    private fun confirmCurrent() {
        val value = preview ?: return
        setControlsEnabled(false)
        stateLabel.text = "기본 멜로디 저장 중…"
        io.execute {
            val result = repository.saveBaseMelody(value)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setControlsEnabled(true)
                if (result is Outcome.Ok) {
                    confirmed = value
                    stateLabel.text = "기본 멜로디 저장 완료 · ${value.id.takeLast(8)}"
                    canvas.render(stars, value)
                } else {
                    stateLabel.text = "저장 실패 · 기존 확정 멜로디는 변경되지 않았습니다."
                }
            }
        }
    }

    private fun showArrangementDialog() {
        val melody = confirmed ?: run {
            stateLabel.text = "편곡 전에 기본 멜로디를 확정해 주세요."
            return
        }
        val density = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (8 * density).toInt(), (24 * density).toInt(), 0)
        }
        fun <T> spinner(values: Array<T>): Spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MelodyStudioActivity,
                android.R.layout.simple_spinner_dropdown_item,
                values.map { it.toString() },
            )
        }
        val genre = spinner(ArrangementGenre.entries.toTypedArray())
        val rhythm = spinner(ArrangementRhythm.entries.toTypedArray())
        val instrument = spinner(ArrangementInstrument.entries.toTypedArray())
        val tempoLabel = TextView(this).apply {
            text = "Tempo 88 BPM"
            setTextColor(Color.WHITE)
        }
        val tempo = SeekBar(this).apply {
            max = 100
            progress = 48
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    tempoLabel.text = "Tempo ${40 + progress} BPM"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        listOf("장르" to genre, "리듬" to rhythm, "악기" to instrument).forEach { (label, field) ->
            content.addView(TextView(this).apply {
                text = label
                setTextColor(GOLD)
            })
            content.addView(field)
        }
        content.addView(tempoLabel)
        content.addView(tempo)
        AlertDialog.Builder(this)
            .setTitle("확정 멜로디 편곡 설정")
            .setView(content)
            .setNegativeButton("취소", null)
            .setPositiveButton("편곡 입력 만들기") { _, _ ->
                val request = ArrangementPromptBuilder.build(
                    melody,
                    ArrangementPreferences(
                        genre = ArrangementGenre.entries[genre.selectedItemPosition],
                        rhythm = ArrangementRhythm.entries[rhythm.selectedItemPosition],
                        instruments = listOf(ArrangementInstrument.entries[instrument.selectedItemPosition]),
                        tempoBpm = 40 + tempo.progress,
                    ),
                )
                stateLabel.text = "외부 생성 서비스용 편곡 입력 준비됨"
                AlertDialog.Builder(this)
                    .setTitle("Provider-neutral arrangement prompt")
                    .setMessage(request.prompt)
                    .setPositiveButton("확인", null)
                    .show()
            }
            .show()
    }

    private fun setControlsEnabled(enabled: Boolean) {
        angleSeek.isEnabled = enabled
        autoButton.isEnabled = enabled
        playButton.isEnabled = enabled
        confirmButton.isEnabled = enabled
        arrangeButton.isEnabled = enabled
        octaveToggle.isEnabled = enabled
        chordToggle.isEnabled = enabled
        thresholdSeek.isEnabled = enabled
        keySpinner.isEnabled = enabled
        resetButton.isEnabled = enabled
    }

    override fun onDestroy() {
        player.close()
        repository.close()
        io.shutdown()
        super.onDestroy()
    }

    private companion object { val GOLD = Color.rgb(229, 200, 136) }
}
