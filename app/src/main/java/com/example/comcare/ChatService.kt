package com.example.comcare

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChatService {
    private val TAG = "ChatService"

    private val url =
        "https://knanak.app.n8n.cloud/webhook/aa5b3dde-db5d-4c58-b383-d36a812fd3d9"

    // Create OkHttpClient with logging interceptor to see exactly what's happening
    private val client: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    var responseCallback: ((String) -> Unit)? = null

    fun sendChatMessageToWorkflow(userId: String, message: String, sessionId: String) {
        Log.d(TAG, "==== STARTING NEW CHAT REQUEST ====")
        Log.d(TAG, "Request to URL: $url")
        Log.d(TAG, "userId: $userId, sessionId: $sessionId, message: $message")

        val json = JSONObject().apply {
            put("userId", userId)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
            put("sessionId", sessionId)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        Log.d(TAG, "Sending request with body: ${json.toString()}")

        // First send a message that we're waiting for the AI
        Handler(Looper.getMainLooper()).post {
            responseCallback?.invoke("AI 응답을 기다리는 중...")
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network call failed", e)
                Log.e(TAG, "Request URL: ${call.request().url}")
                Log.e(TAG, "Request method: ${call.request().method}")
                Log.e(TAG, "Exception message: ${e.message}")
                Log.e(TAG, "Exception cause: ${e.cause}")

                Handler(Looper.getMainLooper()).post {
                    responseCallback?.invoke("연결 오류: ${e.message ?: "알 수 없는 오류"}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    Log.d(TAG, "==== RECEIVED RESPONSE ====")
                    Log.d(TAG, "Response code: ${response.code}")
                    Log.d(TAG, "Response message: ${response.message}")
                    Log.d(TAG, "Response headers: ${response.headers}")

                    // Try debug the actual raw bytes
                    val bodyBytes = response.body?.bytes()

                    if (bodyBytes == null) {
                        Log.e(TAG, "Response body bytes are null")
                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke("서버로부터 응답이 없습니다. n8n 워크플로우를 확인해주세요.")
                        }
                        return
                    }

                    Log.d(TAG, "Response body byte length: ${bodyBytes.size}")

                    if (bodyBytes.isEmpty()) {
                        Log.e(TAG, "Response body is empty (zero bytes)")
                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke("서버에서 빈 응답이 반환되었습니다. n8n 워크플로우에서 응답을 설정했는지 확인해주세요.")
                        }
                        return
                    }

                    // Try to convert bytes to string
                    val responseBody = String(bodyBytes)
                    Log.d(TAG, "Response body: $responseBody")

                    if (responseBody.isBlank()) {
                        Log.e(TAG, "Response body is blank (only whitespace)")
                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke("서버 응답이 비어 있습니다. n8n 워크플로우를 확인해주세요.")
                        }
                        return
                    }

                    // Try to parse the response as JSON
                    try {
                        when {
                            responseBody.trim().startsWith("[") -> {
                                // It's a JSON array
                                val jsonArray = JSONArray(responseBody)
                                extractContentFromJsonArray(jsonArray)
                            }
                            responseBody.trim().startsWith("{") -> {
                                // It's a JSON object
                                val jsonObject = JSONObject(responseBody)
                                extractContentFromJsonObject(jsonObject)
                            }
                            else -> {
                                // It's not JSON, return as plain text
                                Log.d(TAG, "Response is not JSON, returning as plain text")
                                Handler(Looper.getMainLooper()).post {
                                    responseCallback?.invoke(responseBody)
                                }
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Error parsing JSON", e)
                        // If it's not valid JSON, just return the raw response
                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke("JSON 파싱 오류: ${e.message}\n\n원본 응답: $responseBody")
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing response", e)
                    Handler(Looper.getMainLooper()).post {
                        responseCallback?.invoke("응답 처리 중 오류가 발생했습니다: ${e.message}")
                    }
                } finally {
                    response.close()
                }
            }
        })
    }


    private fun extractContentFromJsonArray(jsonArray: JSONArray) {
        Log.d(TAG, "Extracting content from JSON array")

        // Check for the simplified structure from your example
        if (jsonArray.length() > 0) {
            try {
                val mainObject = jsonArray.getJSONObject(0)

                if (mainObject.has("output")) {
                    val outputArray = mainObject.getJSONArray("output")

                    if (outputArray.length() > 0) {
                        val outputItem = outputArray.getJSONObject(0)

                        if (outputItem.has("content")) {
                            val contentArray = outputItem.getJSONArray("content")
                            val contentText = buildStringFromJsonArray(contentArray)

                            Log.d(TAG, "Found content using simplified structure: $contentText")
                            Handler(Looper.getMainLooper()).post {
                                responseCallback?.invoke(contentText)
                            }
                            return
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing simplified structure", e)
            }
        }

        // If the simplified approach failed, try a more general approach
        try {
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.opt(i)
                if (item is JSONObject) {
                    val contentText = searchForContentRecursively(item)
                    if (contentText != null) {
                        Log.d(TAG, "Found content using recursive search: $contentText")
                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke(contentText)
                        }
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in general content search", e)
        }

        // If we couldn't find content, return a useful error with the full response
        Log.w(TAG, "Could not find any content in the response")
        Handler(Looper.getMainLooper()).post {
            responseCallback?.invoke("응답에서 콘텐츠를 찾을 수 없습니다. 전체 응답: " + jsonArray.toString(2))
        }
    }

    private fun extractContentFromJsonObject(jsonObject: JSONObject) {
        Log.d(TAG, "Extracting content from JSON object")

        // Try to find content recursively
        val contentText = searchForContentRecursively(jsonObject)

        if (contentText != null) {
            Log.d(TAG, "Found content: $contentText")
            Handler(Looper.getMainLooper()).post {
                responseCallback?.invoke(contentText)
            }
            return
        }

        // If we couldn't find content, return a useful error with the full response
        Log.w(TAG, "Could not find any content in the response")
        Handler(Looper.getMainLooper()).post {
            responseCallback?.invoke("응답에서 콘텐츠를 찾을 수 없습니다. 전체 응답: " + jsonObject.toString(2))
        }
    }

    private fun searchForContentRecursively(json: Any?): String? {
        if (json == null) return null

        when (json) {
            is JSONObject -> {
                // Direct check for content array
                if (json.has("content")) {
                    try {
                        val content = json.opt("content")
                        if (content is JSONArray) {
                            return buildStringFromJsonArray(content)
                        } else if (content is String) {
                            return content
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting content", e)
                    }
                }

                // Check all keys recursively
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val result = searchForContentRecursively(json.opt(key))
                    if (result != null) {
                        return result
                    }
                }
            }
            is JSONArray -> {
                // If the array itself is named content, build string from it
                if (json.length() > 0) {
                    // First try to build directly from this array
                    try {
                        val allStrings = (0 until json.length()).all {
                            json.opt(it) is String
                        }
                        if (allStrings) {
                            return buildStringFromJsonArray(json)
                        }
                    } catch (e: Exception) {
                        // Continue with recursive search
                    }

                    // Then try each element recursively
                    for (i in 0 until json.length()) {
                        val result = searchForContentRecursively(json.opt(i))
                        if (result != null) {
                            return result
                        }
                    }
                }
            }
        }

        return null
    }

    private fun buildStringFromJsonArray(jsonArray: JSONArray): String {
        val stringBuilder = StringBuilder()
        for (i in 0 until jsonArray.length()) {
            if (i > 0) stringBuilder.append("\n")
            stringBuilder.append(jsonArray.optString(i, ""))
        }
        return stringBuilder.toString()
    }
}