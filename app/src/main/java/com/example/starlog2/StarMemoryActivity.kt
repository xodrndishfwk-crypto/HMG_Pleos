package com.example.starlog2

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.starlog2.contracts.MemoryStar
import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.journey.JourneyRecord
import com.example.starlog2.starlog.journey.StarPhoto
import com.example.starlog2.starlog.storage.SqliteMemoryRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StarMemoryActivity : AppCompatActivity() {
    private lateinit var repository: SqliteMemoryRepository
    private lateinit var starRow: LinearLayout
    private lateinit var detail: TextView
    private lateinit var count: TextView
    private lateinit var preview: ImageView
    private lateinit var addButton: Button
    private lateinit var demoButton: Button
    private val io = Executors.newSingleThreadExecutor()
    private var stars = emptyList<MemoryStar>()
    private var journey: JourneyRecord? = null
    private var selected: MemoryStar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        repository = SqliteMemoryRepository(applicationContext)
        setContentView(buildUi())
        load()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(12))
            setBackgroundColor(BACKGROUND)
        }
        root.addView(buildTopBar(), LinearLayout.LayoutParams(-1, dp(52)))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            background = panelBackground(PANEL, BORDER, 16)
        }
        val information = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("SAVED ARCHIVE · CONSTELLATION MEMORY", 11f, MUTED).apply {
                letterSpacing = 0.12f
            })
            addView(label("별자리 기억의 숲", 28f, Color.WHITE).apply {
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(label("DEMO REPLAY 별자리에서 별을 선택해 여행의 장면을 다시 봅니다.", 13f, MUTED))
        }
        starRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(10))
        }
        information.addView(starRow, LinearLayout.LayoutParams(-1, dp(70)))
        detail = label("별을 선택하세요.", 20f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD }
        count = label("MEMORY 00 / 00", 13f, GOLD)
        information.addView(detail)
        information.addView(count)
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addButton = memoryButton("사진 선택") { openDocument() }.apply {
            isEnabled = false
        }
        demoButton = memoryButton("시연 사진 등록", accent = true) { createDemoPhoto() }.apply {
            isEnabled = false
        }
        buttons.addView(addButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) })
        buttons.addView(demoButton, LinearLayout.LayoutParams(0, dp(42), 1f))
        information.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(16) })
        content.addView(information, LinearLayout.LayoutParams(0, -1, 0.44f).apply { marginEnd = dp(28) })

        val memoryStage = FrameLayout(this).apply {
            background = panelBackground(Color.rgb(5, 9, 17), BORDER, 16)
        }
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(16, 26, 42))
                setStroke(dp(2), GOLD)
            }
            clipToOutline = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        memoryStage.addView(
            preview,
            FrameLayout.LayoutParams(dp(330), dp(330), Gravity.CENTER),
        )
        memoryStage.addView(label("MEMORY ORBIT · 안전하게 앱 내부에 저장", 11f, GOLD).apply {
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, dp(34), Gravity.BOTTOM))
        content.addView(memoryStage, LinearLayout.LayoutParams(0, -1, 0.56f))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(label("SAVED ARCHIVE   ·   선택한 별과 사진 관계를 SQLite에 영속 저장합니다.", 12f, MUTED).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panelBackground(PANEL, BORDER, 12)
            setPadding(dp(18), 0, dp(18), 0)
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        return root
    }

    private fun buildTopBar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label("✦ StarLog", 18f, Color.WHITE).apply {
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(navTab("여정 · 별자리", false))
        addView(navTab("주행 · 기억의 숲", true))
        addView(navTab("여정 종료 · 오토 음악", false))
        addView(label("●  MEMORY ARCHIVE", 11f, GOLD).apply {
            gravity = Gravity.CENTER
            background = panelBackground(PANEL, BORDER, 18)
        }, LinearLayout.LayoutParams(dp(176), dp(34)).apply { marginStart = dp(18) })
    }

    private fun navTab(value: String, selected: Boolean) = TextView(this).apply {
        text = value
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(if (selected) Color.WHITE else MUTED)
        background = if (selected) panelBackground(Color.rgb(17, 22, 32), GOLD_DIM, 18) else null
        setPadding(dp(15), 0, dp(15), 0)
    }

    private fun memoryButton(value: String, accent: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = value
        textSize = 12f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
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

    private fun load() {
        io.execute {
            stars = (repository.listStars(SourceMode.REPLAY) as? Outcome.Ok)?.value.orEmpty()
            journey = (repository.loadLatestJourney(SourceMode.REPLAY) as? Outcome.Ok)?.value
            runOnUiThread {
                starRow.removeAllViews()
                val orderedIds = journey?.points?.filter { it.createsNewStar }?.map { it.starId }.orEmpty()
                stars.filter { orderedIds.isEmpty() || it.id in orderedIds }
                    .sortedBy { orderedIds.indexOf(it.id).let { index -> if (index < 0) Int.MAX_VALUE else index } }
                    .forEach { star ->
                        starRow.addView(memoryButton("★ ${star.title ?: "별"}") { select(star) },
                            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
                    }
                if (stars.isEmpty()) detail.text = "완료된 시연 별자리가 없습니다."
            }
        }
    }

    private fun select(star: MemoryStar) {
        selected = star
        addButton.isEnabled = true
        demoButton.isEnabled = true
        val point = journey?.points?.firstOrNull { it.starId == star.id }
        detail.text = "${star.title ?: point?.label ?: "이름 없는 별"} · 방문 ${point?.visitedAtMs ?: star.createdAtMs}"
        refreshPhotos(star)
    }

    private fun refreshPhotos(star: MemoryStar) {
        io.execute {
            val photos = (repository.listStarPhotos(star.id) as? Outcome.Ok)?.value.orEmpty()
            val bitmap = photos.lastOrNull()?.filePath?.let(BitmapFactory::decodeFile)
            runOnUiThread {
                if (selected?.id != star.id) return@runOnUiThread
                count.text = "MEMORY ${photos.size.toString().padStart(2, '0')} / 05"
                preview.setImageBitmap(bitmap)
            }
        }
    }

    private fun openDocument() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            },
            REQUEST_PHOTO,
        )
    }

    @Deprecated("Legacy result API keeps the demo compatible with the vehicle DocumentsUI stub")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PHOTO && resultCode == Activity.RESULT_OK) {
            data?.data?.let(::copySelectedPhoto)
        }
    }

    private fun copySelectedPhoto(uri: Uri) {
        val star = selected ?: return
        io.execute {
            val file = newPhotoFile()
            val copied = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                    ?: error("Selected photo cannot be opened")
                require(file.length() > 0L)
            }.isSuccess
            if (copied) savePhoto(star, file) else runOnUiThread { detail.text = "사진 등록 실패" }
        }
    }

    private fun createDemoPhoto() {
        val star = selected ?: return
        io.execute {
            val file = newPhotoFile()
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(15, 31, 62))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
            paint.color = Color.rgb(241, 212, 140)
            paint.textSize = 92f
            canvas.drawText("★ STARLOG", 480f, 245f, paint)
            paint.color = Color.WHITE
            paint.textSize = 42f
            canvas.drawText(star.title ?: "DEMO MEMORY", 480f, 335f, paint)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            savePhoto(star, file)
        }
    }

    private fun savePhoto(star: MemoryStar, file: File) {
        val result = repository.saveStarPhoto(
            StarPhoto(UUID.randomUUID().toString(), star.id, file.absolutePath, System.currentTimeMillis()),
        )
        runOnUiThread {
            if (result is Outcome.Ok) refreshPhotos(star)
            else detail.text = "사진 관계 저장 실패"
        }
    }

    private fun newPhotoFile(): File {
        val directory = File(filesDir, "star-photos").apply { mkdirs() }
        return File(directory, "${UUID.randomUUID()}.png")
    }

    override fun onDestroy() {
        io.shutdown()
        Thread {
            try {
                while (!io.awaitTermination(1, TimeUnit.SECONDS)) Unit
                repository.close()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.apply { isDaemon = true; start() }
        super.onDestroy()
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(8), 0, dp(8))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PHOTO = 7102
        val BACKGROUND = Color.rgb(4, 7, 13)
        val PANEL = Color.rgb(9, 13, 21)
        val BORDER = Color.rgb(36, 42, 54)
        val MUTED = Color.rgb(132, 142, 158)
        val GOLD = Color.rgb(229, 200, 136)
        val GOLD_DIM = Color.rgb(118, 101, 67)
    }
}
