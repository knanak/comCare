package com.example.comcare

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException


class ChatService {

    private val url =
        "https://knanak.app.n8n.cloud/webhook/aa5b3dde-db5d-4c58-b383-d36a812fd3d9"

    private val client = OkHttpClient()
    var responseCallback: ((String) -> Unit)? = null

    fun sendChatMessageToWorkflow(userId: String, message: String, sessionId: String) {
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

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle failure
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    responseCallback?.invoke("연결 오류: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        // Log the raw response for debugging
                        Log.d("ChatService", "Raw response: $responseBody")

                        // Check if the response is an array or object
                        if (responseBody.trim().startsWith("[")) {
                            // Handle array response
                            val jsonArray = JSONArray(responseBody)
                            Log.d("ChatService", "Successfully parsed as JSONArray")

                            // Process the first object in the array
                            if (jsonArray.length() > 0) {
                                val mainObject = jsonArray.getJSONObject(0)
                                Log.d("ChatService", "First object in array: $mainObject")

                                // Extract action and response
                                val action = mainObject.optString("action", "")

                                if (action == "parse" && mainObject.has("response")) {
                                    val responseObject = mainObject.getJSONObject("response")

                                    if (responseObject.has("output")) {
                                        val outputObject = responseObject.getJSONObject("output")
                                        val state = outputObject.optString("state", "")

                                        if (state == "성공" && outputObject.has("content")) {
                                            // Extract content array
                                            val contentArray = outputObject.getJSONArray("content")
                                            Log.d("ChatService", "contentArray: $contentArray")

                                            // Combine all content items into a single string
                                            val contentBuilder = StringBuilder()
                                            for (i in 0 until contentArray.length()) {
                                                if (i > 0) contentBuilder.append("\n")
                                                contentBuilder.append(contentArray.getString(i))
                                            }

                                            // Return the formatted content
                                            Handler(Looper.getMainLooper()).post {
                                                responseCallback?.invoke(contentBuilder.toString())
                                            }
                                            return
                                        } else {
                                            Log.e("ChatService", "Invalid state or missing content: $outputObject")
                                        }
                                    } else {
                                        Log.e("ChatService", "Missing output object: $responseObject")
                                    }
                                } else {
                                    Log.e("ChatService", "Unexpected action or missing response: $mainObject")
                                }
                            } else {
                                Log.e("ChatService", "Empty JSON array in response")
                            }
                        } else {
                            // Handle object response (error case)
                            val jsonObject = JSONObject(responseBody)
                            Log.e("ChatService", "Received object instead of array: $jsonObject")

                            // Check if it's an error message
                            if (jsonObject.has("code")) {
                                val errorCode = jsonObject.getInt("code")
                                val errorMessage = jsonObject.optString("message", "Unknown error")
                                Log.e("ChatService", "Error $errorCode: $errorMessage")

                                // Log additional hints if available
                                if (jsonObject.has("hint")) {
                                    Log.e("ChatService", "Hint: " + jsonObject.getString("hint"))
                                }

                                Handler(Looper.getMainLooper()).post {
                                    responseCallback?.invoke("서버 오류 ($errorCode): $errorMessage")
                                }
                                return
                            }
                        }

                        // If we reach here, something unexpected happened with the response format
                        Log.w("ChatService", "Unexpected response format: $responseBody")
                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke("서버 응답을 처리할 수 없습니다.")
                        }

                    } catch (e: Exception) {
                        // Log the exception with stack trace
                        Log.e("ChatService", "Exception while processing response", e)
                        Log.e("ChatService", "Response that caused exception: $responseBody")

                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke("오류가 발생했습니다: ${e.message}")
                        }
                    }
                } ?: run {
                    Log.e("ChatService", "Empty response body")
                    Handler(Looper.getMainLooper()).post {
                        responseCallback?.invoke("서버로부터 응답이 없습니다.")
                    }
                }
            }
        })
    }
}