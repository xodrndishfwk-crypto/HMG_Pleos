package com.example.starlog2

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.example.starlog2.contracts.AppError
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.constellation.ConstellationShapeMatcher
import com.example.starlog2.starlog.demo.DemoRoute
import com.example.starlog2.starlog.gleo.GleoActionRequest
import com.example.starlog2.starlog.gleo.GleoCallbackSender
import com.example.starlog2.starlog.gleo.GleoContract
import com.example.starlog2.starlog.gleo.GleoJourneyActionRequest
import com.example.starlog2.starlog.gleo.GleoJourneyCallbackData
import com.example.starlog2.starlog.journey.JourneyControlResponse
import com.example.starlog2.starlog.journey.JourneyController
import com.example.starlog2.starlog.journey.JourneyRecord
import com.example.starlog2.starlog.journey.JourneyStatus
import com.example.starlog2.starlog.journey.StarPhoto
import com.example.starlog2.starlog.melody.ArrangementGenre
import com.example.starlog2.starlog.melody.ArrangementInstrument
import com.example.starlog2.starlog.melody.ArrangementPreferences
import com.example.starlog2.starlog.melody.ArrangementRhythm
import com.example.starlog2.starlog.melody.BaseMelody
import com.example.starlog2.starlog.melody.MelodyConfig
import com.example.starlog2.starlog.melody.MelodyGenerator
import com.example.starlog2.starlog.melody.MelodyInputProjector
import com.example.starlog2.starlog.melody.MelodyPlaybackSession
import com.example.starlog2.starlog.melody.MelodyPreviewPlayer
import com.example.starlog2.starlog.melody.MelodyStar
import com.example.starlog2.starlog.music.AiMusicCoordinator
import com.example.starlog2.starlog.music.AiMusicGatewayResult
import com.example.starlog2.starlog.music.AiMusicJob
import com.example.starlog2.starlog.music.AiMusicRequest
import com.example.starlog2.starlog.music.AiMusicRequestFactory
import com.example.starlog2.starlog.music.AiMusicStatus
import com.example.starlog2.starlog.music.GeneratedSongPlayer
import com.example.starlog2.starlog.music.HttpAiMusicGateway
import com.example.starlog2.starlog.storage.SqliteMemoryRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import timber.log.Timber

/**
 * Submission launcher that reproduces the StarLog storyboard (intro → atlas → drive → parked memory →
 * constellation forging → music-box score) in a WebView, while every number on screen comes from the
 * shared journey, melody, music and photo pipelines.
 */
class StarLogExperienceActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var controller: JourneyController
    private lateinit var repository: SqliteMemoryRepository
    private lateinit var musicGateway: HttpAiMusicGateway
    private lateinit var musicCoordinator: AiMusicCoordinator
    private lateinit var songPlayer: GeneratedSongPlayer
    private val io = Executors.newSingleThreadExecutor()
    private val musicIo = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val previewPlayer = MelodyPreviewPlayer()
    private val playback = MelodyPlaybackSession(
        playAction = { melody, shouldContinue -> previewPlayer.play(melody, shouldContinue) },
        stopAction = previewPlayer::close,
    )

    private var pageReady = false
    private var pendingIntent: Intent? = null
    private var replayRunning = false
    @Volatile private var preview: BaseMelody? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(LOG_TAG).d("onCreate restored=%s action=%s", savedInstanceState != null, intent?.action)
        supportActionBar?.hide()
        controller = (application as StarlogApplication).journeyController
        repository = SqliteMemoryRepository(applicationContext)
        musicGateway = HttpAiMusicGateway(baseUrl = MelodyStudioActivity.DEFAULT_MUSIC_BACKEND_URL)
        musicCoordinator = AiMusicCoordinator(repository, musicGateway)
        songPlayer = GeneratedSongPlayer(applicationContext) { state ->
            emit("song", JSONObject().put("state", state.name))
        }
        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(5, 6, 10))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            webChromeClient = WebChromeClient()
            addJavascriptInterface(Bridge(), "StarLogNative")
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        setContentView(webView)
        pendingIntent = intent
        webView.loadUrl("file:///android_asset/experience/index.html")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (pageReady) handleGleoIntent(intent) else pendingIntent = intent
    }

    override fun onResume() {
        super.onResume()
        playback.activate()
        io.execute {
            val active = controller.current()?.takeIf { it.status == JourneyStatus.ACTIVE }
            if (active != null && active.points.size < controller.routeOf(active).stops.size) {
                runOnUiThread { startReplayTicks() }
            }
        }
    }

    override fun onPause() {
        stopReplayTicks()
        songPlayer.close()
        playback.deactivate()
        super.onPause()
    }

    override fun onDestroy() {
        Timber.tag(LOG_TAG).d("onDestroy finishing=%s changingConfig=%s", isFinishing, isChangingConfigurations)
        handler.removeCallbacksAndMessages(null)
        songPlayer.close()
        playback.close()
        musicIo.shutdownNow()
        io.shutdown()
        val repositoryToClose = repository
        Thread({
            try {
                while (!io.awaitTermination(1, TimeUnit.SECONDS)) Unit
                repositoryToClose.close()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }, "starlog-experience-repository-close").apply { isDaemon = true; start() }
        webView.destroy()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- JS bridge

    private inner class Bridge {
        @JavascriptInterface
        fun ready() {
            runOnUiThread {
                pageReady = true
                pendingIntent?.let(::handleGleoIntent)
                pendingIntent = null
            }
        }

        /** Asynchronous request: the answer is delivered to `StarLogUI.onReply(id, json)`. */
        @JavascriptInterface
        fun call(requestId: String, method: String, argsJson: String) {
            val args = runCatching { JSONObject(argsJson) }.getOrDefault(JSONObject())
            if (method in UI_THREAD_METHODS) {
                runOnUiThread { reply(requestId, dispatchUi(method, args)) }
            } else {
                io.execute { reply(requestId, runCatching { dispatch(method, args) }.getOrElse(::errorJson)) }
            }
        }
    }

    private fun dispatchUi(method: String, args: JSONObject): JSONObject = when (method) {
        "replay.start" -> { startReplayTicks(); JSONObject().put("ok", true) }
        "melody.stop" -> { playback.deactivate(); playback.activate(); JSONObject().put("ok", true) }
        "song.stop" -> { songPlayer.close(); JSONObject().put("ok", true) }
        else -> errorJson(IllegalArgumentException("unknown ui method $method"))
    }

    private fun dispatch(method: String, args: JSONObject): JSONObject = when (method) {
        "state" -> stateJson()
        "journey.start" -> {
            val response = controller.start(SourceMode.REPLAY)
            runOnUiThread { if (response is JourneyControlResponse.Success) startReplayTicks() }
            responseJson(response)
        }
        "journey.end" -> {
            runOnUiThread { stopReplayTicks() }
            responseJson(controller.end(SourceMode.REPLAY))
        }
        "melody.optimize" -> optimizeMelody()
        "melody.confirm" -> confirmMelody()
        "melody.play" -> playPreview()
        "song.play" -> playSavedSong()
        "music.generate" -> generateMusic(args)
        "memory.demoPhoto" -> addDemoPhoto(args.optString("starId"), args.optString("asset"))
        "memory.photo" -> photoJson(args.optString("starId"))
        else -> errorJson(IllegalArgumentException("unknown method $method"))
    }

    private fun reply(requestId: String, payload: JSONObject) {
        val script = "window.StarLogUI&&StarLogUI.onReply(${JSONObject.quote(requestId)},${payload})"
        runOnUiThread { if (!isDestroyed) webView.evaluateJavascript(script, null) }
    }

    private fun emit(type: String, payload: JSONObject) {
        val script = "window.StarLogUI&&StarLogUI.onEvent(${JSONObject.quote(type)},${payload})"
        runOnUiThread { if (!isDestroyed && pageReady) webView.evaluateJavascript(script, null) }
    }

    private fun errorJson(error: Throwable) = JSONObject().put("ok", false).put("error", error.message ?: error.javaClass.simpleName)

    // ---------------------------------------------------------------- REPLAY ticks

    private fun startReplayTicks() {
        if (replayRunning) return
        replayRunning = true
        scheduleTick()
    }

    private fun stopReplayTicks() {
        replayRunning = false
        handler.removeCallbacksAndMessages(TICK_TOKEN)
    }

    private fun scheduleTick() {
        if (!replayRunning) return
        handler.postAtTime({
            io.execute {
                val active = controller.current()?.takeIf { it.status == JourneyStatus.ACTIVE }
                val total = active?.let { controller.routeOf(it).stops.size } ?: 0
                if (active == null || active.points.size >= total) {
                    runOnUiThread { replayRunning = false }
                    emit("journey", stateJson())
                    return@execute
                }
                val result = controller.advance(SourceMode.REPLAY)
                emit("journey", stateJson())
                runOnUiThread {
                    if (result is Outcome.Ok) scheduleTick() else replayRunning = false
                }
            }
        }, TICK_TOKEN, android.os.SystemClock.uptimeMillis() + REPLAY_TICK_MS)
    }

    // ---------------------------------------------------------------- state

    private fun stateJson(): JSONObject {
        val record = controller.current(SourceMode.REPLAY)
        val route = record?.let(controller::routeOf) ?: controller.route
        val state = JSONObject()
            .put("ok", true)
            .put("tickMs", REPLAY_TICK_MS)
            .put("route", routeJson(route))
            .put("journey", record?.let { journeyJson(it, route) } ?: JSONObject.NULL)
        if (record?.status == JourneyStatus.COMPLETED) {
            val confirmed = (repository.loadBaseMelody(record.constellationId) as? Outcome.Ok)?.value
            val shown = confirmed ?: preview?.takeIf { it.constellationId == record.constellationId }
            state.put("melody", shown?.let { melodyJson(it, record, confirmed != null) } ?: JSONObject.NULL)
            state.put("music", latestJob(record.constellationId)?.let(::musicJson) ?: JSONObject.NULL)
            ConstellationShapeMatcher.bestMatch(uniquePoints(record).map { it.point })?.let { match ->
                state.put(
                    "match",
                    JSONObject()
                        .put("name", match.template.koreanName)
                        .put("latin", match.template.latinName)
                        .put("score", match.score)
                        .put("grade", match.grade),
                )
            }
        }
        if (record != null) {
            val counts = JSONObject()
            uniquePoints(record).forEach { point ->
                val photos = (repository.listStarPhotos(point.starId) as? Outcome.Ok)?.value.orEmpty()
                counts.put(point.starId, photos.size)
            }
            state.put("photoCounts", counts)
        }
        return state
    }

    private fun uniquePoints(record: JourneyRecord) = record.points.filter { it.createsNewStar }

    private fun routeJson(route: DemoRoute) = JSONObject()
        .put("id", route.id)
        .put("label", route.constellationLabel)
        .put("stops", JSONArray().apply {
            route.stops.forEach { stop ->
                put(JSONObject().put("label", stop.label).put("lat", stop.point.lat).put("lon", stop.point.lon))
            }
        })

    private fun journeyJson(record: JourneyRecord, route: DemoRoute): JSONObject {
        val points = JSONArray()
        record.points.forEach { point ->
            points.put(
                JSONObject()
                    .put("order", point.orderIndex)
                    .put("label", point.label)
                    .put("lat", point.point.lat)
                    .put("lon", point.point.lon)
                    .put("starId", point.starId)
                    .put("visitedAt", point.visitedAtMs)
                    .put("newStar", point.createsNewStar),
            )
        }
        val distanceKm = route.stops.zipWithNext().sumOf { (a, b) -> haversineKm(a.point.lat, a.point.lon, b.point.lat, b.point.lon) }
        return JSONObject()
            .put("tripId", record.id)
            .put("status", record.status.name)
            .put("sourceMode", record.sourceMode.name)
            .put("startedAt", record.startedAtMs)
            .put("endedAt", record.completedAtMs ?: JSONObject.NULL)
            .put("constellationId", record.constellationId.takeIf { record.status == JourneyStatus.COMPLETED } ?: JSONObject.NULL)
            .put("points", points)
            .put("totalStops", route.stops.size)
            .put("routeDistanceKm", distanceKm)
            .put("edges", record.edges.size)
    }

    private fun responseJson(response: JourneyControlResponse) = JSONObject()
        .put("ok", response !is JourneyControlResponse.Failure)
        .put("kind", response.javaClass.simpleName)
        .put("message", response.message)
        .put("state", stateJson())

    // ---------------------------------------------------------------- melody

    private fun melodyInput(record: JourneyRecord): List<MelodyStar>? {
        val constellations = (repository.listConstellations(record.sourceMode) as? Outcome.Ok)?.value ?: return null
        val pair = constellations.firstOrNull { it.first.id == record.constellationId } ?: return null
        val stars = (repository.listStars(record.sourceMode) as? Outcome.Ok)?.value ?: return null
        val visits = (repository.listVisits(record.sourceMode) as? Outcome.Ok)?.value ?: return null
        return MelodyInputProjector.project(pair.first, pair.second, stars, visits).takeIf { it.size >= 2 }
    }

    private fun optimizeMelody(): JSONObject {
        val record = controller.current()?.takeIf { it.status == JourneyStatus.COMPLETED }
            ?: return JSONObject().put("ok", false).put("error", "journey.not_completed")
        (repository.loadBaseMelody(record.constellationId) as? Outcome.Ok)?.value?.let { confirmed ->
            return JSONObject().put("ok", true).put("melody", melodyJson(confirmed, record, true))
        }
        val stars = melodyInput(record) ?: return JSONObject().put("ok", false).put("error", "melody.no_input")
        val existing = (repository.listBaseMelodies() as? Outcome.Ok)?.value.orEmpty()
            .filterNot { it.constellationId == record.constellationId }
        val collectionVersion = existing.sortedBy { it.id }.joinToString("|") { "${it.id}:${it.algorithmVersion}" }
        val result = MelodyGenerator(MelodyConfig()).generate(record.constellationId, stars, existing, collectionVersion)
        val melody = result.melody ?: return JSONObject().put("ok", false).put("error", result.message ?: result.status.name)
        preview = melody
        return JSONObject().put("ok", true).put("melody", melodyJson(melody, record, false))
            .put("candidates", result.evaluatedCandidateCount)
    }

    private fun confirmMelody(): JSONObject {
        val record = controller.current()?.takeIf { it.status == JourneyStatus.COMPLETED }
            ?: return JSONObject().put("ok", false).put("error", "journey.not_completed")
        val value = preview?.takeIf { it.constellationId == record.constellationId }
            ?: return JSONObject().put("ok", false).put("error", "melody.no_preview")
        return when (val saved = repository.saveBaseMelody(value)) {
            is Outcome.Ok -> JSONObject().put("ok", true).put("melody", melodyJson(value, record, true))
            is Outcome.Err -> JSONObject().put("ok", false).put("error", saved.error.messageKey)
        }
    }

    private fun melodyJson(melody: BaseMelody, record: JourneyRecord, confirmed: Boolean): JSONObject {
        val byStar = record.points.associateBy { it.starId }
        val notes = JSONArray()
        melody.notes.sortedBy { it.startBeat }.forEach { note ->
            val point = byStar[note.starId]
            notes.put(
                JSONObject()
                    .put("starId", note.starId)
                    .put("label", point?.label ?: "별")
                    .put("lat", point?.point?.lat ?: JSONObject.NULL)
                    .put("midi", note.correctedMidi)
                    .put("startBeat", note.startBeat)
                    .put("duration", note.durationBeats)
                    .put("x", note.rotatedPoint.x)
                    .put("y", note.rotatedPoint.y),
            )
        }
        return JSONObject()
            .put("id", melody.id)
            .put("confirmed", confirmed)
            .put("rotation", melody.rotationAngleDeg)
            .put("tempo", melody.tempoBpm)
            .put("key", melody.keySignature.displayName)
            .put("naturalness", melody.naturalnessScore)
            .put("maxSimilarity", melody.maxSimilarity)
            .put("notes", notes)
    }

    /** Runs on [io]; the audio itself is started on the UI thread. */
    private fun playPreview(): JSONObject {
        val value = preview ?: currentConfirmedMelody() ?: return JSONObject().put("ok", false)
        runOnUiThread {
            songPlayer.close()
            playback.deactivate()
            playback.activate()
            playback.play(value)
        }
        return JSONObject().put("ok", true).put("tempo", value.tempoBpm)
    }

    private fun currentConfirmedMelody(): BaseMelody? {
        val record = controller.current()?.takeIf { it.status == JourneyStatus.COMPLETED } ?: return null
        return (repository.loadBaseMelody(record.constellationId) as? Outcome.Ok)?.value?.also { preview = it }
    }

    // ---------------------------------------------------------------- music (DEMO MOCK gateway)

    private fun latestJob(constellationId: String): AiMusicJob? =
        (repository.listAiMusicJobs(constellationId) as? Outcome.Ok)?.value?.firstOrNull()

    private fun musicJson(job: AiMusicJob): JSONObject {
        val playable = job.selectedResult?.localFilePath?.let(::File)?.isFile == true
        return JSONObject()
            .put("status", job.status.name)
            .put("source", job.generationSource.name)
            .put("playable", playable)
            .put("requestId", job.request.requestId)
            .put("error", job.errorCode?.name ?: JSONObject.NULL)
    }

    private fun generateMusic(args: JSONObject): JSONObject {
        val record = controller.current()?.takeIf { it.status == JourneyStatus.COMPLETED }
            ?: return JSONObject().put("ok", false).put("error", "journey.not_completed")
        // Product rule: no provider request before the base melody is explicitly confirmed.
        val melody = (repository.loadBaseMelody(record.constellationId) as? Outcome.Ok)?.value
            ?: return JSONObject().put("ok", false).put("error", "melody.not_confirmed")
        val preferences = ArrangementPreferences(
            genre = enumOr(args.optString("genre"), ArrangementGenre.AMBIENT),
            rhythm = enumOr(args.optString("rhythm"), ArrangementRhythm.STRAIGHT),
            instruments = listOf(enumOr(args.optString("instrument"), ArrangementInstrument.MUSIC_BOX)),
            tempoBpm = args.optInt("tempo", melody.tempoBpm).coerceIn(40, 160),
        )
        val request = AiMusicRequestFactory.create(melody, record.sourceMode, preferences, System.currentTimeMillis())
        musicIo.execute { runMusicRequest(request) }
        return JSONObject().put("ok", true).put("requestId", request.requestId)
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value.uppercase() } ?: fallback

    private fun runMusicRequest(request: AiMusicRequest) {
        var response = musicCoordinator.submitOrResume(request)
        var polls = 0
        while (response is AiMusicGatewayResult.Success && !response.value.status.isTerminal && polls < MAX_MUSIC_POLLS) {
            emit("music", musicJson(response.value))
            try {
                Thread.sleep(MUSIC_POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
            response = musicCoordinator.refresh(response.value)
            polls += 1
        }
        if (response is AiMusicGatewayResult.Success && response.value.status == AiMusicStatus.SUCCEEDED) {
            val completed = response.value
            val selected = completed.selectedResult
            if (selected != null && selected.localFilePath.isNullOrBlank()) {
                emit("music", musicJson(completed).put("status", AiMusicStatus.DOWNLOADING.name))
                val extension = if (selected.mimeType == "audio/mpeg") "mp3" else "wav"
                val target = File(filesDir, "ai_music/${completed.request.requestId}/${selected.id}.$extension")
                response = when (val downloaded = musicGateway.download(selected, target)) {
                    is AiMusicGatewayResult.Failure -> downloaded
                    is AiMusicGatewayResult.Success ->
                        musicCoordinator.saveDownloadedResult(completed, selected.id, downloaded.value.absolutePath)
                }
            }
        }
        when (response) {
            is AiMusicGatewayResult.Success -> emit("music", musicJson(response.value))
            is AiMusicGatewayResult.Failure -> emit(
                "music",
                JSONObject().put("status", "FAILED").put("source", "DEMO_MOCK").put("playable", false)
                    .put("error", response.code.name),
            )
        }
    }

    /** Runs on [io]; plays only the locally saved DEMO MOCK result. */
    private fun playSavedSong(): JSONObject {
        val record = controller.current()?.takeIf { it.status == JourneyStatus.COMPLETED }
            ?: return JSONObject().put("ok", false)
        val job = (repository.listAiMusicJobs(record.constellationId) as? Outcome.Ok)?.value.orEmpty()
            .firstOrNull { it.status == AiMusicStatus.SUCCEEDED && it.selectedResult?.localFilePath?.let(::File)?.isFile == true }
            ?: return JSONObject().put("ok", false)
        val file = File(job.selectedResult!!.localFilePath!!)
        runOnUiThread {
            playback.deactivate()
            songPlayer.play(file)
        }
        return JSONObject().put("ok", true).put("source", job.generationSource.name)
    }

    // ---------------------------------------------------------------- memories

    private fun addDemoPhoto(starId: String, asset: String): JSONObject {
        if (starId.isBlank() || !asset.matches(Regex("[a-z0-9_]+\\.(jpg|png)"))) {
            return JSONObject().put("ok", false).put("error", "memory.invalid_request")
        }
        val directory = File(filesDir, "star-photos").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.${asset.substringAfterLast('.')}")
        val copied = runCatching {
            assets.open("experience/photos/$asset").use { input -> file.outputStream().use { input.copyTo(it) } }
            require(file.length() > 0L)
        }.isSuccess
        if (!copied) return JSONObject().put("ok", false).put("error", "memory.copy_failed")
        return when (val saved = repository.saveStarPhoto(StarPhoto(UUID.randomUUID().toString(), starId, file.absolutePath, System.currentTimeMillis()))) {
            is Outcome.Ok -> photoJson(starId)
            is Outcome.Err -> JSONObject().put("ok", false).put("error", saved.error.messageKey)
        }
    }

    private fun photoJson(starId: String): JSONObject {
        val photos = (repository.listStarPhotos(starId) as? Outcome.Ok)?.value.orEmpty()
        val latest = photos.lastOrNull()?.filePath?.let(::File)?.takeIf { it.isFile }
        val dataUri = latest?.let {
            val mime = if (it.name.endsWith(".png")) "image/png" else "image/jpeg"
            "data:$mime;base64," + Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
        }
        return JSONObject().put("ok", true).put("starId", starId).put("count", photos.size)
            .put("dataUri", dataUri ?: JSONObject.NULL)
    }

    // ---------------------------------------------------------------- Gleo actions

    private fun handleGleoIntent(sourceIntent: Intent) {
        val action = sourceIntent.action ?: return
        if (action == Intent.ACTION_MAIN) return
        when (action) {
            GleoContract.ACTION_CONTROL_STARLOG_JOURNEY -> handleJourneyAction(sourceIntent)
            GleoContract.ACTION_PREPARE_CONSTELLATION_MUSIC,
            GleoContract.ACTION_PLAY_CONSTELLATION_MUSIC -> handleMusicAction(sourceIntent, action)
        }
    }

    private fun readParameters(sourceIntent: Intent): JSONObject? = runCatching {
        val raw = sourceIntent.getStringExtra(GleoContract.EXTRA_ACTION_PARAM) ?: "{}"
        when (val decoded = JSONTokener(raw).nextValue()) {
            is JSONObject -> decoded
            is String -> JSONObject(decoded)
            else -> JSONObject()
        }
    }.getOrNull()

    private fun handleJourneyAction(sourceIntent: Intent) {
        val parameters = readParameters(sourceIntent)
        val request = parameters?.let {
            GleoJourneyActionRequest.from(sourceIntent.action, it.optString("operation", null), it.optString("sourceMode", null))
        }
        io.execute {
            if (request == null) {
                sendJourneyCallback(
                    JourneyControlResponse.Failure(
                        controller.status(SourceMode.REPLAY).snapshot,
                        "The journey control request parameters are invalid.",
                        AppError("INVALID_INPUT", "gleo.invalid_journey_request", false, null),
                    ),
                )
                return@execute
            }
            if (request.operation.name == "END") runOnUiThread { stopReplayTicks() }
            val response = controller.control(request.operation, request.sourceMode)
            sendJourneyCallback(response)
            if (request.operation.name == "START" && response is JourneyControlResponse.Success &&
                response.record?.status == JourneyStatus.ACTIVE
            ) {
                runOnUiThread { startReplayTicks() }
            }
            emit("gleo", JSONObject().put("function", GleoContract.ACTION_CONTROL_STARLOG_JOURNEY)
                .put("operation", request.operation.name).put("response", responseJson(response)))
        }
    }

    private fun sendJourneyCallback(response: JourneyControlResponse) {
        GleoCallbackSender.send(
            context = this,
            functionName = GleoContract.ACTION_CONTROL_STARLOG_JOURNEY,
            status = when (response) {
                is JourneyControlResponse.Success -> GleoCallbackSender.Status.SUCCESS
                is JourneyControlResponse.Asking -> GleoCallbackSender.Status.ASKING
                is JourneyControlResponse.Failure -> GleoCallbackSender.Status.FAIL
            },
            message = response.message,
            data = listOf(GleoJourneyCallbackData.from(response)),
        )
    }

    private fun handleMusicAction(sourceIntent: Intent, functionName: String) {
        val parameters = readParameters(sourceIntent)
        val request = parameters?.let {
            GleoActionRequest.from(
                functionName = functionName,
                constellationId = it.optString("constellationId").takeIf(String::isNotBlank),
                sourceMode = it.optString("sourceMode").takeIf(String::isNotBlank),
                genre = it.optString("genre").takeIf(String::isNotBlank),
            )
        }
        if (request == null) {
            GleoCallbackSender.send(this, functionName, GleoCallbackSender.Status.FAIL, "The StarLog action parameters are invalid.")
            return
        }
        io.execute {
            val record = controller.current()?.takeIf {
                it.status == JourneyStatus.COMPLETED &&
                    (request.constellationId == null || request.constellationId == it.constellationId)
            }
            if (record == null) {
                GleoCallbackSender.send(
                    this, functionName, GleoCallbackSender.Status.ASKING,
                    "A recorded constellation must be selected before this action can continue.",
                )
                return@execute
            }
            val confirmed = (repository.loadBaseMelody(record.constellationId) as? Outcome.Ok)?.value
            val common = mutableMapOf(
                "constellationId" to record.constellationId,
                "sourceMode" to record.sourceMode.name,
                "tripId" to record.id,
            ).apply { confirmed?.let { put("baseMelodyId", it.id) } }
            if (functionName == GleoContract.ACTION_PREPARE_CONSTELLATION_MUSIC) {
                val message = if (confirmed == null) {
                    "The base melody must be confirmed before arranging generated music."
                } else {
                    "Review the arrangement settings and confirm the generation request on screen."
                }
                GleoCallbackSender.send(this, functionName, GleoCallbackSender.Status.ASKING, message, listOf(common))
                emit("gleo", JSONObject().put("function", functionName).put("confirmed", confirmed != null)
                    .put("genre", request.genre?.name ?: JSONObject.NULL))
            } else {
                val job = (repository.listAiMusicJobs(record.constellationId) as? Outcome.Ok)?.value.orEmpty()
                    .firstOrNull { it.status == AiMusicStatus.SUCCEEDED && it.selectedResult?.localFilePath?.let(::File)?.isFile == true }
                val selected = job?.selectedResult
                if (job != null && selected != null) {
                    runOnUiThread {
                        playback.deactivate()
                        songPlayer.play(File(selected.localFilePath!!))
                    }
                    common["jobId"] = job.providerJobId ?: job.request.requestId
                    common["resultId"] = selected.id
                    common["generationSource"] = job.generationSource.name
                    GleoCallbackSender.send(
                        this, functionName, GleoCallbackSender.Status.SUCCESS,
                        "The saved constellation song is now playing.", listOf(common), GleoCallbackSender.Mode.SILENT,
                    )
                    emit("gleo", JSONObject().put("function", functionName).put("playing", true))
                } else {
                    GleoCallbackSender.send(
                        this, functionName, GleoCallbackSender.Status.FAIL,
                        "No playable generated song is saved for the selected constellation.", listOf(common),
                    )
                    emit("gleo", JSONObject().put("function", functionName).put("playing", false))
                }
            }
        }
    }

    companion object {
        private const val LOG_TAG = "StarLogExperience"
        private const val REPLAY_TICK_MS = 2_600L
        private const val MAX_MUSIC_POLLS = 40
        private const val MUSIC_POLL_INTERVAL_MS = 1_500L
        private val TICK_TOKEN = Any()
        private val UI_THREAD_METHODS = setOf("replay.start", "melody.stop", "song.stop")

        private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * 6371.0 * asin(sqrt(a))
        }
    }
}
