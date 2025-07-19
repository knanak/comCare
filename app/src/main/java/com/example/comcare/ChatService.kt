package com.example.comcare

import android.content.Context
import android.content.SharedPreferences
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
import java.util.Calendar
import java.util.Date

class ChatService(private val context: Context) {
    private val TAG = "ChatService"

    private val url = "http://192.168.219.101:5000/query"
//    private val url = "https://coral-app-fjt8m.ondigitalocean.app/query"

    // SharedPreferences for storing count and date
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
    private val REQUEST_COUNT_KEY = "request_count"
    private val LAST_REQUEST_DATE_KEY = "last_request_date"
    private val MAX_REQUESTS_PER_DAY = 100

    // 최근 저장된 SearchHistory ID를 저장
    var lastSearchHistoryId: String? = null

    // 현재 검색 결과들을 저장하는 변수
    private var currentResults: JSONArray? = null
    private var currentIndex: Int = 0

    // 탐색 결과를 위한 별도 저장 변수 - ChatMessage 타입으로 변경
    private var exploreResults: List<ChatMessage> = emptyList()
    private var isExploreMode = false

    // SearchHistory 저장을 위한 임시 변수 추가
    var lastSearchCategory: String? = null
    var lastSearchAnswer: String? = null
    var lastQueryContent: String? = null  // 마지막 질문 저장용 추가

    private var resumeData = mutableMapOf<String, String>()
    private var isResumeInProgress = false
    private var currentResumeStep = ""

    // 이력서 데이터 초기화
    private fun initializeResumeData() {
        resumeData.clear()
        resumeData["name"] = ""
        resumeData["gender"] = ""
        resumeData["birthDate"] = ""
        resumeData["address"] = ""
        resumeData["phone"] = ""
        resumeData["school"] = ""
        resumeData["major"] = ""
        resumeData["company"] = ""
        resumeData["workPeriod"] = ""
        resumeData["workDuties"] = ""
        resumeData["certificate"] = ""
        resumeData["driving"] = ""
        resumeData["vehicle"] = ""
        resumeData["strength"] = ""
        resumeData["weakness"] = ""
        isResumeInProgress = false
        currentResumeStep = ""
    }

    // 이력서 단계별 데이터 저장
    private fun saveResumeStep(step: String, userInput: String) {
        when (step) {
            "personal_name" -> resumeData["name"] = userInput
            "personal_gender" -> resumeData["gender"] = userInput
            "personal_birth" -> resumeData["birthDate"] = userInput
            "personal_address" -> resumeData["address"] = userInput
            "personal_phone" -> resumeData["phone"] = userInput
            "education_school" -> resumeData["school"] = userInput
            "education_major" -> resumeData["major"] = userInput
            "career_company" -> resumeData["company"] = userInput
            "career_period" -> resumeData["workPeriod"] = userInput
            "career_duties" -> resumeData["workDuties"] = userInput
            "skill_certificate" -> resumeData["certificate"] = userInput
            "skill_driving" -> resumeData["driving"] = userInput
            "skill_vehicle" -> resumeData["vehicle"] = userInput
            "strength" -> resumeData["strength"] = userInput
            "weakness" -> resumeData["weakness"] = userInput
        }

        Log.d(TAG, "Resume step saved: $step = $userInput")
    }

    // 완성된 이력서 생성
    private fun generateFormattedResume(): String {
        val formattedResume = StringBuilder()
        formattedResume.append("📋 완성된 이력서\n\n")

        // (1) 인적 사항
        formattedResume.append("(1) 인적 사항\n")
        formattedResume.append("1. 이름: ${resumeData["name"]?.ifEmpty { "미입력" } ?: "미입력"}\n")
        formattedResume.append("2. 성별: ${resumeData["gender"]?.ifEmpty { "미입력" } ?: "미입력"}\n")
        formattedResume.append("3. 생년월일: ${resumeData["birthDate"]?.ifEmpty { "미입력" } ?: "미입력"}\n")
        formattedResume.append("4. 주소: ${resumeData["address"]?.ifEmpty { "미입력" } ?: "미입력"}\n")
        formattedResume.append("5. 연락처: ${resumeData["phone"]?.ifEmpty { "미입력" } ?: "미입력"}\n\n")

        // (2) 최종 학력
        formattedResume.append("(2) 최종 학력\n")
        formattedResume.append("1. 학교명: ${resumeData["school"]?.ifEmpty { "미입력" } ?: "미입력"}\n")
        formattedResume.append("2. 전공명: ${resumeData["major"]?.ifEmpty { "미입력" } ?: "미입력"}\n\n")

        // (3) 경력 사항
        formattedResume.append("(3) 경력 사항\n")
        formattedResume.append("1. 회사명: ${resumeData["company"]?.ifEmpty { "미입력" } ?: "미입력"}\n")
        formattedResume.append("2. 근무기간: ${resumeData["workPeriod"]?.ifEmpty { "미입력" } ?: "미입력"}\n")
        formattedResume.append("3. 담당 업무: ${resumeData["workDuties"]?.ifEmpty { "미입력" } ?: "미입력"}\n\n")

        // (4) 보유 역량
        formattedResume.append("(4) 보유 역량\n")
        formattedResume.append("1. 자격증: ${resumeData["certificate"]?.ifEmpty { "미입력" } ?: "미입력"}\n")
        formattedResume.append("2. 운전: ${resumeData["driving"]?.ifEmpty { "미입력" } ?: "미입력"}\n")
        formattedResume.append("3. 차량 소유: ${resumeData["vehicle"]?.ifEmpty { "미입력" } ?: "미입력"}\n")
        formattedResume.append("4. 장점: ${resumeData["strength"]?.ifEmpty { "미입력" } ?: "미입력"}\n")
        formattedResume.append("5. 단점: ${resumeData["weakness"]?.ifEmpty { "미입력" } ?: "미입력"}\n")

        return formattedResume.toString()
    }

    private fun sendResumeToServer(resumeContent: String, userId: String) {
        Log.d(TAG, "이력서 서버 전송 시작")

        val resumeUrl = "http://192.168.219.101:5000/resume"
//    val resumeUrl = "https://coral-app-fjt8m.ondigitalocean.app/resume"

        // MainActivity에서 kakao_id 정보 가져오기
        val mainActivity = context as? MainActivity
        val kakaoId = mainActivity?.currentUserKakaoId

        // userId 분석 및 실제 사용자 ID 추출
        val (actualKakaoId, actualUserId) = if (userId.startsWith("kakao_")) {
            val extractedKakaoId = userId.removePrefix("kakao_")
            Pair(extractedKakaoId, null)
        } else {
            Pair(kakaoId, userId) // kakaoId가 있으면 함께 전송
        }

        // 현재 시간을 ISO 8601 형식으로 생성
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())

        val json = JSONObject().apply {
            // ✅ kakao_id와 user_id 모두 전송
            if (actualKakaoId != null) {
                put("kakao_id", actualKakaoId)
                Log.d(TAG, "카카오 ID 설정: $actualKakaoId")
            } else {
                put("kakao_id", JSONObject.NULL)
            }

            if (actualUserId != null) {
                put("user_id", actualUserId)
                Log.d(TAG, "사용자 ID 설정: $actualUserId")
            } else {
                put("user_id", JSONObject.NULL)
            }

            put("resume_content", resumeContent)
            put("timestamp", currentTime)
            put("source", "android_app")

            // 기존 user_identifier도 호환성을 위해 유지
            put("user_identifier", userId)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)

        Log.d(TAG, "이력서 전송 요청 JSON: ${json.toString()}")

        val request = Request.Builder()
            .url(resumeUrl)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        // 별도의 스레드에서 전송 (비동기)
        Thread {
            try {
                val response = client.newCall(request).execute()
                response.use {
                    Log.d(TAG, "이력서 서버 전송 응답 코드: ${response.code}")
                    val responseBody = response.body?.string()
                    Log.d(TAG, "이력서 서버 전송 응답 본문: $responseBody")

                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ 이력서 서버 전송 성공")

                        // 응답 파싱하여 결과 확인
                        try {
                            val responseJson = JSONObject(responseBody ?: "{}")
                            val success = responseJson.optBoolean("success", false)
                            val message = responseJson.optString("message", "")
                            val kakaoIdFromResponse = responseJson.optString("kakao_id", "")
                            val userIdFromResponse = responseJson.optString("user_id", "")

                            Log.d(TAG, "서버 응답 - success: $success")
                            Log.d(TAG, "서버 응답 - message: $message")
                            Log.d(TAG, "서버 응답 - kakao_id: $kakaoIdFromResponse")
                            Log.d(TAG, "서버 응답 - user_id: $userIdFromResponse")

                            if (success) {
                                // 성공 메시지를 UI에 표시
                                Handler(Looper.getMainLooper()).post {
                                    Log.d(TAG, "이력서가 서버에 성공적으로 저장되었습니다.")
                                }
                            } else {
                                Log.e(TAG, "서버에서 실패 응답: $message")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "응답 JSON 파싱 오류: ${e.message}")
                        }
                    } else {
                        Log.e(TAG, "❌ 이력서 서버 전송 실패: ${response.code} - ${response.message}")
                        Log.e(TAG, "응답 본문: $responseBody")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "이력서 서버 전송 중 오류 발생", e)
            }
        }.start()
    }


    // 탐색 모드 상태 확인 및 설정
    fun setExploreMode(enabled: Boolean) {
        isExploreMode = enabled
    }

    fun isInExploreMode(): Boolean {
        return isExploreMode
    }


    // 탐색 모드인지 추적하는 변수 추가
    private var isCurrentlyExploring: Boolean = false

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

    var exploreResponseCallback: ((String) -> Unit)? = null

    // 네비게이션 상태를 영구적으로 저장하기 위한 변수 추가
    private var savedNavigationState: NavigationState? = null

    // NavigationState 데이터 클래스 추가
    data class NavigationState(
        val hasPrevious: Boolean,
        val hasNext: Boolean,
        val currentPage: Int,
        val totalPages: Int
    )

    // 네비게이션 상태 저장 메서드 추가
    fun saveNavigationState() {
        val state = getCurrentNavigationState()
        savedNavigationState = state
        Log.d(TAG, "Navigation state saved: $state")
    }

    // 현재 네비게이션 상태 가져오기
    fun getCurrentNavigationState(): NavigationState? {
        return if (isExploreMode && exploreResults.isNotEmpty()) {
            NavigationState(
                hasPrevious = currentIndex > 0,
                hasNext = currentIndex < exploreResults.size - 1,
                currentPage = currentIndex + 1,
                totalPages = exploreResults.size
            )
        } else if (currentResults != null && currentResults!!.length() > 0) {
            NavigationState(
                hasPrevious = currentIndex > 0,
                hasNext = currentIndex < currentResults!!.length() - 1,
                currentPage = currentIndex + 1,
                totalPages = currentResults!!.length()
            )
        } else {
            null
        }
    }

    // 네비게이션 상태 복원 메서드 추가
    fun restoreNavigationState() {
        savedNavigationState?.let { state ->
            Log.d(TAG, "Restoring navigation state: $state")
            navigationCallback?.invoke(
                state.hasPrevious,
                state.hasNext,
                state.currentPage,
                state.totalPages
            )
        }
    }


    // 오늘 날짜 확인 및 카운트 초기화
    private fun checkAndResetDailyCount() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val lastRequestDate = sharedPrefs.getLong(LAST_REQUEST_DATE_KEY, 0)
        val lastRequestCalendar = Calendar.getInstance().apply {
            timeInMillis = lastRequestDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // 날짜가 바뀌었으면 카운트 초기화
        if (today != lastRequestCalendar) {
            sharedPrefs.edit().apply {
                putInt(REQUEST_COUNT_KEY, 0)
                putLong(LAST_REQUEST_DATE_KEY, System.currentTimeMillis())
                apply()
            }
            Log.d(TAG, "Daily count reset - new day started")
        }
    }

    // 현재 요청 카운트 가져오기
    fun getCurrentRequestCount(): Int {
        checkAndResetDailyCount()
        return sharedPrefs.getInt(REQUEST_COUNT_KEY, 0)
    }

    // 요청 카운트 증가
    private fun incrementRequestCount() {
        checkAndResetDailyCount()
        val currentCount = sharedPrefs.getInt(REQUEST_COUNT_KEY, 0)
        sharedPrefs.edit().apply {
            putInt(REQUEST_COUNT_KEY, currentCount + 1)
            putLong(LAST_REQUEST_DATE_KEY, System.currentTimeMillis())
            apply()
        }
        Log.d(TAG, "Request count incremented to: ${currentCount + 1}")
    }

    // 채팅 가능 여부 확인
    fun canSendMessage(): Boolean {
        checkAndResetDailyCount()
        val currentCount = sharedPrefs.getInt(REQUEST_COUNT_KEY, 0)
        return currentCount < MAX_REQUESTS_PER_DAY
    }

    // 남은 채팅 횟수 가져오기
    fun getRemainingMessages(): Int {
        checkAndResetDailyCount()
        val currentCount = sharedPrefs.getInt(REQUEST_COUNT_KEY, 0)
        return MAX_REQUESTS_PER_DAY - currentCount
    }


    fun sendChatMessageToWorkflow(userId: String, message: String, sessionId: String, userCity: String = "", userDistrict: String = "") {
        // 이력서 진행 중인 경우 사용자 입력 저장
        if (isResumeInProgress && currentResumeStep.isNotEmpty()) {
            saveResumeStep(currentResumeStep, message)
        }

        // 채팅 횟수 확인
        if (!canSendMessage()) {
            Log.d(TAG, "Daily chat limit reached")
            Handler(Looper.getMainLooper()).post {
                responseCallback?.invoke("오늘 채팅 갯수 도달")
            }
            return
        }

        // 현재 질문을 저장
        lastQueryContent = message

        Log.d(TAG, "==== STARTING NEW CHAT REQUEST ====")
        Log.d(TAG, "Request to URL: $url")
        Log.d(TAG, "message: $message")
        Log.d(TAG, "userCity: $userCity")
        Log.d(TAG, "userDistrict: $userDistrict")
        Log.d(TAG, "userId: $userId")

        // ✅ MainActivity에서 kakao_id 정보 가져오기
        val mainActivity = context as? MainActivity
        val kakaoId = mainActivity?.currentUserKakaoId

        // userId 분석 및 실제 사용자 ID 추출
        val (actualKakaoId, actualUserId) = if (userId.startsWith("kakao_")) {
            // 카카오 사용자인 경우
            val extractedKakaoId = userId.removePrefix("kakao_")
            Pair(extractedKakaoId, null)
        } else {
            // 일반 사용자인 경우
            Pair(null, userId)
        }

        Log.d(TAG, "Extracted - kakaoId: $actualKakaoId, userId: $actualUserId")

        // ✅ JSON 객체에 kakao_id와 user_id 구분해서 추가
        val json = JSONObject().apply {
            put("query", message)
            put("userCity", userCity)
            put("userDistrict", userDistrict)

            // kakao_id와 user_id 구분해서 전송
            if (actualKakaoId != null) {
                put("kakao_id", actualKakaoId)
                put("user_id", JSONObject.NULL)  // null 값 명시적 전송
            } else {
                put("kakao_id", JSONObject.NULL)  // null 값 명시적 전송
                put("user_id", actualUserId)
            }

            // 세션 ID도 추가
            put("session_id", sessionId)
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

        // 요청 카운트 증가
        incrementRequestCount()

        // 새로운 검색 시작 시 초기화 - 탐색 모드 완전히 해제
        currentResults = null
        currentIndex = 0
        isExploreMode = false
        isCurrentlyExploring = false  // 중요: 탐색 모드 플래그도 초기화
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

                    // 응답 파싱 및 SearchHistory용 데이터 추출
                    var categoryForHistory: String? = null
                    var answerForHistory: String? = null
                    var queryCategory: String? = null

                    try {
                        val jsonResponse = JSONObject(responseBody)

                        // namespace와 Query_Category를 각각 별도로 처리
                        categoryForHistory = jsonResponse.optString("namespace", null)
                        queryCategory = jsonResponse.optString("Query_Category", null)

                        Log.d(TAG, "Query_Category: ${queryCategory ?: "없음"}")
                        Log.d(TAG, "namespace: ${categoryForHistory ?: "없음"}")

                        val namespace = jsonResponse.optString("namespace", "")

                        // 🔥 resume_builder namespace 특별 처리
                        if (namespace == "resume_builder") {
                            Log.d(TAG, "Resume builder namespace detected")

                            val resumeAction = jsonResponse.optString("resume_action", "")
                            val resumeStep = jsonResponse.optString("resume_step", "")
                            val resumeContent = jsonResponse.optString("resume_content", "")

                            Log.d(TAG, "Resume action: $resumeAction")
                            Log.d(TAG, "Resume step: $resumeStep")
                            Log.d(TAG, "Resume content length: ${resumeContent.length}")

                            if (jsonResponse.has("results")) {
                                val results = jsonResponse.getJSONArray("results")

                                if (results.length() > 0) {
                                    val firstResult = results.getJSONObject(0)
                                    val content = firstResult.optString("content", "")

                                    when (resumeAction) {
                                        "started" -> {
                                            Log.d(TAG, "Resume creation started")
                                            initializeResumeData()
                                            isResumeInProgress = true
                                            currentResumeStep = resumeStep

                                            Handler(Looper.getMainLooper()).post {
                                                responseCallback?.invoke(content)
                                                navigationCallback?.invoke(false, false, 1, 1)
                                            }
                                        }
                                        "completed" -> {
                                            Log.d(TAG, "Resume creation completed")
                                            isResumeInProgress = false
                                            currentResumeStep = ""

                                            // 완성된 이력서 생성
                                            val formattedResume = generateFormattedResume()

                                            // 완료 메시지 먼저 표시
                                            Handler(Looper.getMainLooper()).post {
                                                responseCallback?.invoke(content)
                                                navigationCallback?.invoke(false, false, 1, 1)
                                            }

                                            // 1초 후 완성된 이력서 표시
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                responseCallback?.invoke(formattedResume)

                                                // 🔥 완성된 이력서를 서버로 전송
                                                sendResumeToServer(formattedResume, userId)

                                            }, 1000)
                                        }
                                        "next_question" -> {
                                            Log.d(TAG, "Resume next question")
                                            currentResumeStep = resumeStep

                                            Handler(Looper.getMainLooper()).post {
                                                responseCallback?.invoke(content)
                                                navigationCallback?.invoke(false, false, 1, 1)
                                            }
                                        }
                                        else -> {
                                            Log.d(TAG, "Resume default action")
                                            currentResumeStep = resumeStep

                                            Handler(Looper.getMainLooper()).post {
                                                responseCallback?.invoke(content)
                                                navigationCallback?.invoke(false, false, 1, 1)
                                            }
                                        }
                                    }

                                    // SearchHistory용 데이터 설정
                                    answerForHistory = if (content.length > 100) {
                                        content.substring(0, 100)
                                    } else {
                                        content
                                    }

                                    // SearchHistory에 저장할 데이터를 ChatService에 저장
                                    lastSearchCategory = categoryForHistory
                                    lastSearchAnswer = answerForHistory

                                    return
                                }
                            }
                        }

                        // 일반 Pinecone 응답 처리 (기존 코드 그대로)
                        if (jsonResponse.has("results")) {
                            val results = jsonResponse.getJSONArray("results")

                            if (results.length() > 0) {
                                // Query_Category가 있는 경우 특별 처리
                                if (!queryCategory.isNullOrEmpty()) {
                                    Log.d(TAG, "Query_Category 감지: $queryCategory")

                                    // 체육시설 소득공제인 경우 특별 마커 추가
                                    if (queryCategory == "체육시설 소득공제") {
                                        // 첫 번째 결과에 Query_Category 정보 추가
                                        val firstResult = results.getJSONObject(0)
                                        firstResult.put("query_category", queryCategory)
                                        Log.d(TAG, "체육시설 소득공제 마커 추가")
                                    }
                                }

                                // workout namespace인 경우 results에 namespace 정보 추가
                                if (namespace == "workout") {
                                    for (i in 0 until results.length()) {
                                        val result = results.getJSONObject(i)
                                        result.put("namespace", namespace)
                                    }
                                }

                                // 첫 번째 결과의 content에서 answer 추출
                                val firstResult = results.getJSONObject(0)
                                val content = firstResult.optString("content", "")

                                // content의 앞 100자만 추출
                                answerForHistory = if (content.length > 100) {
                                    content.substring(0, 100)
                                } else {
                                    content
                                }

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

                        // SearchHistory에 저장할 데이터를 ChatService에 저장
                        lastSearchCategory = categoryForHistory
                        lastSearchAnswer = answerForHistory

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

            private fun formatResumeContent(resumeContent: String): String {
                return try {
                    Log.d(TAG, "Original resume content: $resumeContent")

                    // 간단한 템플릿 방식으로 변경
                    // 서버에서 받은 복잡한 마크다운 대신 기본 템플릿 사용
                    val formattedResume = StringBuilder()
                    formattedResume.append("📋 완성된 이력서\n\n")

                    // (1) 인적 사항
                    formattedResume.append("(1) 인적 사항\n")
                    formattedResume.append("1. 이름: 미입력\n")
                    formattedResume.append("2. 성별: 미입력\n")
                    formattedResume.append("3. 생년월일: 미입력\n")
                    formattedResume.append("4. 주소: 미입력\n")
                    formattedResume.append("5. 연락처: 미입력\n\n")

                    // (2) 최종 학력
                    formattedResume.append("(2) 최종 학력\n")
                    formattedResume.append("1. 학교명: 미입력\n")
                    formattedResume.append("2. 전공명: 미입력\n\n")

                    // (3) 경력 사항
                    formattedResume.append("(3) 경력 사항\n")
                    formattedResume.append("1. 회사명: 미입력\n")
                    formattedResume.append("2. 근무기간: 미입력\n")
                    formattedResume.append("3. 담당 업무: 미입력\n\n")

                    // (4) 보유 역량
                    formattedResume.append("(4) 보유 역량\n")
                    formattedResume.append("1. 자격증: 미입력\n")
                    formattedResume.append("2. 운전: 미입력\n")
                    formattedResume.append("3. 차량 소유: 미입력\n")
                    formattedResume.append("4. 장점: 미입력\n")
                    formattedResume.append("5. 단점: 미입력\n")

                    val result = formattedResume.toString().trim()
                    Log.d(TAG, "Formatted resume: $result")

                    return result

                } catch (e: Exception) {
                    Log.e(TAG, "Error formatting resume content", e)
                    "📋 이력서 작성이 완료되었습니다.\n\n$resumeContent"
                }
            }

        })
    }

    // 탐색 결과 설정 - ChatMessage 타입으로 변경
    fun setSearchResults(results: List<ChatMessage>) {
        exploreResults = results
        currentIndex = 0
        isExploreMode = true
        isCurrentlyExploring = true  // 탐색 모드 플래그 설정
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

    private fun showCurrentResult() {
        // 탐색 모드인 경우
        if (isExploreMode && exploreResults.isNotEmpty()) {
            if (currentIndex >= 0 && currentIndex < exploreResults.size) {
                val message = exploreResults[currentIndex]

                Handler(Looper.getMainLooper()).post {
                    // 탐색 모드일 때는 exploreResponseCallback을 사용
                    if (isCurrentlyExploring && exploreResponseCallback != null) {
                        exploreResponseCallback?.invoke(message.text)
                    } else {
                        responseCallback?.invoke(message.text)
                    }

                    // 네비게이션 상태 업데이트
                    val hasPrevious = currentIndex > 0
                    val hasNext = currentIndex < exploreResults.size - 1
                    val currentPage = currentIndex + 1
                    val totalPages = exploreResults.size

                    navigationCallback?.invoke(hasPrevious, hasNext, currentPage, totalPages)

                    // 상태 저장
                    savedNavigationState = NavigationState(hasPrevious, hasNext, currentPage, totalPages)
                }
            }
            return
        }

        // 일반 검색 모드
        currentResults?.let { results ->
            if (currentIndex >= 0 && currentIndex < results.length()) {
                try {
                    val currentResult = results.getJSONObject(currentIndex)

                    // namespace 확인
                    val namespace = currentResult.optString("namespace", "")

                    // 🔥 resume_builder namespace 특별 처리
                    if (namespace == "resume_builder") {
                        Log.d(TAG, "Resume builder namespace detected")

                        // 전체 응답에서 resume_action 확인
                        val parentResponse = currentResults?.toString()
                        var resumeAction: String? = null
                        var resumeContent: String? = null

                        try {
                            // currentResults는 JSONArray이므로 전체 응답을 다시 파싱해야 함
                            // 이를 위해 원본 응답을 저장하거나 다른 방법 사용

                            // 일단 currentResult에서 확인 (개별 result 객체에는 resume_action이 없을 수 있음)
                            val content = currentResult.optString("content", "")

                            // 로그에서 보면 전체 응답 구조를 확인할 수 있음
                            Log.d(TAG, "Resume result content: $content")

                            // 이력서 작성 완료 메시지 감지
                            // 이력서 작성 완료 메시지 감지
                            if (content.contains("이력서가 성공적으로 생성되었습니다") ||
                                content.contains("이력서 생성 완료") ||
                                content.contains("이력서 작성이 완료되었습니다")) {

                                Log.d(TAG, "Resume completion detected in content")

                                // 완성된 이력서 생성
                                val formattedResume = generateFormattedResume()

                                // 이력서 완료 메시지 표시
                                Handler(Looper.getMainLooper()).post {
                                    responseCallback?.invoke(content)

                                    // 네비게이션 상태 업데이트
                                    val hasPrevious = currentIndex > 0
                                    val hasNext = currentIndex < results.length() - 1
                                    val currentPage = currentIndex + 1
                                    val totalPages = results.length()

                                    navigationCallback?.invoke(hasPrevious, hasNext, currentPage, totalPages)
                                    savedNavigationState = NavigationState(hasPrevious, hasNext, currentPage, totalPages)
                                }

                                // 1초 후 완성된 이력서 표시 및 서버 전송
                                Handler(Looper.getMainLooper()).postDelayed({
                                    responseCallback?.invoke(formattedResume)

                                    // 🔥 완성된 이력서를 서버로 전송
                                    // userId는 실제 사용자 ID로 변경하세요
                                    sendResumeToServer(formattedResume, "default_user") // 또는 실제 userId 파라미터 사용

                                }, 1000)

                            } else {
                                // 일반 이력서 작성 과정 메시지
                                Handler(Looper.getMainLooper()).post {
                                    responseCallback?.invoke(content)

                                    // 네비게이션 상태 업데이트
                                    val hasPrevious = currentIndex > 0
                                    val hasNext = currentIndex < results.length() - 1
                                    val currentPage = currentIndex + 1
                                    val totalPages = results.length()

                                    navigationCallback?.invoke(hasPrevious, hasNext, currentPage, totalPages)
                                    savedNavigationState = NavigationState(hasPrevious, hasNext, currentPage, totalPages)
                                }
                            }

                            return

                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing resume builder response", e)
                            // 오류 발생 시 일반 처리로 fallback
                        }
                    }

                    // workout namespace인 경우 특별 처리
                    if (namespace == "workout" || currentResult.has("thumbnail_url")) {
                        // workout 전용 포맷팅
                        val title = currentResult.optString("title", "제목 없음")
                        val thumbnailUrl = currentResult.optString("thumbnail_url", "")
                        val videoUrl = currentResult.optString("url", "")

                        // workout 결과 형식으로 포맷팅
                        val formattedContent = StringBuilder()
                        formattedContent.append("📋 $title")

                        if (thumbnailUrl.isNotEmpty() && videoUrl.isNotEmpty()) {
                            formattedContent.append("\n\n[THUMBNAIL_URL]$thumbnailUrl[/THUMBNAIL_URL]")
                            formattedContent.append("\n[YOUTUBE_URL]$videoUrl[/YOUTUBE_URL]")
                        }

                        Log.d(TAG, "Showing workout result: $formattedContent")

                        Handler(Looper.getMainLooper()).post {
                            responseCallback?.invoke(formattedContent.toString())

                            // 네비게이션 상태 업데이트
                            val hasPrevious = currentIndex > 0
                            val hasNext = currentIndex < results.length() - 1
                            val currentPage = currentIndex + 1
                            val totalPages = results.length()

                            navigationCallback?.invoke(hasPrevious, hasNext, currentPage, totalPages)
                            savedNavigationState = NavigationState(hasPrevious, hasNext, currentPage, totalPages)
                        }
                    } else {
                        // 기존 로직 (일반 content 처리)
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
                            savedNavigationState = NavigationState(hasPrevious, hasNext, currentPage, totalPages)
                        }
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

    private fun formatResponse(content: String): String {
        var formatted = content

        // "Showing result X:" 부분 제거
        val showingResultPattern = Regex("^Showing result \\d+:\\s*", RegexOption.IGNORE_CASE)
        formatted = formatted.replace(showingResultPattern, "")

        // "id: XXX" 부분 제거 (줄바꿈 포함)
        val idPattern = Regex("id:\\s*\\d+\\s*[\n\r]*", RegexOption.IGNORE_CASE)
        formatted = formatted.replace(idPattern, "")

        // "Id: XXX" 부분도 제거 (대문자 I)
        val idPatternCapital = Regex("Id:\\s*\\d+\\s*[\n\r]*", RegexOption.IGNORE_CASE)
        formatted = formatted.replace(idPatternCapital, "")

        // | 를 줄바꿈으로 변경
        formatted = formatted.replace(" | ", "\n")
        formatted = formatted.replace("|", "\n")

        // 연속된 줄바꿈을 하나로 통합
        formatted = formatted.replace(Regex("\n+"), "\n")

        // Title 정보 추출 (대소문자 구분 없이)
        var title: String? = null
        val titlePattern = Regex("Title:\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
        val titleMatch = titlePattern.find(formatted)
        if (titleMatch != null) {
            title = titleMatch.groupValues[1].trim()
            // 원본에서 Title 라인 제거
            formatted = formatted.replace(titleMatch.value, "")
        }

        // Category 정보 추출 후 제거 - 채팅화면에서만 제거
        var category: String? = null
        val categoryPattern = Regex("^Category:\\s*([^\\n]+)", RegexOption.MULTILINE)
        val categoryMatch = categoryPattern.find(formatted)
        if (categoryMatch != null) {
            category = categoryMatch.groupValues[1].trim()
            // Category 라인 전체 제거
            formatted = formatted.replace(categoryMatch.value, "")
        }

        // "Job" 텍스트 단독으로 있는 경우 제거
        val jobPattern = Regex("\\n*Job\\s*\\n+", RegexOption.IGNORE_CASE)
        formatted = formatted.replace(jobPattern, "\n")

        // YouTube URL 추출 및 처리
        var youtubeUrl: String? = null
        var thumbnailUrl: String? = null

        // YouTube URL 패턴들
        val youtubePatterns = listOf(
            Regex("(?:https?://)?(?:www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})", RegexOption.IGNORE_CASE),
            Regex("(?:https?://)?(?:www\\.)?youtu\\.be/([a-zA-Z0-9_-]{11})", RegexOption.IGNORE_CASE),
            Regex("(?:https?://)?(?:www\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})", RegexOption.IGNORE_CASE),
            Regex("YouTube:\\s*(https?://[^\\n\\s]+)", RegexOption.IGNORE_CASE),
            Regex("youtube:\\s*(https?://[^\\n\\s]+)", RegexOption.IGNORE_CASE),
            Regex("Video:\\s*(https?://[^\\n\\s]+youtube[^\\n\\s]+)", RegexOption.IGNORE_CASE)
        )

        // YouTube URL 찾기
        for (pattern in youtubePatterns) {
            val match = pattern.find(formatted)
            if (match != null) {
                // 전체 URL 추출
                youtubeUrl = when {
                    match.groupValues.size > 1 && match.groupValues[1].startsWith("http") -> {
                        match.groupValues[1]
                    }
                    match.groupValues.size > 1 -> {
                        // Video ID만 있는 경우
                        "https://www.youtube.com/watch?v=${match.groupValues[1]}"
                    }
                    else -> {
                        match.value
                    }
                }

                // Video ID 추출하여 썸네일 URL 생성
                val videoIdMatch = Regex("(?:v=|/)([a-zA-Z0-9_-]{11})").find(youtubeUrl)
                if (videoIdMatch != null) {
                    val videoId = videoIdMatch.groupValues[1]
                    // 항상 최고화질 썸네일부터 시도
                    thumbnailUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
                }

                // 원본에서 YouTube URL 라인 제거
                formatted = formatted.replace(match.value, "")
                break
            }
        }

        // Detail URL 추출 (YouTube가 아닌 경우)
        var detailUrl: String? = null
        if (youtubeUrl == null) {
            val detailPatterns = listOf(
                Regex("Detail:\\s*([^\\n]+)", RegexOption.IGNORE_CASE),
                Regex("detail:\\s*([^\\n]+)", RegexOption.IGNORE_CASE),
                Regex("🔗 상세정보:\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
            )

            for (pattern in detailPatterns) {
                val match = pattern.find(formatted)
                if (match != null) {
                    detailUrl = match.groupValues[1].trim()
                    formatted = formatted.replace(match.value, "")
                    break
                }
            }
        }

        // 시작과 끝 공백 제거
        formatted = formatted.trim()

        // 각 라인의 앞뒤 공백 제거
        formatted = formatted.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

        // 특정 패턴들을 더 보기 좋게 포맷팅
        formatted = formatted
            .replace("DateOfRegistration:", "📅 등록일:")
            .replace("Deadline:", "⏰ 마감일:")
            .replace("JobCategory:", "💼 직종:")
            .replace("ExperienceRequired:", "📊 경력:")
            .replace("EmploymentType:", "📋 고용형태:")
            .replace("Salary:", "💰 급여:")
            .replace("SocialEnsurance:", "🛡 사회보험:")
            .replace("RetirementBenefit:", "💼 퇴직혜택:")
            .replace("Location:", "📍 주소:")
            .replace("WorkingHours:", "⏰ 근무시간:")
            .replace("WorkingType:", "💼 근무형태:")
            .replace("CompanyName:", "🏢 회사명:")
            .replace("JobDescription:", "📝 상세설명:")
            .replace("ApplicationMethod:", "📝 지원방법:")
            .replace("ApplicationType:", "📋 전형방법:")
            .replace("document:", "📄 제출서류:")
            .replace("Document:", "📄 제출서류:")
            .replace("Institution:", "🏛️ 기관:")
            .replace("Address:", "📍 주소:")
            .replace("Recruitment_period:", "📅 모집기간:")
            .replace("Education_period:", "📚 교육기간:")
            .replace("Fees:", "💰 수강료:")
            .replace("Fee:", "💰 수강료:")
            .replace("Quota:", "👥 정원:")
            .replace("Service1:", "🏥 서비스1:")
            .replace("Service2:", "🏥 서비스2:")
            .replace("Rating:", "⭐ 등급:")
            .replace("Full:", "📊 정원:")
            .replace("Now:", "✅ 현원:")
            .replace("Wating:", "⏳ 대기:")
            .replace("Bus:", "🚌 방문목욕차량:")
            .replace("Tel:", "📞 전화:")
            .replace("Date:", "📅 교육일시:")
            .replace("State:", "📋 상태:")
            .replace("Registration:", "📝 등록방법:")

        // WorkingHours 특별 처리 - 이모지 변환 후에 처리
        // "주 소정근로시간"이 여러 줄에 걸쳐 있는 경우를 처리
        if (formatted.contains("주 소정근로시간")) {
            // 전체 텍스트를 섹션별로 분리하여 처리
            val sections = formatted.split(Regex("(?=⏰|📍|💼|🏢|📝|📋|📄|⭐|🚌|📞|📅|📊|✅|⏳|🛡|🏥)"))
            val processedSections = sections.map { section ->
                if (section.startsWith("⏰ 근무시간:") && section.contains("주 소정근로시간")) {
                    // "주 소정근로시간" 이전까지만 유지
                    val sojeongIndex = section.indexOf("주 소정근로시간")
                    var workingHours = section.substring(0, sojeongIndex).trim()

                    // "(근무시간)" 텍스트 제거
                    workingHours = workingHours.replace("(근무시간)", "")

                    // "※ 상세 근무시간" 텍스트 제거
                    workingHours = workingHours.replace("※ 상세 근무시간", "")

                    // 여러 공백 정리 (줄바꿈은 유지)
                    workingHours = workingHours.replace(Regex(" {2,}"), " ")
                    workingHours = workingHours.trim()

                    workingHours
                } else {
                    section
                }
            }

            formatted = processedSections.filter { it.isNotEmpty() }.joinToString("\n\n")
        }

        // 추가로 단독으로 있는 불필요한 텍스트 제거
        formatted = formatted.replace("(근무시간)", "")
        formatted = formatted.replace("※ 상세 근무시간", "")

        // Title을 맨 앞에 추가
        val result = StringBuilder()

        if (!title.isNullOrEmpty()) {
            result.append("📋 $title\n")
            result.append("\n")
        }

        result.append(formatted)

        // 중복된 줄바꿈 다시 한 번 정리
        var finalResult = result.toString().replace(Regex("\n{3,}"), "\n\n")

        // 최종적으로 시작 부분의 공백이나 줄바꿈 제거
        finalResult = finalResult.trim()

        // YouTube URL과 썸네일 정보를 특별한 마커로 저장
        if (!youtubeUrl.isNullOrEmpty() && !thumbnailUrl.isNullOrEmpty()) {
            finalResult += "\n\n[THUMBNAIL_URL]$thumbnailUrl[/THUMBNAIL_URL]"
            finalResult += "\n[YOUTUBE_URL]$youtubeUrl[/YOUTUBE_URL]"
            Log.d("ChatService", "formatResponse - YouTube URL found: $youtubeUrl")
            Log.d("ChatService", "formatResponse - Thumbnail URL: $thumbnailUrl")
        } else if (!detailUrl.isNullOrEmpty()) {
            // YouTube가 아닌 경우에만 Detail URL 추가
            finalResult += "\n\n[DETAIL_URL]$detailUrl[/DETAIL_URL]"
            Log.d("ChatService", "formatResponse - Detail URL found: $detailUrl")
        }

        return finalResult
    }

    private var isNavigatingResults = false

    fun isNavigating(): Boolean = isNavigatingResults
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

    // 검색 결과 초기화 메서드 수정
    fun clearResults() {
        currentResults = null
        currentIndex = 0
        isExploreMode = false
        isCurrentlyExploring = false
        exploreResults = emptyList()
        savedNavigationState = null  // 상태도 초기화
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