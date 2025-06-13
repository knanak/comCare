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

// ChatService.kt의 sendChatMessageToWorkflow 함수 전체 수정

    fun sendChatMessageToWorkflow(userId: String, message: String, sessionId: String, userCity: String = "", userDistrict: String = "") {
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

        val json = JSONObject().apply {
            put("query", message)
            put("userCity", userCity)
            put("userDistrict", userDistrict)
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

                    // Pinecone 응답 처리 로직
                    try {
                        val jsonResponse = JSONObject(responseBody)

                        // namespace 추출 (category용)
                        categoryForHistory = jsonResponse.optString("namespace", null)

                        if (jsonResponse.has("results")) {
                            val results = jsonResponse.getJSONArray("results")

                            if (results.length() > 0) {
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
                        // MainActivity에서 접근할 수 있도록 함
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

    // 현재 인덱스의 결과를 표시하는 함수
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

        // 일반 검색 모드 (기존 코드 유지)
        currentResults?.let { results ->
            if (currentIndex >= 0 && currentIndex < results.length()) {
                try {
                    val currentResult = results.getJSONObject(currentIndex)
                    var content = currentResult.optString("content", "내용 없음")

                    // 응답 포맷팅: | 를 줄바꿈으로 변경하고 가독성 개선
                    content = formatResponse(content)

                    Log.d(TAG, "Showing result $currentIndex: $content")

                    Handler(Looper.getMainLooper()).post {
                        // 일반 검색 모드에서는 항상 responseCallback 사용
                        responseCallback?.invoke(content)

                        // 네비게이션 상태 업데이트
                        val hasPrevious = currentIndex > 0
                        val hasNext = currentIndex < results.length() - 1
                        val currentPage = currentIndex + 1
                        val totalPages = results.length()

                        navigationCallback?.invoke(hasPrevious, hasNext, currentPage, totalPages)

                        // 상태 저장
                        savedNavigationState = NavigationState(hasPrevious, hasNext, currentPage, totalPages)
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
// ChatService.kt의 formatResponse 함수 전체

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

        // Detail URL 추출
        var detailUrl: String? = null
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

        // Detail URL 정보를 특별한 마커로 저장
        if (!detailUrl.isNullOrEmpty()) {
            finalResult += "\n\n[DETAIL_URL]$detailUrl[/DETAIL_URL]"
        }

        Log.d("ChatService", "formatResponse - Detail URL found: $detailUrl")

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