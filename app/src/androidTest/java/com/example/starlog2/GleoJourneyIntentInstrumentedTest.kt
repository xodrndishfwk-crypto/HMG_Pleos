package com.example.starlog2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.gleo.GleoContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class GleoJourneyIntentInstrumentedTest {
    @Test
    fun statusIntent_returnsContractPayloadWithoutMutatingJourney() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val controller = (context.applicationContext as StarlogApplication).journeyController
        val before = controller.status(SourceMode.REPLAY).snapshot
        val received = AtomicReference<JSONObject?>()
        val latch = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra("response")?.let { received.set(JSONObject(it)) }
                latch.countDown()
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(GleoContract.CALLBACK_ACTION),
            Context.RECEIVER_EXPORTED,
        )
        var activity: StarLogDemoActivity? = null
        try {
            activity = instrumentation.startActivitySync(
                Intent(context, StarLogDemoActivity::class.java).apply {
                    action = GleoContract.ACTION_CONTROL_STARLOG_JOURNEY
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(GleoContract.EXTRA_ACTION_PARAM, "{\"operation\":\"STATUS\",\"sourceMode\":\"REPLAY\"}")
                },
            ) as StarLogDemoActivity
            check(latch.await(7, TimeUnit.SECONDS)) { "Gleo callback exceeded seven seconds" }
            val callback = received.get()
            assertNotNull(callback)
            assertEquals(GleoContract.ACTION_CONTROL_STARLOG_JOURNEY, callback!!.getString("functionName"))
            assertEquals("success", callback.getString("status"))
            val data = JSONArray(callback.getString("data")).getJSONObject(0)
            assertEquals(before.journeyStatus, data.getString("journeyStatus"))
            assertEquals(before.sourceMode.name, data.getString("sourceMode"))
            assertEquals(before.recordedPointCount.toString(), data.getString("recordedPointCount"))
            assertEquals(before, controller.status(SourceMode.REPLAY).snapshot)
        } finally {
            context.unregisterReceiver(receiver)
            activity?.let { value -> instrumentation.runOnMainSync { value.finish() } }
        }
    }
}
