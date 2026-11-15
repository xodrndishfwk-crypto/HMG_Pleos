package com.example.starlog2

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.melody.BaseMelody
import com.example.starlog2.starlog.melody.ArrangementGenre
import com.example.starlog2.starlog.melody.ArrangementInstrument
import com.example.starlog2.starlog.melody.ArrangementPreferences
import com.example.starlog2.starlog.melody.ArrangementRhythm
import com.example.starlog2.starlog.melody.MelodyCanvasView
import com.example.starlog2.starlog.melody.MelodyConfig
import com.example.starlog2.starlog.melody.MelodyGenerator
import com.example.starlog2.starlog.melody.MelodyInputProjector
import com.example.starlog2.starlog.melody.MelodyKey
import com.example.starlog2.starlog.melody.MelodyPlaybackSession
import com.example.starlog2.starlog.melody.MelodyPreviewPlayer
import com.example.starlog2.starlog.melody.MelodyRotationMapper
import com.example.starlog2.starlog.melody.MelodyStar
import com.example.starlog2.starlog.demo.MelodyDemoReplaySeeder
import com.example.starlog2.starlog.gleo.GleoActionRequest
import com.example.starlog2.starlog.gleo.GleoCallbackSender
import com.example.starlog2.starlog.gleo.GleoContract
import com.example.starlog2.starlog.music.AiMusicCoordinator
import com.example.starlog2.starlog.music.AiMusicGatewayResult
import com.example.starlog2.starlog.music.AiMusicJob
import com.example.starlog2.starlog.music.AiMusicRequest
import com.example.starlog2.starlog.music.AiMusicRequestFactory
import com.example.starlog2.starlog.music.AiMusicStatus
import com.example.starlog2.starlog.music.GeneratedSongPlayer
import com.example.starlog2.starlog.music.HttpAiMusicGateway
import com.example.starlog2.starlog.journey.JourneyStatus
import com.example.starlog2.starlog.storage.SqliteMemoryRepository
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener

class MelodyStudioActivity : AppCompatActivity() {
    private lateinit var canvas: MelodyCanvasView
    private lateinit var stateLabel: TextView
    private lateinit var generationSourceLabel: TextView
    private lateinit var angleLabel: TextView
    private lateinit var constellationButton: Button
    private lateinit var angleSeek: SeekBar
    private lateinit var autoButton: Button
    private lateinit var playButton: Button
    private lateinit var confirmButton: Button
    private lateinit var arrangeButton: Button
    private lateinit var aiPlayButton: Button
    private lateinit var octaveToggle: CheckBox
    private lateinit var chordToggle: CheckBox
    private lateinit var thresholdSeek: SeekBar
    private lateinit var keySpinner: Spinner
    private lateinit var resetButton: Button
    private lateinit var repository: SqliteMemoryRepository
    private lateinit var musicGateway: HttpAiMusicGateway
    private lateinit var musicCoordinator: AiMusicCoordinator
    private lateinit var generatedSongPlayer: GeneratedSongPlayer
    private val io = Executors.newSingleThreadExecutor()
    private val player = MelodyPreviewPlayer()
    private val playback = MelodyPlaybackSession(
        playAction = { melody, shouldContinue -> player.play(melody, shouldContinue) },
        stopAction = player::close,
    )
    private var constellationId: String? = null
    private var sourceMode: SourceMode? = null
    private var requestedConstellationId: String? = null
    private var requestedSourceMode: SourceMode? = null
    private var stars: List<MelodyStar> = emptyList()
    private var preview: BaseMelody? = null
    private var confirmed: BaseMelody? = null
    private var aiMusicJob: AiMusicJob? = null
    private var pendingGleoAction: GleoActionRequest? = null
    private var chordThresholdRatio: Double = 0.25
    private var selectedKey: MelodyKey = MelodyKey.C_MAJOR
    private data class LoadedConstellation(
        val id: String,
        val tripId: String?,
        val sourceMode: SourceMode,
        val stars: List<MelodyStar>,
        val confirmed: BaseMelody?,
        val aiMusicJob: AiMusicJob?,
        val playableAiMusicJob: AiMusicJob?,
    )
    private data class ConstellationChoice(
        val id: String,
        val sourceMode: SourceMode,
        val label: String,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        pendingGleoAction = readGleoAction(intent)
        requestedConstellationId = pendingGleoAction?.constellationId
            ?: intent.getStringExtra(EXTRA_CONSTELLATION_ID)
        requestedSourceMode = pendingGleoAction?.sourceMode ?: intent.getStringExtra(EXTRA_SOURCE_MODE)
            ?.let { runCatching { SourceMode.valueOf(it) }.getOrNull() }
        repository = SqliteMemoryRepository(applicationContext)
        musicGateway = HttpAiMusicGateway(
            baseUrl = DEFAULT_MUSIC_BACKEND_URL,
        )
        musicCoordinator = AiMusicCoordinator(repository, musicGateway)
        generatedSongPlayer = GeneratedSongPlayer(applicationContext) { state ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    when (state) {
                        GeneratedSongPlayer.State.PREPARING -> stateLabel.text = "AI 음악 재생 준비 중…"
                        GeneratedSongPlayer.State.PLAYING -> stateLabel.text = "AI 음악 재생 중"
                        GeneratedSongPlayer.State.STOPPED -> Unit
                        GeneratedSongPlayer.State.ERROR -> stateLabel.text = "AI 음악 재생 실패"
                    }
                }
            }
        }
        setContentView(buildUi())
        loadLatestConstellation()
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(4, 7, 13))
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "✦ StarLog"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(studioTab("여정 · 별자리", false))
        header.addView(studioTab("주행 · 기억의 숲", false))
        header.addView(studioTab("여정 종료 · 오토 음악", true))
        generationSourceLabel = TextView(this).apply {
            text = "DEMO MOCK"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(GOLD)
            background = studioPanel(Color.rgb(9, 13, 21), Color.rgb(36, 42, 54), 18)
            setPadding(dp(14), 0, dp(14), 0)
        }
        header.addView(generationSourceLabel, LinearLayout.LayoutParams(dp(128), dp(34)).apply { marginStart = dp(16) })
        stateLabel = TextView(this).apply {
            text = "불러오는 중"
            textSize = 12f
            setTextColor(GOLD)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
        }
        header.addView(stateLabel, LinearLayout.LayoutParams(dp(178), -1).apply { marginStart = dp(12) })
        root.addView(header, LinearLayout.LayoutParams(-1, dp(52)))

        canvas = MelodyCanvasView(this)
        root.addView(canvas, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        constellationButton = actionButton("별자리 선택") { showConstellationSelection() }
        controls.addView(constellationButton)
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
            if (current != null) {
                generatedSongPlayer.close()
                playback.activate()
                playback.play(current)
            }
        }
        confirmButton = actionButton("기본 멜로디 확정") { confirmCurrent() }
        arrangeButton = actionButton("AI 음악 만들기") { showArrangementDialog() }
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
        aiPlayButton = actionButton("AI 곡 재생") { playGeneratedSong() }.apply { isEnabled = false }
        advanced.addView(aiPlayButton)
        root.addView(advanced, LinearLayout.LayoutParams(-1, dp(52)))
        setControlsEnabled(false)
        constellationButton.isEnabled = true
        return root
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        setTextColor(Color.WHITE)
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dpLocal(10), 0, dpLocal(10), 0)
        background = studioPanel(Color.rgb(12, 17, 27), Color.rgb(47, 53, 66), 12)
        setOnClickListener { action() }
    }

    private fun studioTab(value: String, selected: Boolean) = TextView(this).apply {
        text = value
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(if (selected) Color.WHITE else Color.rgb(132, 142, 158))
        background = if (selected) studioPanel(Color.rgb(17, 22, 32), Color.rgb(118, 101, 67), 18) else null
        setPadding(dpLocal(14), 0, dpLocal(14), 0)
    }

    private fun studioPanel(fill: Int, stroke: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        setStroke(dpLocal(1), stroke)
        cornerRadius = dpLocal(radiusDp).toFloat()
    }

    private fun dpLocal(value: Int) = (value * resources.displayMetrics.density).toInt()

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
                    finishPendingGleoWithoutConstellation()
                } else {
                    constellationId = loaded.id
                    sourceMode = loaded.sourceMode
                    stars = loaded.stars
                    aiMusicJob = loaded.aiMusicJob
                    setControlsEnabled(true)
                    if (loaded.confirmed != null) {
                        confirmed = loaded.confirmed
                        preview = loaded.confirmed
                        selectedKey = loaded.confirmed.keySignature
                        keySpinner.setSelection(MelodyKey.entries.indexOf(selectedKey))
                        angleSeek.progress = loaded.confirmed.rotationAngleDeg
                        stateLabel.text = "확정 멜로디 복원 · ${loaded.confirmed.id.takeLast(8)}"
                        canvas.render(stars, loaded.confirmed)
                        showRestoredAiMusicState(loaded.aiMusicJob)
                        loaded.aiMusicJob?.let(::resumeAiMusicJobIfNeeded)
                    } else {
                        stateLabel.text = "${stars.size}개 별 · 준비됨"
                        renderManual(0)
                    }
                    handlePendingGleoAction(loaded)
                }
            }
        }
    }

    private fun loadInput(): LoadedConstellation? {
        val requestedId = requestedConstellationId
        val modes = requestedSourceMode?.let(::listOf) ?: listOf(SourceMode.LIVE, SourceMode.REPLAY)
        for (mode in modes) {
            val requiresCompletedJourney = pendingGleoAction?.functionName in setOf(
                GleoContract.ACTION_PREPARE_CONSTELLATION_MUSIC,
                GleoContract.ACTION_PLAY_CONSTELLATION_MUSIC,
            )
            val completedJourney = if (requiresCompletedJourney) {
                (repository.loadLatestJourney(mode) as? Outcome.Ok)?.value
                    ?.takeIf { it.status == JourneyStatus.COMPLETED }
            } else {
                null
            }
            if (requiresCompletedJourney && completedJourney == null) continue
            val constellationResult = repository.listConstellations(mode)
            val starResult = repository.listStars(mode)
            val visitResult = repository.listVisits(mode)
            if (constellationResult !is Outcome.Ok || starResult !is Outcome.Ok || visitResult !is Outcome.Ok) continue
            val effectiveRequestedId = requestedId ?: completedJourney?.constellationId
            val pair = if (effectiveRequestedId == null) {
                constellationResult.value.maxByOrNull { it.first.createdAtMs }
            } else {
                constellationResult.value.firstOrNull { it.first.id == effectiveRequestedId }
            } ?: continue
            if (requiresCompletedJourney) {
                val tripId = pair.first.tripId ?: continue
                val journey = (repository.loadJourney(tripId) as? Outcome.Ok)?.value ?: continue
                if (journey.status != JourneyStatus.COMPLETED || journey.constellationId != pair.first.id) continue
            }
            val converted = MelodyInputProjector.project(pair.first, pair.second, starResult.value, visitResult.value)
            if (converted.size >= 2) {
                val saved = (repository.loadBaseMelody(pair.first.id) as? Outcome.Ok)?.value
                val jobs = (repository.listAiMusicJobs(pair.first.id) as? Outcome.Ok)?.value.orEmpty()
                val playableJob = jobs.firstOrNull { job ->
                    job.status == AiMusicStatus.SUCCEEDED &&
                        job.selectedResult?.localFilePath?.let(::File)?.isFile == true
                }
                return LoadedConstellation(
                    pair.first.id,
                    pair.first.tripId,
                    mode,
                    converted,
                    saved,
                    jobs.firstOrNull(),
                    playableJob,
                )
            }
        }
        return null
    }

    private fun showConstellationSelection() {
        constellationButton.isEnabled = false
        stateLabel.text = "별자리 목록 불러오는 중…"
        io.execute {
            val choices = buildList {
                for (mode in listOf(SourceMode.LIVE, SourceMode.REPLAY)) {
                    val values = (repository.listConstellations(mode) as? Outcome.Ok)?.value.orEmpty()
                    values.sortedByDescending { it.first.createdAtMs }.forEach { (constellation, members) ->
                        if (members.size >= 2) {
                            add(
                                ConstellationChoice(
                                    id = constellation.id,
                                    sourceMode = mode,
                                    label = "${mode.name} · ${constellation.label ?: constellation.id} · ${members.size} stars",
                                ),
                            )
                        }
                    }
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                constellationButton.isEnabled = true
                if (choices.isEmpty()) {
                    stateLabel.text = "선택할 별자리가 없습니다."
                    AlertDialog.Builder(this)
                        .setTitle("별자리 없음")
                        .setMessage("실제 기록을 만든 뒤 다시 시도하거나, 명시적인 합성 REPLAY 데모를 생성할 수 있습니다.")
                        .setNegativeButton("취소", null)
                        .setPositiveButton("합성 REPLAY 생성") { _, _ -> seedDemoReplay() }
                        .show()
                    return@runOnUiThread
                }
                AlertDialog.Builder(this)
                    .setTitle("멜로디로 만들 별자리 선택")
                    .setItems(choices.map { it.label }.toTypedArray()) { _, index ->
                        val choice = choices[index]
                        requestedConstellationId = choice.id
                        requestedSourceMode = choice.sourceMode
                        constellationId = null
                        sourceMode = null
                        stars = emptyList()
                        preview = null
                        confirmed = null
                        aiMusicJob = null
                        aiPlayButton.isEnabled = false
                        setControlsEnabled(false)
                        constellationButton.isEnabled = false
                        stateLabel.text = "선택한 별자리 불러오는 중…"
                        loadLatestConstellation()
                    }
                    .setNeutralButton("합성 REPLAY 생성") { _, _ -> seedDemoReplay() }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }
    }

    private fun seedDemoReplay() {
        setControlsEnabled(false)
        constellationButton.isEnabled = false
        stateLabel.text = "합성 REPLAY 별자리 생성 중…"
        io.execute {
            val result = MelodyDemoReplaySeeder.seed(repository)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result is Outcome.Ok) {
                    requestedConstellationId = result.value
                    requestedSourceMode = SourceMode.REPLAY
                    stateLabel.text = "합성 REPLAY 별자리 불러오는 중…"
                    loadLatestConstellation()
                } else {
                    constellationButton.isEnabled = true
                    stateLabel.text = "합성 REPLAY 생성 실패"
                }
            }
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
        preview = MelodyRotationMapper.previewAtDisplayedAngle(id, stars, angle, config())
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
                result.melody?.let {
                    animateOptimizationResult(it, localConfig) {
                        setControlsEnabled(true)
                        stateLabel.text = "${result.status.name} · ${result.evaluatedCandidateCount}개 후보"
                    }
                } ?: run {
                    setControlsEnabled(true)
                    stateLabel.text = result.message ?: result.status.name
                }
            }
        }
    }

    private fun animateOptimizationResult(value: BaseMelody, localConfig: MelodyConfig, onEnd: () -> Unit) {
        val id = constellationId ?: return onEnd()
        val start = angleSeek.progress
        val target = value.rotationAngleDeg
        ValueAnimator.ofInt(start, target).apply {
            duration = 900L
            addUpdateListener { animator ->
                val displayedAngle = animator.animatedValue as Int
                angleSeek.progress = displayedAngle
                angleLabel.text = "회전 ${displayedAngle}°"
                preview = MelodyRotationMapper.previewAtDisplayedAngle(id, stars, displayedAngle, localConfig)
                canvas.render(stars, preview)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    preview = value
                    angleSeek.progress = target
                    canvas.render(stars, value)
                    onEnd()
                }
            })
            start()
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

    private fun showArrangementDialog(preselectedGenre: ArrangementGenre? = null) {
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
        preselectedGenre?.let { genre.setSelection(ArrangementGenre.entries.indexOf(it)) }
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
            .setPositiveButton("AI 음악 생성") { _, _ ->
                startAiMusicGeneration(
                    melody,
                    ArrangementPreferences(
                        genre = ArrangementGenre.entries[genre.selectedItemPosition],
                        rhythm = ArrangementRhythm.entries[rhythm.selectedItemPosition],
                        instruments = listOf(ArrangementInstrument.entries[instrument.selectedItemPosition]),
                        tempoBpm = 40 + tempo.progress,
                    ),
                )
            }
            .show()
    }

    private fun startAiMusicGeneration(melody: BaseMelody, preferences: ArrangementPreferences) {
        val mode = sourceMode ?: run {
            stateLabel.text = "별자리 데이터 출처를 확인할 수 없습니다."
            return
        }
        val request = AiMusicRequestFactory.create(melody, mode, preferences, System.currentTimeMillis())
        arrangeButton.isEnabled = false
        stateLabel.text = "AI 음악 요청 제출 중…"
        runAiMusicRequest(request)
    }

    private fun resumeAiMusicJobIfNeeded(job: AiMusicJob) {
        val hasPlayableFile = job.selectedResult?.localFilePath?.let(::File)?.isFile == true
        if (job.status == AiMusicStatus.FAILED || hasPlayableFile) return
        arrangeButton.isEnabled = false
        stateLabel.text = "저장된 AI 음악 작업 이어받는 중…"
        runAiMusicRequest(job.request)
    }

    private fun runAiMusicRequest(request: AiMusicRequest) {
        io.execute {
            var response = musicCoordinator.submitOrResume(request)
            var polls = 0
            while (response is AiMusicGatewayResult.Success &&
                !response.value.status.isTerminal &&
                polls < MAX_MUSIC_POLLS &&
                !Thread.currentThread().isInterrupted
            ) {
                val snapshot = response.value
                runOnUiThread { showAiMusicState(snapshot) }
                try {
                    Thread.sleep(MUSIC_POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }
                response = musicCoordinator.refresh(snapshot)
                polls += 1
            }

            if (response is AiMusicGatewayResult.Success && response.value.status == AiMusicStatus.SUCCEEDED) {
                val completed = response.value
                val selected = completed.selectedResult
                if (selected != null && selected.localFilePath.isNullOrBlank()) {
                    runOnUiThread { stateLabel.text = "AI 음악 저장 중…" }
                    val extension = if (selected.mimeType == "audio/mpeg") "mp3" else "wav"
                    val target = File(filesDir, "ai_music/${completed.request.requestId}/${selected.id}.$extension")
                    response = when (val downloaded = musicGateway.download(selected, target)) {
                        is AiMusicGatewayResult.Failure -> downloaded
                        is AiMusicGatewayResult.Success ->
                            musicCoordinator.saveDownloadedResult(completed, selected.id, downloaded.value.absolutePath)
                    }
                }
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                arrangeButton.isEnabled = confirmed != null
                when (response) {
                    is AiMusicGatewayResult.Failure -> {
                        stateLabel.text = when (response.code) {
                            com.example.starlog2.starlog.music.AiMusicErrorCode.NETWORK,
                            com.example.starlog2.starlog.music.AiMusicErrorCode.TIMEOUT
                            -> "DEMO MOCK 연결 실패 · 앱은 계속 사용 가능"
                            else -> "DEMO MOCK 음악 요청 실패 · ${response.code.name}"
                        }
                        aiPlayButton.isEnabled = false
                    }
                    is AiMusicGatewayResult.Success -> {
                        aiMusicJob = response.value
                        showAiMusicState(response.value)
                    }
                }
            }
        }
    }

    private fun showRestoredAiMusicState(job: AiMusicJob?) {
        if (job == null) {
            aiPlayButton.isEnabled = false
            return
        }
        showAiMusicState(job)
    }

    private fun showAiMusicState(job: AiMusicJob) {
        aiMusicJob = job
        val generationLabel = job.generationSource.name.replace('_', ' ')
        generationSourceLabel.text = generationLabel
        when (job.status) {
            AiMusicStatus.SUBMITTING -> stateLabel.text = "$generationLabel 요청 제출 중…"
            AiMusicStatus.QUEUED -> stateLabel.text = "$generationLabel 생성 대기 중"
            AiMusicStatus.GENERATING -> stateLabel.text = "$generationLabel 생성 중"
            AiMusicStatus.DOWNLOADING -> stateLabel.text = "$generationLabel 저장 중…"
            AiMusicStatus.SUCCEEDED -> {
                val playable = job.selectedResult?.localFilePath?.let(::File)?.isFile == true
                aiPlayButton.isEnabled = playable
                stateLabel.text = if (playable) "$generationLabel 저장 완료 · 재생 가능" else "$generationLabel 생성 완료 · 다운로드 필요"
            }
            AiMusicStatus.FAILED -> {
                aiPlayButton.isEnabled = false
                stateLabel.text = "$generationLabel 생성 실패 · ${job.errorCode?.name ?: "UNKNOWN"}"
            }
        }
    }

    private fun playGeneratedSong() {
        val file = aiMusicJob?.selectedResult?.localFilePath?.let(::File)
        if (file?.isFile != true) {
            stateLabel.text = "저장된 AI 음악 파일을 찾을 수 없습니다."
            aiPlayButton.isEnabled = false
            return
        }
        playback.deactivate()
        generatedSongPlayer.play(file)
    }

    private fun readGleoAction(sourceIntent: Intent): GleoActionRequest? {
        val functionName = sourceIntent.action
            ?.takeIf { it in GleoContract.supportedActions }
            ?: return null
        val request = runCatching {
            val raw = sourceIntent.getStringExtra(GleoContract.EXTRA_ACTION_PARAM) ?: "{}"
            val decoded = JSONTokener(raw).nextValue()
            val parameters = when (decoded) {
                is JSONObject -> decoded
                is String -> JSONObject(decoded)
                else -> JSONObject()
            }
            GleoActionRequest.from(
                functionName = functionName,
                constellationId = parameters.optString("constellationId").takeIf { it.isNotBlank() },
                sourceMode = parameters.optString("sourceMode").takeIf { it.isNotBlank() },
                genre = parameters.optString("genre").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
        if (request == null) {
            GleoCallbackSender.send(
                context = this,
                functionName = functionName,
                status = GleoCallbackSender.Status.FAIL,
                message = "The StarLog action parameters are invalid.",
            )
        }
        return request
    }

    private fun finishPendingGleoWithoutConstellation() {
        val request = pendingGleoAction ?: return
        pendingGleoAction = null
        GleoCallbackSender.send(
            context = this,
            functionName = request.functionName,
            status = GleoCallbackSender.Status.ASKING,
            message = "A recorded constellation must be selected before this action can continue.",
        )
    }

    private fun handlePendingGleoAction(loaded: LoadedConstellation) {
        val request = pendingGleoAction ?: return
        pendingGleoAction = null
        val commonData = mutableMapOf(
            "constellationId" to loaded.id,
            "sourceMode" to loaded.sourceMode.name,
        ).apply {
            loaded.tripId?.let { put("tripId", it) }
            loaded.confirmed?.let { put("baseMelodyId", it.id) }
        }
        when (request.functionName) {
            GleoContract.ACTION_OPEN_MELODY_STUDIO -> GleoCallbackSender.send(
                context = this,
                functionName = request.functionName,
                status = GleoCallbackSender.Status.SUCCESS,
                message = "The constellation melody studio is open.",
                data = listOf(commonData),
            )

            GleoContract.ACTION_PREPARE_CONSTELLATION_MUSIC -> {
                if (loaded.confirmed == null) {
                    stateLabel.text = "AI 음악 생성 전에 기본 멜로디를 확정해 주세요."
                    GleoCallbackSender.send(
                        context = this,
                        functionName = request.functionName,
                        status = GleoCallbackSender.Status.ASKING,
                        message = "The base melody must be confirmed before arranging generated music.",
                        data = listOf(commonData),
                    )
                } else {
                    GleoCallbackSender.send(
                        context = this,
                        functionName = request.functionName,
                        status = GleoCallbackSender.Status.ASKING,
                        message = "Review the arrangement settings and confirm the generation request on screen.",
                        data = listOf(commonData),
                    )
                    showArrangementDialog(request.genre)
                }
            }

            GleoContract.ACTION_PLAY_CONSTELLATION_MUSIC -> {
                val job = loaded.playableAiMusicJob
                val selectedResult = job?.selectedResult
                val file = selectedResult?.localFilePath?.let(::File)
                if (job != null && selectedResult != null && file?.isFile == true) {
                    aiMusicJob = job
                    playback.deactivate()
                    generatedSongPlayer.play(file)
                    commonData["jobId"] = job.providerJobId ?: job.request.requestId
                    commonData["resultId"] = selectedResult.id
                    commonData["generationSource"] = job.generationSource.name
                    GleoCallbackSender.send(
                        context = this,
                        functionName = request.functionName,
                        status = GleoCallbackSender.Status.SUCCESS,
                        message = "The saved constellation song is now playing.",
                        data = listOf(commonData),
                        mode = GleoCallbackSender.Mode.SILENT,
                    )
                } else {
                    GleoCallbackSender.send(
                        context = this,
                        functionName = request.functionName,
                        status = GleoCallbackSender.Status.FAIL,
                        message = "No playable generated song is saved for the selected constellation.",
                        data = listOf(commonData),
                    )
                }
            }
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        constellationButton.isEnabled = true
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
        aiPlayButton.isEnabled = enabled && aiMusicJob?.selectedResult?.localFilePath?.let(::File)?.isFile == true
    }

    override fun onResume() {
        super.onResume()
        playback.activate()
    }

    override fun onPause() {
        generatedSongPlayer.close()
        playback.deactivate()
        super.onPause()
    }

    override fun onDestroy() {
        generatedSongPlayer.close()
        playback.close()
        io.shutdownNow()
        closeRepositoryAfterIoStops()
        super.onDestroy()
    }

    private fun closeRepositoryAfterIoStops() {
        val repositoryToClose = repository
        Thread({
            try {
                while (!io.awaitTermination(1, TimeUnit.SECONDS)) {
                    // Wait off the UI thread so no repository operation races close().
                }
                repositoryToClose.close()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }, "starlog-melody-repository-close").apply {
            isDaemon = true
            start()
        }
    }

    companion object {
        const val EXTRA_CONSTELLATION_ID = "starlog.constellationId"
        const val EXTRA_SOURCE_MODE = "starlog.sourceMode"
        const val DEFAULT_MUSIC_BACKEND_URL = "http://10.0.2.2:8787"
        private const val MAX_MUSIC_POLLS = 40
        private const val MUSIC_POLL_INTERVAL_MS = 1_500L
        val GOLD = Color.rgb(229, 200, 136)
    }
}
