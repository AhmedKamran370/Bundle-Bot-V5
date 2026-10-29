package com.bundlebot

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Ai {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private fun call(prompt: String): String {
        val key = Store.aiKey()
        if (key.isBlank()) return "Add your free Gemini API key in Setup first."
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key")
            .post(body).build()
        return try {
            http.newCall(req).execute().use { r ->
                val s = r.body?.string() ?: ""
                val j = JSONObject(s)
                j.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text")
            }
        } catch (e: Exception) { "AI unavailable: ${e.message}" }
    }

    fun ask(q: String): String = call("You are the in-app assistant for Nabeel, an Android app that sells mobile data bundles via WhatsApp. Answer briefly in simple English. Question: $q")

    fun parseMessage(raw: String): JSONObject? {
        val prompt = """Extract from this WhatsApp message. Return JSON with keys number, gb, days, name, network, price. If a key is unknown, use 0 or "". Message: "$raw""""
        val out = call(prompt)
        return try {
            val start = out.indexOf("{"); val end = out.lastIndexOf("}")
            if (start < 0 || end < 0) null else JSONObject(out.substring(start, end + 1))
        } catch (e: Exception) { null }
    }
}