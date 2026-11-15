package com.example.starlog2.starlog.gleo

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

object GleoCallbackSender {
    enum class Status(val wireValue: String) {
        SUCCESS("success"),
        ASKING("asking"),
        FAIL("fail"),
    }

    enum class Mode(val wireValue: String) {
        VOICE("voice"),
        BEEP("beep"),
        SILENT("silent"),
    }

    fun send(
        context: Context,
        functionName: String,
        status: Status,
        message: String,
        data: List<Map<String, String>> = emptyList(),
        mode: Mode = Mode.VOICE,
    ) {
        require(functionName in GleoContract.supportedActions) { "Unsupported Gleo function: $functionName" }
        val response = JSONObject().apply {
            put("functionName", functionName)
            put("status", status.wireValue)
            put("message", message)
            put("mode", mode.wireValue)
            if (data.isNotEmpty()) {
                put(
                    "data",
                    JSONArray().apply {
                        data.forEach { values -> put(JSONObject(values)) }
                    }.toString(),
                )
            }
        }
        context.sendBroadcast(
            Intent(GleoContract.CALLBACK_ACTION).apply {
                putExtra("packageName", context.packageName)
                putExtra("response", response.toString())
            },
        )
    }
}
