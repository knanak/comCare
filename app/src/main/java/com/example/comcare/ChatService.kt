
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
        "https://n8n.biseo.store/webhook/aa5b3dde-db5d-4c58-b383-d36a812fd3d9"

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
                        // First check if this is a double-encoded JSON
                        if (responseBody.trim().startsWith("{\"output\":\"")) {
                            Log.d(TAG, "Detected double-encoded JSON")
                            try {
                                // Parse the outer JSON first
                                val outerJson = JSONObject(responseBody)
                                if (outerJson.has("output")) {
                                    // Get the inner JSON string
                                    val innerJsonString = outerJson.optString("output", "")
                                    if (innerJsonString.isNotEmpty()) {
                                        // Check if the inner content starts with markdown code block
                                        if (innerJsonString.trim().startsWith("```")) {
                                            Log.d(TAG, "Detected markdown code block in output")
                                            // Extract content between code blocks
                                            val cleanJsonString = extractJsonFromMarkdown(innerJsonString)
                                            if (cleanJsonString.isNotEmpty()) {
                                                try {
                                                    val extractedJson = JSONObject(cleanJsonString)
                                                    parseAndFormatResponse(extractedJson)
                                                    return
                                                } catch (e: JSONException) {
                                                    Log.e(TAG, "Failed to parse JSON from markdown", e)
                                                }
                                            }
                                        } else {
                                            // Normal JSON string without markdown
                                            try {
                                                val innerJson = JSONObject(innerJsonString)
                                                parseAndFormatResponse(innerJson)
                                                return
                                            } catch (e: JSONException) {
                                                // If inner JSON parsing fails, continue with normal flow
                                                Log.e(TAG, "Failed to parse inner JSON", e)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error handling double-encoded JSON", e)
                            }
                        }

                        // Continue with standard JSON parsing
                        when {
                            responseBody.trim().startsWith("[") -> {
                                // It's a JSON array
                                val jsonArray = JSONArray(responseBody)
                                extractContentFromJsonArray(jsonArray)
                            }
                            responseBody.trim().startsWith("{") -> {
                                // It's a JSON object
                                val jsonObject = JSONObject(responseBody)
                                parseAndFormatResponse(jsonObject)
                            }
                            else -> {
                                // It's not JSON, return as plain text
                                Log.d(TAG, "Response is not JSON, returning as plain text")
                                // 일반 텍스트에서도 '\n' 텍스트를 줄바꿈으로 변경
                                val formattedText = responseBody.replace("\\n", "\n")
                                Handler(Looper.getMainLooper()).post {
                                    responseCallback?.invoke(formattedText)
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

    /**
     * 마크다운 코드 블록에서 JSON 문자열을 추출하는 헬퍼 함수
     * ```json
     * { ... }
     * ```
     * 와 같은 형식에서 { ... } 부분만 추출
     */
    private fun extractJsonFromMarkdown(markdownString: String): String {
        Log.d(TAG, "Extracting JSON from markdown string")

        // Remove the opening markdown code block marker (```json or just ```)
        val withoutOpening = markdownString.replace(Regex("^```(json)?\\s*\\n"), "")

        // Remove the closing markdown code block marker (```)
        val withoutClosing = withoutOpening.replace(Regex("\\n```\\s*$"), "")

        Log.d(TAG, "Extracted JSON from markdown: $withoutClosing")
        return withoutClosing.trim()
    }

    /**
     * 특정 응답 구조를 위한 새로운 메서드
     * Response: {"output": {"content": ["line1", "line2", ...]}}
     */
    private fun parseAndFormatResponse(jsonObject: JSONObject) {
        Log.d(TAG, "Parsing and formatting response: ${jsonObject.toString()}")

        try {
            // 1. 특정 구조 확인: {"output": {"content": [...]}}
            if (jsonObject.has("output")) {
                // 출력 필드가 문자열인지 객체인지 확인
                val output = jsonObject.opt("output")

                if (output is JSONObject && output.has("content")) {
                    // Case 1: output is a JSON object with content field
                    val content = output.optJSONArray("content")

                    if (content != null && content.length() > 0) {
                        // 2. content 배열의 모든 항목을 줄바꿈으로 연결
                        val formattedContent = formatContentArray(content)

                        Log.d(TAG, "Formatted content: $formattedContent")

                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke(formattedContent)
                        }
                        return
                    }
                } else if (output is String) {
                    // Case 2: output is a string that might be a JSON
                    try {
                        val outputJson = JSONObject(output.toString())
                        if (outputJson.has("content")) {
                            val content = outputJson.optJSONArray("content")

                            if (content != null && content.length() > 0) {
                                // content 배열의 모든 항목을 줄바꿈으로 연결
                                val formattedContent = formatContentArray(content)

                                Log.d(TAG, "Formatted content from string output: $formattedContent")

                                Handler(Looper.getMainLooper()).post {
                                    responseCallback?.invoke(formattedContent)
                                }
                                return
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Output string is not a valid JSON", e)
                    }
                }
            }

            // 특정 구조가 아닌 경우 일반적인 방법으로 처리
            Log.d(TAG, "Specific structure not found, falling back to general method")
            extractContentFromJsonObject(jsonObject)

        } catch (e: Exception) {
            Log.e(TAG, "Error in parseAndFormatResponse", e)
            Handler(Looper.getMainLooper()).post {
                responseCallback?.invoke("응답 파싱 중 오류가 발생했습니다: ${e.message}\n\n원본 응답: ${jsonObject.toString()}")
            }
        }
    }

    /**
     * JSONArray에서 content 문자열을 추출하고 포맷팅하는 헬퍼 함수
     */
    private fun formatContentArray(content: JSONArray): String {
        val formattedContent = StringBuilder()

        for (i in 0 until content.length()) {
            val line = content.optString(i, "")
                .replace("\\n", "\n") // 이스케이프된 줄바꿈을 실제 줄바꿈으로 변환

            formattedContent.append(line)

            // 마지막 항목이 아니면 줄바꿈 추가
            if (i < content.length() - 1) {
                formattedContent.append("\n\n") // 두 줄 띄우기 적용
            }
        }

        return formattedContent.toString()
    }

    private fun extractContentFromJsonArray(jsonArray: JSONArray) {
        Log.d(TAG, "Extracting content from JSON array")

        // Check for the simplified structure from your example
        if (jsonArray.length() > 0) {
            try {
                val mainObject = jsonArray.getJSONObject(0)

                if (mainObject.has("output")) {
                    // Try to parse using the new method first
                    parseAndFormatResponse(mainObject)
                    return
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
            responseCallback?.invoke("응답에서 콘텐츠를 찾을 수 없습니다.\n\n디버깅 정보: " + jsonArray.toString(2))
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
            responseCallback?.invoke("응답에서 콘텐츠를 찾을 수 없습니다.\n\n디버깅 정보: " + jsonObject.toString(2))
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
                        if (content is JSONArray && content.length() > 0) {
                            // content 배열의 모든 항목을 두 줄 띄우기로 연결
                            return formatContentArray(content)
                        } else if (content is String) {
                            // 문자열에서 '\n' 텍스트를 실제 줄바꿈으로 변환
                            return content.replace("\\n", "\n\n") // 두 줄 띄우기로 변경
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
                            return formatContentArray(json)
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
        return formatContentArray(jsonArray)
    }
}