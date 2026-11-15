package com.example.starlog2

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setBackgroundColor(Color.rgb(7, 12, 26))
        }
        root.addView(label("별을 선택해 추억 보기", 28f, Color.rgb(241, 212, 140)))
        root.addView(label("DEMO REPLAY 별자리 · 사진은 앱 내부에 안전하게 복사됩니다.", 14f, Color.LTGRAY))
        starRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        root.addView(starRow)
        detail = label("별을 선택하세요.", 19f, Color.WHITE)
        count = label("사진 0장", 16f, Color.rgb(170, 200, 235))
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(20, 31, 54))
        }
        root.addView(detail)
        root.addView(count)
        root.addView(preview, LinearLayout.LayoutParams(-1, dp(260)))
        addButton = Button(this).apply {
            text = "시스템에서 사진 선택"
            isEnabled = false
            setOnClickListener { openDocument() }
        }
        demoButton = Button(this).apply {
            text = "시연 사진 등록 (DEMO)"
            isEnabled = false
            setOnClickListener { createDemoPhoto() }
        }
        root.addView(addButton)
        root.addView(demoButton)
        return ScrollView(this).apply { addView(root) }
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
                        starRow.addView(Button(this).apply {
                            text = "★\n${star.title ?: "별"}"
                            setOnClickListener { select(star) }
                        })
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
                count.text = "사진 ${photos.size}장"
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

    companion object { private const val REQUEST_PHOTO = 7102 }
}
