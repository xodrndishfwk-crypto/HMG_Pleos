package com.example.starlog2

import android.graphics.Color
import android.os.Bundle
import timber.log.Timber
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue

class MainActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private lateinit var readButton: Button
    private var vehicle: Vehicle? = null
    private var initialized = false
    private var destroyed = false
    private var requestInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
        readButton.setOnClickListener { readDoorPosition() }
        initializeVehicle()
    }

    private fun createContentView(): LinearLayout {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                (32 * density).toInt(),
                (32 * density).toInt(),
                (32 * density).toInt(),
                (32 * density).toInt(),
            )
            setBackgroundColor(Color.WHITE)

            addView(TextView(context).apply {
                setText(R.string.door_diagnostic_title)
                textSize = 28f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            statusView = TextView(context).apply {
                text = "초기화 중…"
                textSize = 18f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(0, (24 * density).toInt(), 0, (24 * density).toInt())
            }
            addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            readButton = Button(context).apply {
                text = "1열 왼쪽 도어 위치 다시 읽기"
                isEnabled = false
            }
            addView(readButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun initializeVehicle() {
        showState("초기화 중…", busy = true)
        try {
            vehicle = Vehicle(applicationContext).also { it.initialize() }
            initialized = true
            showState("초기화 호출 완료. 실제 도어 값을 조회합니다.", busy = false)
            readDoorPosition()
        } catch (error: Exception) {
            initialized = false
            Timber.tag(TAG).e(error, "Vehicle 초기화 실패")
            showError("초기화 오류", error)
        }
    }

    private fun readDoorPosition() {
        if (!initialized || requestInFlight || destroyed) return
        val currentVehicle = vehicle ?: return
        showState("조회 중: ROW_1_LEFT", busy = true)

        try {
            val door = currentVehicle.getDoor()
            door.getCurrentDoorPosition(
                area = DoorArea(DoorValue.ROW_1_LEFT),
                onComplete = { position: Int? ->
                    runOnUiThread {
                        if (destroyed) return@runOnUiThread
                        if (position == null) {
                            showState("값 없음: ROW_1_LEFT", busy = false)
                        } else {
                            showState("조회 성공: ROW_1_LEFT\n원본 정숫값: $position", busy = false)
                        }
                    }
                },
                onFailed = { error: Exception ->
                    Timber.tag(TAG).e(error, "도어 위치 조회 실패")
                    runOnUiThread {
                        if (!destroyed) showError("조회 오류", error)
                    }
                },
            )
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "도어 위치 조회 호출 실패")
            showError("조회 호출 오류", error)
        }
    }

    private fun showState(message: String, busy: Boolean) {
        requestInFlight = busy
        statusView.text = message
        readButton.isEnabled = initialized && !busy && !destroyed
    }

    private fun showError(prefix: String, error: Exception) {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        showState("$prefix: $detail", busy = false)
    }

    override fun onDestroy() {
        destroyed = true
        requestInFlight = false
        initialized = false
        val currentVehicle = vehicle
        vehicle = null
        if (currentVehicle != null) {
            try {
                currentVehicle.release()
            } catch (error: Exception) {
                Timber.tag(TAG).e(error, "Vehicle 해제 실패")
            }
        }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Starlog2"
    }
}
