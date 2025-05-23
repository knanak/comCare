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

    // URL을 Pinecone 서버 URL로 변경
    private val url = "http://192.168.219.102:5000/query"

    // 현재 검색 결과들을 저장하는 변수
    private var currentResults: JSONArray? = null
    private var currentIndex: Int = 0

    // 탐색 결과를 위한 별도 저장 변수 - ChatMessage 타입으로 변경
    private var exploreResults: List<ChatMessage> = emptyList()
    private var isExploreMode: Boolean = false

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
    var navigationCallback: ((hasPrevious: Boolean, hasNext: Boolean, currentPage: Int, totalPages: Int) -> Unit)? = null

    fun sendChatMessageToWorkflow(userId: String, message: String, sessionId: String) {
        Log.d(TAG, "==== STARTING NEW CHAT REQUEST ====")
        Log.d(TAG, "Request to URL: $url")
        Log.d(TAG, "message: $message")

        val json = JSONObject().apply {
            put("query", message)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)

        Log.d(TAG, "Request JSON: ${json.toString()}")

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        Log.d(TAG, "Request headers: ${request.headers}")
        Log.d(TAG, "Sending request...")

        // 새로운 검색 시작 시 초기화
        currentResults = null
        currentIndex = 0
        isExploreMode = false
        exploreResults = emptyList()

        // First send a message that we're waiting for the AI
        Handler(Looper.getMainLooper()).post {
            responseCallback?.invoke("AI가 검색중...")
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

                    val bodyBytes = response.body?.bytes()

                    if (bodyBytes == null) {
                        Log.e(TAG, "Response body bytes are null")
                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke("서버로부터 응답이 없습니다. Pinecone 서버를 확인해주세요.")
                        }
                        return
                    }

                    Log.d(TAG, "Response body byte length: ${bodyBytes.size}")

                    if (bodyBytes.isEmpty()) {
                        Log.e(TAG, "Response body is empty (zero bytes)")
                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke("서버에서 빈 응답이 반환되었습니다. Pinecone 서버를 확인해주세요.")
                        }
                        return
                    }

                    val responseBody = String(bodyBytes)
                    Log.d(TAG, "Response body: $responseBody")

                    if (responseBody.isBlank()) {
                        Log.e(TAG, "Response body is blank (only whitespace)")
                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke("서버 응답이 비어 있습니다. Pinecone 서버를 확인해주세요.")
                        }
                        return
                    }

                    // Pinecone 응답 처리 로직
                    try {
                        val jsonResponse = JSONObject(responseBody)

                        if (jsonResponse.has("results")) {
                            val results = jsonResponse.getJSONArray("results")

                            if (results.length() > 0) {
                                // 검색 결과 저장
                                currentResults = results
                                currentIndex = 0

                                // 첫 번째 결과만 표시
                                showCurrentResult()

                            } else {
                                // 검색 결과가 없는 경우
                                Handler(Looper.getMainLooper()).post {
                                    responseCallback?.invoke("검색 결과가 없습니다. 다른 질문을 시도해보세요.")
                                    navigationCallback?.invoke(false, false, 0, 0)
                                }
                                return
                            }
                        } else if (jsonResponse.has("error")) {
                            // 오류 메시지가 있는 경우
                            val errorMessage = jsonResponse.getString("error")
                            Handler(Looper.getMainLooper()).post {
                                responseCallback?.invoke("서버 오류: $errorMessage")
                                navigationCallback?.invoke(false, false, 0, 0)
                            }
                            return
                        } else {
                            // 기본 응답 - 다른 모든 경우
                            Handler(Looper.getMainLooper()).post {
                                responseCallback?.invoke("응답: $responseBody")
                                navigationCallback?.invoke(false, false, 0, 0)
                            }
                        }

                    } catch (e: JSONException) {
                        Log.e(TAG, "Error parsing JSON", e)
                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke("JSON 파싱 오류: ${e.message}\n\n원본 응답: $responseBody")
                            navigationCallback?.invoke(false, false, 0, 0)
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing response", e)
                    Handler(Looper.getMainLooper()).post {
                        responseCallback?.invoke("응답 처리 중 오류가 발생했습니다: ${e.message}")
                        navigationCallback?.invoke(false, false, 0, 0)
                    }
                } finally {
                    response.close()
                }
            }
        })
    }

    // 탐색 결과 설정 - ChatMessage 타입으로 변경
    fun setSearchResults(results: List<ChatMessage>) {
        exploreResults = results
        currentIndex = 0
        isExploreMode = true
        currentResults = null // 일반 검색 결과 초기화

        // 네비게이션 콜백 호출
        Handler(Looper.getMainLooper()).post {
            navigationCallback?.invoke(
                false, // hasPrevious - 첫 번째 결과이므로 false
                results.size > 1, // hasNext
                1, // currentPage
                results.size // totalPages
            )
        }
    }

    // 현재 인덱스의 결과를 표시하는 함수
    private fun showCurrentResult() {
        // 탐색 모드인 경우
        if (isExploreMode && exploreResults.isNotEmpty()) {
            if (currentIndex >= 0 && currentIndex < exploreResults.size) {
                val message = exploreResults[currentIndex]

                Handler(Looper.getMainLooper()).post {
                    responseCallback?.invoke(message.text)

                    // 네비게이션 상태 업데이트
                    val hasPrevious = currentIndex > 0
                    val hasNext = currentIndex < exploreResults.size - 1
                    val currentPage = currentIndex + 1
                    val totalPages = exploreResults.size

                    navigationCallback?.invoke(hasPrevious, hasNext, currentPage, totalPages)
                }
            }
            return
        }

        // 일반 검색 모드
        currentResults?.let { results ->
            if (currentIndex >= 0 && currentIndex < results.length()) {
                try {
                    val currentResult = results.getJSONObject(currentIndex)
                    var content = currentResult.optString("content", "내용 없음")

                    // 응답 포맷팅: | 를 줄바꿈으로 변경하고 가독성 개선
                    content = formatResponse(content)

                    Log.d(TAG, "Showing result $currentIndex: $content")

                    Handler(Looper.getMainLooper()).post {
                        responseCallback?.invoke(content)

                        // 네비게이션 상태 업데이트
                        val hasPrevious = currentIndex > 0
                        val hasNext = currentIndex < results.length() - 1
                        val currentPage = currentIndex + 1
                        val totalPages = results.length()

                        navigationCallback?.invoke(hasPrevious, hasNext, currentPage, totalPages)
                    }
                } catch (e: JSONException) {
                    Log.e(TAG, "Error parsing current result", e)
                    Handler(Looper.getMainLooper()).post {
                        responseCallback?.invoke("결과 파싱 중 오류가 발생했습니다.")
                    }
                }
            }
        }
    }

    // 응답 텍스트를 사용자가 보기 편하게 포맷팅하는 함수
    private fun formatResponse(content: String): String {
        var formatted = content

        // "Showing result X:" 부분 제거
        val showingResultPattern = Regex("^Showing result \\d+:\\s*", RegexOption.IGNORE_CASE)
        formatted = formatted.replace(showingResultPattern, "")

        // "id: XXX" 부분 제거 (줄바꿈 포함)
        val idPattern = Regex("id:\\s*\\d+\\s*[\n\r]*", RegexOption.IGNORE_CASE)
        formatted = formatted.replace(idPattern, "")

        // | 를 줄바꿈으로 변경
        formatted = formatted.replace(" | ", "\n")
        formatted = formatted.replace("|", "\n")

        // 연속된 줄바꿈을 하나로 통합
        formatted = formatted.replace(Regex("\n+"), "\n")

        // 시작과 끝 공백 제거
        formatted = formatted.trim()

        // 각 라인의 앞뒤 공백 제거
        formatted = formatted.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

        // 특정 패턴들을 더 보기 좋게 포맷팅 (Category는 제거)
        formatted = formatted
            .replace(Regex("Category:\\s*[^\\n]*\\n?", RegexOption.IGNORE_CASE), "") // Category 라인 전체 제거
            .replace("Title:", "📋 제목:")
            .replace("DateOfRegistration:", "\n📅 등록일:")
            .replace("Deadline:", "\n⏰ 마감일:")
            .replace("JobCategory:", "\n 직종:")
            .replace("ExperienceRequired:", "\n 경력:")
            .replace("EmploymentType:", "\n 고용형태:")
            .replace("Salary:", "\n💰 급여:")
            .replace("SocialEnsurance:", "\n🛡 사회보험:")
            .replace("RetirementBenefit:", "\n 퇴직혜택:")
            .replace("Location:", "\n📍 주소:")
            .replace("WorkingHours:", "\n⏰ 근무시간:")
            .replace("WorkingType:", "\n 근무형태:")
            .replace("CompanyName:", "\n 회사명:")
            .replace("JobDescription:", "\n 상세설명:")
            .replace("ApplicationMethod:", "\n📝 지원방법:")
            .replace("ApplicationType:", "\n📋 전형방법:")
            .replace("document:", "\n📄 제출서류:")
            .replace("Institution:", "\n📄 기관:")
            .replace("Address:", "\n📍 주소:")
            .replace("Recruitment_period:", "\n⏰ 등록기간:")
            .replace("Education_period:", "\n⏰ 교육기간:")
            .replace("Fee:", "\n💰 비용:")
            .replace("Quota:", "\n 정원:")
            .replace("Service1:", "\n📍")
            .replace("Service2:", "\n📍")
            .replace("Rating:", "\n📝 등급:")
            .replace("Full:", "\n 정원:")
            .replace("Now:", "\n 가능:")
            .replace("Wating:", "\n 대기:")
            .replace("Bus:", "\n\uD83D\uDE8C 방문목욕차량:")
            .replace("Tel:", "\n\uD83D\uDCDE 전화:")

        // 중복된 줄바꿈 다시 한 번 정리
        formatted = formatted.replace(Regex("\n{2,}"), "\n\n")

        // 최종적으로 시작 부분의 공백이나 줄바꿈 제거
        formatted = formatted.trim()

        return formatted
    }

    // 이전 결과로 이동
    fun showPreviousResult(): Boolean {
        if (isExploreMode && exploreResults.isNotEmpty()) {
            if (currentIndex > 0) {
                currentIndex--
                showCurrentResult()
                return true
            }
            return false
        }

        currentResults?.let { results ->
            if (currentIndex > 0) {
                currentIndex--
                showCurrentResult()
                return true
            }
        }
        return false
    }

    // 다음 결과로 이동
    fun showNextResult(): Boolean {
        if (isExploreMode && exploreResults.isNotEmpty()) {
            if (currentIndex < exploreResults.size - 1) {
                currentIndex++
                showCurrentResult()
                return true
            }
            return false
        }

        currentResults?.let { results ->
            if (currentIndex < results.length() - 1) {
                currentIndex++
                showCurrentResult()
                return true
            }
        }
        return false
    }

    // 특정 인덱스로 이동
    fun showResultAtIndex(index: Int): Boolean {
        if (isExploreMode && exploreResults.isNotEmpty()) {
            if (index >= 0 && index < exploreResults.size) {
                currentIndex = index
                showCurrentResult()
                return true
            }
            return false
        }

        currentResults?.let { results ->
            if (index >= 0 && index < results.length()) {
                currentIndex = index
                showCurrentResult()
                return true
            }
        }
        return false
    }

    // 현재 상태 정보 가져오기
    fun getCurrentState(): Triple<Int, Int, Boolean>? {
        if (isExploreMode && exploreResults.isNotEmpty()) {
            return Triple(currentIndex + 1, exploreResults.size, exploreResults.size > 1)
        }

        currentResults?.let { results ->
            return Triple(currentIndex + 1, results.length(), results.length() > 1)
        }
        return null
    }

    // 검색 결과 초기화
    fun clearResults() {
        currentResults = null
        currentIndex = 0
        isExploreMode = false
        exploreResults = emptyList()
    }

    // 현재 결과가 있는지 확인
    fun hasResults(): Boolean {
        if (isExploreMode) {
            return exploreResults.isNotEmpty()
        }
        return currentResults != null && currentResults!!.length() > 0
    }

    // 이전 버튼 활성화 여부
    fun hasPrevious(): Boolean {
        if (isExploreMode) {
            return exploreResults.isNotEmpty() && currentIndex > 0
        }
        return currentResults != null && currentIndex > 0
    }

    // 다음 버튼 활성화 여부
    fun hasNext(): Boolean {
        if (isExploreMode && exploreResults.isNotEmpty()) {
            return currentIndex < exploreResults.size - 1
        }

        currentResults?.let { results ->
            return currentIndex < results.length() - 1
        }
        return false
    }
}