package com.example.comcare

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.providers.Kakao
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.gotrue.gotrue


class SupabaseDatabaseHelper(private val context: Context) {

    companion object {
        private const val SUPABASE_URL = "https://ptztivxympkpwiwdlcit.supabase.co"
        private const val TAG = "SupabaseHelper"
    }

    // Create Supabase client
    private val supabase = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Postgrest)
        install(GoTrue)

    }

    sealed class SignUpResult {
        object Success : SignUpResult()
        object UserExists : SignUpResult()
        data class Error(val message: String) : SignUpResult()
    }

    // 사용자 데이터 클래스 (일반 로그인과 카카오 로그인 통합)
    @Serializable
    data class User(
        val userId: String,              // 고유 사용자 ID (일반: 직접입력, 카카오: kakao_카카오ID)
        val password: String? = null,    // 비밀번호 (카카오 로그인은 null)
        val kakaoId: String? = null,     // 카카오 고유 ID
        val email: String? = null,       // 이메일
        val nickname: String? = null,    // 닉네임
        val profileImage: String? = null,// 프로필 이미지 URL
        val age: String = "미설정",      // 연령대
        val gender: String = "미설정",   // 성별
        val loginType: String = "normal",// 로그인 타입 (normal, kakao, oauth_kakao)
        val supabaseUserId: String? = null, // Supabase Auth ID (OAuth 사용시)
        val createdAt: String? = null
    )

    // Helper function to get ISO 8601 formatted timestamp
    private fun getCurrentTimestampIso8601(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    // ========== 카카오 SDK 로그인 (기존 방식) ==========

    // 카카오 SDK를 통한 로그인/회원가입
    suspend fun loginOrCreateKakaoUser(
        kakaoId: String,
        email: String?,
        nickname: String?,
        profileImage: String?
    ): Boolean {
        return try {
            Log.d(TAG, "Starting Kakao SDK login/create for kakaoId: $kakaoId")

            withContext(Dispatchers.IO) {
                try {
                    val userId = "kakao_$kakaoId"

                    // 기존 사용자 확인
                    val existingUsers = supabase.postgrest["users"]
                        .select(
                            filter = {
                                eq("userId", userId)
                            }
                        )
                        .decodeList<User>()

                    val existingUser = existingUsers.firstOrNull()

                    if (existingUser != null) {
                        Log.d(TAG, "Existing Kakao user found, updating info")

                        // 기존 사용자 정보 업데이트
                        val updateData = buildMap {
                            email?.let { put("email", it) }
                            nickname?.let { put("nickname", it) }
                            profileImage?.let { put("profileImage", it) }
                        }

                        if (updateData.isNotEmpty()) {
                            supabase.postgrest["users"]
                                .update(updateData) {
                                    eq("userId", userId)
                                }
                            Log.d(TAG, "Kakao user info updated successfully")
                        }
                        true
                    } else {
                        Log.d(TAG, "No existing Kakao user found, creating new user")

                        // 새 사용자 생성
                        val newUser = User(
                            userId = userId,
                            password = null,
                            kakaoId = kakaoId,
                            email = email,
                            nickname = nickname,
                            profileImage = profileImage,
                            age = "미설정",
                            gender = "미설정",
                            loginType = "kakao",
                            createdAt = getCurrentTimestampIso8601()
                        )

                        supabase.postgrest["users"]
                            .insert(newUser)

                        Log.d(TAG, "New Kakao user created successfully")
                        true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in Kakao SDK login/create: ${e.message}", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loginOrCreateKakaoUser: ${e.message}", e)
            false
        }
    }

    // ========== Supabase OAuth 카카오 로그인 ==========

    // Supabase OAuth를 통한 카카오 로그인
    suspend fun signInWithKakao(): Boolean {
        return try {
            Log.d(TAG, "Starting OAuth Kakao login")

            withContext(Dispatchers.IO) {
                try {
                    // Supabase OAuth로 카카오 로그인
                    supabase.gotrue.loginWith(Kakao)

                    // 로그인 성공 후 세션 확인
                    val session = supabase.gotrue.currentSessionOrNull()
                    if (session != null) {
                        Log.d(TAG, "Kakao OAuth login successful")

                        // 현재 사용자 정보 가져오기
                        val user = supabase.gotrue.currentUserOrNull()
                        if (user != null) {
                            Log.d(TAG, "OAuth User ID: ${user.id}")
                            Log.d(TAG, "OAuth User email: ${user.email}")

                            // 사용자 메타데이터에서 추가 정보 가져오기
                            val metadata = user.userMetadata
                            val kakaoId = metadata?.get("sub") as? String ?: user.id
                            val nickname = metadata?.get("name") as? String
                            val profileImage = metadata?.get("picture") as? String

                            // users 테이블에 사용자 정보 저장/업데이트
                            saveOrUpdateOAuthUser(
                                supabaseUserId = user.id,
                                kakaoId = kakaoId,
                                email = user.email,
                                nickname = nickname,
                                profileImage = profileImage
                            )
                        }
                        true
                    } else {
                        Log.e(TAG, "No session after Kakao OAuth login")
                        false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in Kakao OAuth login: ${e.message}", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in signInWithKakao: ${e.message}", e)
            false
        }
    }

    // OAuth 사용자 정보를 users 테이블에 저장/업데이트
    private suspend fun saveOrUpdateOAuthUser(
        supabaseUserId: String,
        kakaoId: String,
        email: String?,
        nickname: String?,
        profileImage: String?
    ): Boolean {
        return try {
            val userId = "kakao_$kakaoId"

            // 기존 사용자 확인
            val existingUsers = supabase.postgrest["users"]
                .select(
                    filter = {
                        eq("userId", userId)
                    }
                )
                .decodeList<User>()

            val existingUser = existingUsers.firstOrNull()

            if (existingUser != null) {
                // 기존 사용자 정보 업데이트
                val updateData = buildMap {
                    email?.let { put("email", it) }
                    nickname?.let { put("nickname", it) }
                    profileImage?.let { put("profileImage", it) }
                    put("supabaseUserId", supabaseUserId)
                    put("loginType", "oauth_kakao")
                }

                supabase.postgrest["users"]
                    .update(updateData) {
                        eq("userId", userId)
                    }
            } else {
                // 새 사용자 생성
                val newUser = User(
                    userId = userId,
                    password = null,
                    kakaoId = kakaoId,
                    email = email,
                    nickname = nickname,
                    profileImage = profileImage,
                    age = "미설정",
                    gender = "미설정",
                    loginType = "oauth_kakao",
                    supabaseUserId = supabaseUserId,
                    createdAt = getCurrentTimestampIso8601()
                )

                supabase.postgrest["users"]
                    .insert(newUser)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving OAuth user: ${e.message}", e)
            false
        }
    }

    // ========== 공통 사용자 관리 함수들 ==========

    // 카카오 ID로 사용자 정보 조회
    suspend fun getKakaoUserInfo(kakaoId: String): User? {
        return try {
            withContext(Dispatchers.IO) {
                val userId = "kakao_$kakaoId"

                val users = supabase.postgrest["users"]
                    .select(
                        filter = {
                            eq("userId", userId)
                        }
                    )
                    .decodeList<User>()

                users.firstOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Kakao user info: ${e.message}", e)
            null
        }
    }

    // 현재 로그인된 OAuth 사용자 정보 가져오기
    suspend fun getCurrentOAuthUser(): User? {
        return try {
            withContext(Dispatchers.IO) {
                val currentUser = supabase.gotrue.currentUserOrNull()
                if (currentUser != null) {
                    // supabaseUserId로 사용자 조회
                    val users = supabase.postgrest["users"]
                        .select(
                            filter = {
                                eq("supabaseUserId", currentUser.id)
                            }
                        )
                        .decodeList<User>()

                    users.firstOrNull()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current OAuth user: ${e.message}", e)
            null
        }
    }

    // 카카오 사용자 추가 정보 업데이트
    suspend fun updateKakaoUserAdditionalInfo(
        kakaoId: String,
        age: String? = null,
        gender: String? = null
    ): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val userId = "kakao_$kakaoId"

                val updateData = buildMap {
                    age?.let { put("age", it) }
                    gender?.let { put("gender", it) }
                }

                if (updateData.isNotEmpty()) {
                    supabase.postgrest["users"]
                        .update(updateData) {
                            eq("userId", userId)
                        }
                    true
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating additional info: ${e.message}", e)
            false
        }
    }

    // OAuth 로그아웃
    suspend fun signOutKakao(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                supabase.gotrue.logout()
                Log.d(TAG, "Kakao OAuth sign out successful")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out: ${e.message}", e)
            false
        }
    }

    // 현재 OAuth 세션 확인
    suspend fun isKakaoSessionValid(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val session = supabase.gotrue.currentSessionOrNull()
                session != null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking session: ${e.message}", e)
            false
        }
    }

    // 일반 로그인 함수 (기존 사용자용)
    suspend fun loginUser(userId: String, password: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val users = supabase.postgrest["users"]
                    .select(
                        filter = {
                            eq("userId", userId)
                            eq("password", password)
                        }
                    )
                    .decodeList<User>()

                users.firstOrNull() != null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error: ${e.message}")
            false
        }
    }

    // 일반 회원가입 함수
    suspend fun signupUser(
        userId: String,
        password: String,
        age: String,
        gender: String
    ): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                // 이미 존재하는 사용자인지 확인
                val existingUsers = supabase.postgrest["users"]
                    .select(
                        filter = {
                            eq("userId", userId)
                        }
                    )
                    .decodeList<User>()

                if (existingUsers.isNotEmpty()) {
                    return@withContext false
                }

                // 새 사용자 생성
                val newUser = User(
                    userId = userId,
                    password = password,
                    kakaoId = null,
                    email = null,
                    nickname = null,
                    profileImage = null,
                    age = age,
                    gender = gender,
                    loginType = "normal",
                    createdAt = getCurrentTimestampIso8601()
                )

                supabase.postgrest["users"]
                    .insert(newUser)

                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signup error: ${e.message}")
            false
        }
    }

    // 1. facilities data
    @Serializable
    data class facilities(
        val id: Int,
        val name: String,
        val service1: String? = null,
        val service2: String,
        val rating: String = "",
        val rating_year: String = "",
        val full: String = "0",
        val now: String = "0",
        val wating: String = "0",
        val bus: String = "",
        val address: String,
        val tel: String,
    )

    // Helper function to get ISO 8601 formatted timestamp
//    private fun getCurrentTimestampIso8601(): String {
//        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
//        sdf.timeZone = TimeZone.getTimeZone("UTC")
//        return sdf.format(Date())
//    }

    suspend fun getFacilities(): List<facilities> {
        return try {
            Log.d("supabase", "Starting getFacilities")

            withContext(Dispatchers.IO) {
                // First get the total count
                val totalCount = supabase.postgrest["facilities"]
                    .select(head = true, count = Count.EXACT)
                    .count() ?: 0

                Log.d("supabase", "Total count of facilities: $totalCount")

                // Determine the batch size by making a test request
                val testBatch = supabase.postgrest["facilities"]
                    .select()
                    .decodeList<facilities>()

                // The batch size is whatever limit Supabase applied to our first request
                val batchSize = testBatch.size
                Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                // Now we know the batch size, fetch all records
                val allFacilities = mutableListOf<facilities>()
                // Add the first batch that we already retrieved
                allFacilities.addAll(testBatch)

                var currentStart = batchSize

                // Continue fetching until we have all records
                while (currentStart < totalCount) {
                    val currentEnd = currentStart + batchSize - 1
                    Log.d("supabase", "Fetching batch: $currentStart to $currentEnd")

                    // Fetch a batch using range
                    val batch = supabase.postgrest["facilities"]
                        .select(filter = {
                            range(from = currentStart.toLong(), to = currentEnd.toLong())
                        })
                        .decodeList<facilities>()

//                    Log.d("supabase", "Fetched batch size: ${batch.size}")
                    allFacilities.addAll(batch)

                    // If we got an empty batch or fewer items than requested, we might be done
                    if (batch.isEmpty() || batch.size < batchSize) {
                        break
                    }

                    // Move to next batch
                    currentStart += batchSize
                }

                Log.d("supabase", "Retrieved ${allFacilities.size} facilities out of $totalCount total")

                // Verify we got all records
                if (allFacilities.size < totalCount) {
                    Log.w("supabase", "Warning: Retrieved fewer records than expected (${allFacilities.size} vs $totalCount)")
                }

                allFacilities
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error fetching facilities: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific facility by ID
    suspend fun getFacilityById(facilityId: String): facilities? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("facilities")
                    .select(
                        filter = {
                            eq("id", facilityId)
                        }
                    )
                    .decodeSingleOrNull<facilities>()

                if (response != null) {
                    Log.d(TAG, "Retrieved facility with ID: $facilityId")
                } else {
                    Log.d(TAG, "No facility found with ID: $facilityId")
                }

                response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching facility by ID: ${e.message}")
            null
        }
    }

    fun SupabaseDatabaseHelper.facilities.toPlace(): Place {
        // Parse service1 to extract service2 if needed
        val originalService2 = this.service2
        val (newService1, newService2) = if (originalService2.contains("치매")) {
            // Find the index of "치매"
            val dementiaIndex = originalService2.indexOf("치매")

            // Split the string at that index
            var service1Part = originalService2.substring(0, dementiaIndex).trim()
            if (service1Part.endsWith("내")) {
                service1Part = service1Part.substring(0, service1Part.length - 1)
            }
            var service2Part = originalService2.substring(dementiaIndex).trim()


            Pair(service1Part, service2Part)

        } else {
            // If there's no "치매", use the original string for service1 and empty for service2
            Pair(originalService2, this.service2 ?: "")  // Use empty string if service2 is null
        }

        return Place(
            id = this.id.toString(),
            name = this.name,
            facilityCode = "", // No direct equivalent in Supabase model
            facilityKind = originalService2,  // 시설유형
            facilityKindDetail = "장기요양기관",  // 시설종류
            district = extractDistrict(this.address),
            address = this.address,
            tel = this.tel,
            zipCode = "",
            service1 = listOf("장기요양기관"),  // Using processed non-null value
            service2 = listOf(newService1),  // Using processed non-null value
            rating = this.rating,
            rating_year = this.rating_year,
            full = this.full,
            now = this.now,
            wating = this.wating,
            bus = this.bus
        )
    }

    /**
     * Helper function to extract district from address
     */
    private fun extractDistrict(address: String): String {
        val addressParts = address.split(" ")
        return if (addressParts.size >= 2) {
            // Try to get district part (usually second part of Korean address)
            val districtPart = addressParts[1]

            // Make sure it ends with "구" if it's a district
            if (districtPart.endsWith("구")) {
                districtPart
            } else if (districtPart.contains("구")) {
                // Extract just the district part if it contains "구" with other text
                val districtMatch = "(.+구)".toRegex().find(districtPart)
                districtMatch?.groupValues?.get(1) ?: districtPart
            } else {
                districtPart
            }
        } else {
            "" // Return empty string if address format is unexpected
        }
    }

    // 2. job data
    @Serializable
    data class Job(
        val Id: Int,
        val Title: String? = null,
        val DateOfRegistration: String? = null,
        val Deadline: String? = null,
        val JobCategory: String? = null,
        val ExperienceRequired: String? = null,
        val EmploymentType: String? = null,
        val Salary: String? = null,
        val SocialEnsurance: String? = null,
        val RetirementBenefit: String? = null,
        val Address: String? = null,
        val Category: String? = null,
        val WorkingHours: String? = null,
        val WorkingType: String? = null,
        val CompanyName: String? = null,
        val JobDescription: String? = null,
        val ApplicationMethod: String? = null,
        val ApplicationType: String? = null,
        val Document: String? = null,
        val Detail: String? = null
    )



    suspend fun getJobs(): List<Job> {
        return try {
            Log.d("supabase", "Starting getJobs")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["job"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of jobs: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No jobs found in the 'job' table")
                        return@withContext emptyList<Job>()
                    }

                    // Determine the batch size
                    val batchSize = 100
                    val allJobs = mutableListOf<Job>()

                    // Fetch in batches
                    var currentStart = 0
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching jobs batch: $currentStart to $currentEnd")

                        try {
                            val batch = supabase.postgrest["job"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<Job>()

                            Log.d("supabase", "Fetched batch of ${batch.size} jobs")

                            // Clean the WorkingHours field for each job before adding to the list
                            val cleanedBatch = batch.map { job ->
                                var cleanedHours = job.WorkingHours

                                // Skip processing if WorkingHours is null
                                if (cleanedHours != null) {
                                    // Remove "주 소정근로시간" if present
                                    if (cleanedHours.contains("주 소정근로시간")) {
                                        val parts = cleanedHours.split("주 소정근로시간")
                                        cleanedHours = if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                                            parts[0].trim()
                                        } else {
                                            // If no text before "주 소정근로시간", check after it
                                            if (parts.size > 1) parts[1].trim() else cleanedHours
                                        }
                                    }

                                    // Remove "(근무시간) " if present
                                    if (cleanedHours.contains("(근무시간) ")) {
                                        cleanedHours = cleanedHours.replace("(근무시간) ", "").trim()
                                    }

                                    // Remove "* 상세 근무시간" and everything after it if present
                                    if (cleanedHours.contains("* 상세 근무시간")) {
                                        val parts = cleanedHours.split("* 상세 근무시간")
                                        cleanedHours = parts[0].trim()
                                    }
                                }

                                // Create a copy of the job with the cleaned hours
                                job.copy(WorkingHours = cleanedHours)
                            }

                            allJobs.addAll(cleanedBatch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching jobs batch $currentStart-$currentEnd: ${e.message}", e)
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allJobs.size} jobs out of $totalCount total")

                    // Log a sample job for debugging
                    if (allJobs.isNotEmpty()) {
                        val sample = allJobs.first()
                        Log.d("supabase", "Sample job: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.JobCategory}, " +
                                "hours=${sample.WorkingHours}, " +
                                "deadline=${sample.Deadline}")
                    }

                    allJobs
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getJobs inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getJobs: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // 3. Lecture data
    @Serializable
    data class Lecture(
        val Id: Int,
        val Category: String? = null,
        val Institution: String? = null,
        val Address : String? = null,
        val Title: String? = null,
        val Recruitment_period: String? = null,
        val Education_period: String? = null,
        val Fee: String? = null,
        val Quota: String? = null,
        val Detail: String? = null,
        val Tel: String? = null
    )

    suspend fun getLectures(): List<Lecture> {
        return try {
            Log.d("supabase", "Starting getLectures")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["lecture"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of lectures: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No lectures found in the 'lecture' table")
                        return@withContext emptyList<Lecture>()
                    }

                    // Determine the batch size
                    val batchSize = 100
                    val allLectures = mutableListOf<Lecture>()

                    // Fetch in batches
                    var currentStart = 0
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching lectures batch: $currentStart to $currentEnd")

                        try {
                            // Configure a custom JSON instance that ignores unknown keys
                            val batch = supabase.postgrest["lecture"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<Lecture>()

                            Log.d("supabase", "Fetched batch of ${batch.size} lectures")

                            // Clean the lecture data and extract region information
                            val cleanedBatch = batch.map { lecture ->
                                // Remove newlines entirely from Recruitment_period and Education_period
                                val cleanedRecruitmentPeriod = lecture.Recruitment_period?.replace("\n", "")
                                val cleanedEducationPeriod = lecture.Education_period?.replace("\n", "")

                                // Determine region based on Institution
                                var region = ""
                                val institution = lecture.Institution ?: ""

                                if (institution.contains("센터")) {
                                    // Case 1: If Institution contains "센터", extract the part before "센터" and append "구"
                                    val centerIndex = institution.indexOf("센터")
                                    if (centerIndex > 0) {
                                        // Extract the part before "센터", trim whitespace, and append "구"
                                        region = institution.substring(0, centerIndex).trim() + "구"
                                    }
                                } else {
                                    // Case 2: Handle specific campus names
                                    region = when (institution) {
                                        "남부캠퍼스" -> "구로구"
                                        "중부캠퍼스" -> "마포구"
                                        "서부캠퍼스" -> "은평구"
                                        "북부캠퍼스" -> "도봉구"
                                        "동부캠퍼스" -> "광진구"
                                        else -> "기타"
                                    }
                                }

                                // Add logging to see extracted regions
//                                if (region.isNotEmpty()) {
////                                    Log.d("supabase", "Extracted region '$region' from institution '$institution'")
//                                }

                                // Create a copy of the lecture with the cleaned fields and region information
                                // We'll store the region in the existing Institution field with a prefix
                                // so it can be used for filtering while preserving the original institution name
                                val updatedInstitution = if (region.isNotEmpty()) {
                                    "$institution [REGION:서울특별시 $region]"
                                } else {
                                    institution
                                }

                                lecture.copy(
                                    Recruitment_period = cleanedRecruitmentPeriod,
                                    Education_period = cleanedEducationPeriod,
                                    Institution = updatedInstitution
                                )
                            }

                            allLectures.addAll(cleanedBatch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching lectures batch $currentStart-$currentEnd: ${e.message}", e)
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allLectures.size} lectures out of $totalCount total")

                    // Log a sample lecture for debugging
                    if (allLectures.isNotEmpty()) {
                        val sample = allLectures.first()
                        Log.d("supabase", "Sample lecture: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "institution=${sample.Institution}, " +
                                "period=${sample.Education_period}")
                    }

                    allLectures
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getLectures inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getLectures: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // 4. KK_Job data
    @Serializable
    data class KKJob(
        val Id: Int,
        val Title: String? = null,
        val DateOfRegistration: String? = null,
        val Deadline: String? = null,
        val JobCategory: String? = null,
        val ExperienceRequired: String? = null,
        val EmploymentType: String? = null,
        val Salary: String? = null,
        val SocialEnsurance: String? = null,
        val RetirementBenefit: String? = null,
        val Address: String? = null,
        val Category: String? = null,
        val WorkingHours: String? = null,
        val WorkingType: String? = null,
        val CompanyName: String? = null,
        val JobDescription: String? = null,
        val ApplicationMethod: String? = null,
        val ApplicationType: String? = null,
        val Document: String? = null,
        val Detail: String? = null
    )

    // Helper function to clean working hours
    private fun cleanWorkingHours(workingHours: String?): String? {
        if (workingHours == null) return null

        var result = workingHours

        // Remove "주 소정근로시간" if present
        if (result.contains("주 소정근로시간")) {
            val parts = result.split("주 소정근로시간")
            result = if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                parts[0].trim()
            } else {
                if (parts.size > 1) parts[1].trim() else result
            }
        }

        // Remove "(근무시간) " if present
        if (result.contains("(근무시간) ")) {
            result = result.replace("(근무시간) ", "").trim()
        }

        // Remove "* 상세 근무시간" and everything after it if present
        if (result.contains("* 상세 근무시간")) {
            val parts = result.split("* 상세 근무시간")
            result = parts[0].trim()
        }

        return result
    }

    suspend fun getKKJobs(): List<KKJob> {
        return try {
            Log.d("supabase", "Starting getKKJobs")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["kk_job"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of kk_jobs: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No jobs found in the 'kk_job' table")
                        return@withContext emptyList<KKJob>()
                    }

                    // Use the same approach as getFacilities() - first make a test request
                    val testBatch = supabase.postgrest["kk_job"]
                        .select()
                        .decodeList<KKJob>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allKKJobs = mutableListOf<KKJob>()
                    // Add the first batch that we already retrieved
                    allKKJobs.addAll(testBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching kk_jobs batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["kk_job"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<KKJob>()

                            Log.d("supabase", "Fetched batch of ${batch.size} kk_jobs")

                            // Clean the WorkingHours field for each job before adding to the list
                            val cleanedBatch = batch.map { kkJob ->
                                kkJob.copy(WorkingHours = cleanWorkingHours(kkJob.WorkingHours))
                            }

                            allKKJobs.addAll(cleanedBatch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching kk_jobs batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    // Clean the first batch as well
                    val cleanedFirstBatch = testBatch.map { kkJob ->
                        kkJob.copy(WorkingHours = cleanWorkingHours(kkJob.WorkingHours))
                    }

                    // Replace the first batch with cleaned version
                    if (cleanedFirstBatch.isNotEmpty()) {
                        allKKJobs.clear()
                        allKKJobs.addAll(cleanedFirstBatch)

                        // Add remaining batches
                        currentStart = batchSize
                        while (currentStart < totalCount) {
                            val currentEnd = currentStart + batchSize - 1
                            Log.d("supabase", "Fetching kk_jobs batch: $currentStart to $currentEnd")

                            try {
                                val batch = supabase.postgrest["kk_job"]
                                    .select(filter = {
                                        range(from = currentStart.toLong(), to = currentEnd.toLong())
                                    })
                                    .decodeList<KKJob>()

                                val cleanedBatch = batch.map { kkJob ->
                                    kkJob.copy(WorkingHours = cleanWorkingHours(kkJob.WorkingHours))
                                }

                                allKKJobs.addAll(cleanedBatch)

                                if (batch.isEmpty() || batch.size < batchSize) {
                                    break
                                }

                                currentStart += batchSize
                            } catch (e: Exception) {
                                Log.e("supabase", "Error fetching kk_jobs batch: ${e.message}", e)
                                currentStart += batchSize
                            }
                        }
                    }

                    Log.d("supabase", "Retrieved ${allKKJobs.size} kk_jobs out of $totalCount total")

                    // Verify we got all records
                    if (allKKJobs.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allKKJobs.size} vs $totalCount)")
                    }

                    // Log a sample job for debugging
                    if (allKKJobs.isNotEmpty()) {
                        val sample = allKKJobs.first()
                        Log.d("supabase", "Sample kk_job: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.JobCategory}, " +
                                "hours=${sample.WorkingHours}, " +
                                "deadline=${sample.Deadline}")
                    }

                    allKKJobs
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getKKJobs inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getKKJobs: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific kk_job by ID
    suspend fun getKKJobById(kkJobId: Int): KKJob? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("kk_job")
                    .select(
                        filter = {
                            eq("id", kkJobId)
                        }
                    )
                    .decodeSingleOrNull<KKJob>()

                if (response != null) {
                    Log.d(TAG, "Retrieved kk_job with ID: $kkJobId")

                    // Clean the WorkingHours field if present
                    response.copy(WorkingHours = cleanWorkingHours(response.WorkingHours))
                } else {
                    Log.d(TAG, "No kk_job found with ID: $kkJobId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching kk_job by ID: ${e.message}")
            null
        }
    }

    // 5. KK_Culture data
    @Serializable
    data class KKCulture(
        val Id: Int,
        val Category: String? = null,
        val Title: String? = null,
        val Recruitment_period: String? = null,
        val Education_period: String? = null,
        val Institution: String? = null,
        val Address: String? = null,
        val Quota: String? = null,
        val State: String? = null,
        val Registration: String? = null,
        val Detail: String? = null,
        val Date: String? = null,
        val Tel: String? = null,
        val Fee: String? = null
    )

    suspend fun getKKCultures(): List<KKCulture> {
        return try {
            Log.d("supabase", "Starting getKKCultures")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["kk_culture"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of kk_cultures: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No cultures found in the 'kk_culture' table")
                        return@withContext emptyList<KKCulture>()
                    }

                    // Use the same approach as getKKJobs() - first make a test request
                    val testBatch = supabase.postgrest["kk_culture"]
                        .select()
                        .decodeList<KKCulture>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allKKCultures = mutableListOf<KKCulture>()

                    // Clean the test batch data
                    val cleanedFirstBatch = testBatch.map { kkCulture ->
                        // Remove newlines from period fields
                        val cleanedRecruitmentPeriod = kkCulture.Recruitment_period?.replace("\n", "")
                        val cleanedEducationPeriod = kkCulture.Education_period?.replace("\n", "")

                        kkCulture.copy(
                            Recruitment_period = cleanedRecruitmentPeriod,
                            Education_period = cleanedEducationPeriod
                        )
                    }

                    // Add the cleaned first batch
                    allKKCultures.addAll(cleanedFirstBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching kk_cultures batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["kk_culture"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<KKCulture>()

                            Log.d("supabase", "Fetched batch of ${batch.size} kk_cultures")

                            // Clean the batch data
                            val cleanedBatch = batch.map { kkCulture ->
                                // Remove newlines from period fields
                                val cleanedRecruitmentPeriod = kkCulture.Recruitment_period?.replace("\n", "")
                                val cleanedEducationPeriod = kkCulture.Education_period?.replace("\n", "")

                                kkCulture.copy(
                                    Recruitment_period = cleanedRecruitmentPeriod,
                                    Education_period = cleanedEducationPeriod
                                )
                            }

                            allKKCultures.addAll(cleanedBatch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching kk_cultures batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allKKCultures.size} kk_cultures out of $totalCount total")

                    // Verify we got all records
                    if (allKKCultures.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allKKCultures.size} vs $totalCount)")
                    }

                    // Log a sample culture for debugging
                    if (allKKCultures.isNotEmpty()) {
                        val sample = allKKCultures.first()
                        Log.d("supabase", "Sample kk_culture: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "institution=${sample.Institution}, " +
                                "address=${sample.Address}, " +
                                "category=${sample.Category}")
                    }

                    allKKCultures
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getKKCultures inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getKKCultures: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific kk_culture by ID
    suspend fun getKKCultureById(kkCultureId: Int): KKCulture? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("kk_culture")
                    .select(
                        filter = {
                            eq("id", kkCultureId)
                        }
                    )
                    .decodeSingleOrNull<KKCulture>()

                if (response != null) {
                    Log.d(TAG, "Retrieved kk_culture with ID: $kkCultureId")

                    // Clean the period fields if present
                    val cleanedRecruitmentPeriod = response.Recruitment_period?.replace("\n", "")
                    val cleanedEducationPeriod = response.Education_period?.replace("\n", "")

                    response.copy(
                        Recruitment_period = cleanedRecruitmentPeriod,
                        Education_period = cleanedEducationPeriod
                    )
                } else {
                    Log.d(TAG, "No kk_culture found with ID: $kkCultureId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching kk_culture by ID: ${e.message}")
            null
        }
    }

    @Serializable
    data class KKFacility(
        val Id: Int,
        val Category: String? = null,
        val Title: String? = null,
        val Service1: String? = null,
        val Service2: String? = null,
        val Rating: String? = null,
        val Full: String? = null,
        val Now: String? = null,
        val Wating: String? = null,
        val Bus: String? = null,
        val Address: String? = null,
        val Tel: String? = null
    )

    suspend fun getKKFacilities(): List<KKFacility> {
        return try {
            Log.d("supabase", "Starting getKKFacilities")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["kk_facility"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of kk_facilities: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No facilities found in the 'kk_facility' table")
                        return@withContext emptyList<KKFacility>()
                    }

                    // Use the same approach as getKKJobs() - first make a test request
                    val testBatch = supabase.postgrest["kk_facility"]
                        .select()
                        .decodeList<KKFacility>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allKKFacilities = mutableListOf<KKFacility>()

                    // Add the first batch
                    allKKFacilities.addAll(testBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching kk_facilities batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["kk_facility"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<KKFacility>()

                            Log.d("supabase", "Fetched batch of ${batch.size} kk_facilities")

                            allKKFacilities.addAll(batch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching kk_facilities batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allKKFacilities.size} kk_facilities out of $totalCount total")

                    // Verify we got all records
                    if (allKKFacilities.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allKKFacilities.size} vs $totalCount)")
                    }

                    // Log a sample facility for debugging
                    if (allKKFacilities.isNotEmpty()) {
                        val sample = allKKFacilities.first()
                        Log.d("supabase", "Sample kk_facility: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.Category}, " +
                                "address=${sample.Address}")
                    }

                    allKKFacilities
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getKKFacilities inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getKKFacilities: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific kk_facility by ID
    suspend fun getKKFacilityById(kkFacilityId: Int): KKFacility? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("kk_facility")
                    .select(
                        filter = {
                            eq("id", kkFacilityId)
                        }
                    )
                    .decodeSingleOrNull<KKFacility>()

                if (response != null) {
                    Log.d(TAG, "Retrieved kk_facility with ID: $kkFacilityId")
                    response
                } else {
                    Log.d(TAG, "No kk_facility found with ID: $kkFacilityId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching kk_facility by ID: ${e.message}")
            null
        }
    }

    @Serializable
    data class KKFacility2(
        val Id: Int,
        val Category: String? = null,
        val Service1: String? = null,
        val Service2: String? = null,
        val Title: String? = null,
        val Address: String? = null,
        val Institution: String? = null,
        val Tel: String? = null,
        val Users: Int? = null,
        val Quota: Int? = null
    )

    suspend fun getKKFacility2s(): List<KKFacility2> {
        return try {
            Log.d("supabase", "Starting getKKFacility2s")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["kk_facility2"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of kk_facility2s: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No facilities found in the 'kk_facility2' table")
                        return@withContext emptyList<KKFacility2>()
                    }

                    // Use the same approach - first make a test request
                    val testBatch = supabase.postgrest["kk_facility2"]
                        .select()
                        .decodeList<KKFacility2>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allKKFacility2s = mutableListOf<KKFacility2>()

                    // Add the first batch
                    allKKFacility2s.addAll(testBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching kk_facility2s batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["kk_facility2"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<KKFacility2>()

                            Log.d("supabase", "Fetched batch of ${batch.size} kk_facility2s")

                            allKKFacility2s.addAll(batch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching kk_facility2s batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allKKFacility2s.size} kk_facility2s out of $totalCount total")

                    // Verify we got all records
                    if (allKKFacility2s.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allKKFacility2s.size} vs $totalCount)")
                    }

                    // Log a sample facility for debugging
                    if (allKKFacility2s.isNotEmpty()) {
                        val sample = allKKFacility2s.first()
                        Log.d("supabase", "Sample kk_facility2: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.Category}, " +
                                "address=${sample.Address}, " +
                                "institution=${sample.Institution}")
                    }

                    allKKFacility2s
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getKKFacility2s inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getKKFacility2s: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // SupabaseDatabaseHelper.kt에 추가할 내용

    // 6. ICH_Facility data
    @Serializable
    data class ICHFacility(
        val Id: Int,
        val Category: String? = null,
        val Title: String? = null,
        val Service1: String? = null,
        val Service2: String? = null,
        val Rating: String? = null,
        val Full: String? = null,
        val Now: String? = null,
        val Wating: String? = null,
        val Bus: String? = null,
        val Address: String? = null,
        val Tel: String? = null,
        val created_at: String? = null
    )

    suspend fun getICHFacilities(): List<ICHFacility> {
        return try {
            Log.d("supabase", "Starting getICHFacilities")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["ich_facility"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of ich_facilities: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No facilities found in the 'ich_facility' table")
                        return@withContext emptyList<ICHFacility>()
                    }

                    // Use the same approach as getKKFacilities() - first make a test request
                    val testBatch = supabase.postgrest["ich_facility"]
                        .select()
                        .decodeList<ICHFacility>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allICHFacilities = mutableListOf<ICHFacility>()

                    // Add the first batch
                    allICHFacilities.addAll(testBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching ich_facilities batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["ich_facility"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<ICHFacility>()

                            Log.d("supabase", "Fetched batch of ${batch.size} ich_facilities")

                            allICHFacilities.addAll(batch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching ich_facilities batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allICHFacilities.size} ich_facilities out of $totalCount total")

                    // Verify we got all records
                    if (allICHFacilities.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allICHFacilities.size} vs $totalCount)")
                    }

                    // Log a sample facility for debugging
                    if (allICHFacilities.isNotEmpty()) {
                        val sample = allICHFacilities.first()
                        Log.d("supabase", "Sample ich_facility: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.Category}, " +
                                "address=${sample.Address}")
                    }

                    allICHFacilities
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getICHFacilities inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getICHFacilities: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific ich_facility by ID
    suspend fun getICHFacilityById(ichFacilityId: Int): ICHFacility? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("ich_facility")
                    .select(
                        filter = {
                            eq("id", ichFacilityId)
                        }
                    )
                    .decodeSingleOrNull<ICHFacility>()

                if (response != null) {
                    Log.d(TAG, "Retrieved ich_facility with ID: $ichFacilityId")
                    response
                } else {
                    Log.d(TAG, "No ich_facility found with ID: $ichFacilityId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ich_facility by ID: ${e.message}")
            null
        }
    }


    // 7. ICH_Job data (KK_Job과 동일한 구조)
    @Serializable
    data class ICHJob(
        val Id: Int,
        val Title: String? = null,
        val DateOfRegistration: String? = null,
        val Deadline: String? = null,
        val JobCategory: String? = null,
        val ExperienceRequired: String? = null,
        val EmploymentType: String? = null,
        val Salary: String? = null,
        val SocialEnsurance: String? = null,
        val RetirementBenefit: String? = null,
        val Address: String? = null,
        val Category: String? = null,
        val WorkingHours: String? = null,
        val WorkingType: String? = null,
        val CompanyName: String? = null,
        val JobDescription: String? = null,
        val ApplicationMethod: String? = null,
        val ApplicationType: String? = null,
        val Document: String? = null,
        val Detail: String? = null
    )

    suspend fun getICHJobs(): List<ICHJob> {
        return try {
            Log.d("supabase", "Starting getICHJobs")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["ich_job"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of ich_jobs: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No jobs found in the 'ich_job' table")
                        return@withContext emptyList<ICHJob>()
                    }

                    // Use the same approach as getKKJobs() - first make a test request
                    val testBatch = supabase.postgrest["ich_job"]
                        .select()
                        .decodeList<ICHJob>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allICHJobs = mutableListOf<ICHJob>()

                    // Clean the test batch data
                    val cleanedFirstBatch = testBatch.map { ichJob ->
                        ichJob.copy(WorkingHours = cleanWorkingHours(ichJob.WorkingHours))
                    }

                    // Add the cleaned first batch
                    allICHJobs.addAll(cleanedFirstBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching ich_jobs batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["ich_job"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<ICHJob>()

                            Log.d("supabase", "Fetched batch of ${batch.size} ich_jobs")

                            // Clean the WorkingHours field for each job before adding to the list
                            val cleanedBatch = batch.map { ichJob ->
                                ichJob.copy(WorkingHours = cleanWorkingHours(ichJob.WorkingHours))
                            }

                            allICHJobs.addAll(cleanedBatch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching ich_jobs batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allICHJobs.size} ich_jobs out of $totalCount total")

                    // Verify we got all records
                    if (allICHJobs.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allICHJobs.size} vs $totalCount)")
                    }

                    // Log a sample job for debugging
                    if (allICHJobs.isNotEmpty()) {
                        val sample = allICHJobs.first()
                        Log.d("supabase", "Sample ich_job: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.JobCategory}, " +
                                "hours=${sample.WorkingHours}, " +
                                "deadline=${sample.Deadline}, " +
                                "address=${sample.Address}")
                    }

                    allICHJobs
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getICHJobs inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getICHJobs: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific ich_job by ID
    suspend fun getICHJobById(ichJobId: Int): ICHJob? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("ich_job")
                    .select(
                        filter = {
                            eq("id", ichJobId)
                        }
                    )
                    .decodeSingleOrNull<ICHJob>()

                if (response != null) {
                    Log.d(TAG, "Retrieved ich_job with ID: $ichJobId")

                    // Clean the WorkingHours field if present
                    response.copy(WorkingHours = cleanWorkingHours(response.WorkingHours))
                } else {
                    Log.d(TAG, "No ich_job found with ID: $ichJobId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ich_job by ID: ${e.message}")
            null
        }
    }

    // SupabaseDatabaseHelper.kt에 추가할 내용

    // 8. ICH_Culture data
    @Serializable
    data class ICHCulture(
        val Id: Int,
        val Category: String? = null,
        val Title: String? = null,
        val Recruitment_period: String? = null,
        val Education_period: String? = null,
        val Date: String? = null,
        val Quota: String? = null,
        val Institution: String? = null,
        val Address: String? = null,
        val Tel: String? = null,
        val Detail: String? = null,
        val Fee: String? = null,
        val created_at: String? = null
    )

    suspend fun getICHCultures(): List<ICHCulture> {
        return try {
            Log.d("supabase", "Starting getICHCultures")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["ich_culture"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of ich_cultures: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No cultures found in the 'ich_culture' table")
                        return@withContext emptyList<ICHCulture>()
                    }

                    // Use the same approach as getKKCultures() - first make a test request
                    val testBatch = supabase.postgrest["ich_culture"]
                        .select()
                        .decodeList<ICHCulture>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allICHCultures = mutableListOf<ICHCulture>()

                    // Clean the test batch data
                    val cleanedFirstBatch = testBatch.map { ichCulture ->
                        // Remove newlines from period fields
                        val cleanedRecruitmentPeriod = ichCulture.Recruitment_period?.replace("\n", "")
                        val cleanedEducationPeriod = ichCulture.Education_period?.replace("\n", "")

                        ichCulture.copy(
                            Recruitment_period = cleanedRecruitmentPeriod,
                            Education_period = cleanedEducationPeriod
                        )
                    }

                    // Add the cleaned first batch
                    allICHCultures.addAll(cleanedFirstBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching ich_cultures batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["ich_culture"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<ICHCulture>()

                            Log.d("supabase", "Fetched batch of ${batch.size} ich_cultures")

                            // Clean the batch data
                            val cleanedBatch = batch.map { ichCulture ->
                                // Remove newlines from period fields
                                val cleanedRecruitmentPeriod = ichCulture.Recruitment_period?.replace("\n", "")
                                val cleanedEducationPeriod = ichCulture.Education_period?.replace("\n", "")

                                ichCulture.copy(
                                    Recruitment_period = cleanedRecruitmentPeriod,
                                    Education_period = cleanedEducationPeriod
                                )
                            }

                            allICHCultures.addAll(cleanedBatch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching ich_cultures batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allICHCultures.size} ich_cultures out of $totalCount total")

                    // Verify we got all records
                    if (allICHCultures.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allICHCultures.size} vs $totalCount)")
                    }

                    // Log a sample culture for debugging
                    if (allICHCultures.isNotEmpty()) {
                        val sample = allICHCultures.first()
                        Log.d("supabase", "Sample ich_culture: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "institution=${sample.Institution}, " +
                                "address=${sample.Address}, " +
                                "category=${sample.Category}")
                    }

                    allICHCultures
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getICHCultures inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getICHCultures: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific ich_culture by ID
    suspend fun getICHCultureById(ichCultureId: Int): ICHCulture? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("ich_culture")
                    .select(
                        filter = {
                            eq("id", ichCultureId)
                        }
                    )
                    .decodeSingleOrNull<ICHCulture>()

                if (response != null) {
                    Log.d(TAG, "Retrieved ich_culture with ID: $ichCultureId")

                    // Clean the period fields if present
                    val cleanedRecruitmentPeriod = response.Recruitment_period?.replace("\n", "")
                    val cleanedEducationPeriod = response.Education_period?.replace("\n", "")

                    response.copy(
                        Recruitment_period = cleanedRecruitmentPeriod,
                        Education_period = cleanedEducationPeriod
                    )
                } else {
                    Log.d(TAG, "No ich_culture found with ID: $ichCultureId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ich_culture by ID: ${e.message}")
            null
        }
    }

    // SupabaseDatabaseHelper.kt에 추가할 내용

    // 9. ICH_Facility2 data
    @Serializable
    data class ICHFacility2(
        val Id: Int,
        val Category: String? = null,
        val Title: String? = null,
        val Service1: String? = null,
        val Service2: String? = null,
        val Address: String? = null,
        val Institution: String? = null,
        val Tel: String? = null
    )

    suspend fun getICHFacility2s(): List<ICHFacility2> {
        return try {
            Log.d("supabase", "Starting getICHFacility2s")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["ich_facility2"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of ich_facility2s: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No facilities found in the 'ich_facility2' table")
                        return@withContext emptyList<ICHFacility2>()
                    }

                    // Use the same approach - first make a test request
                    val testBatch = supabase.postgrest["ich_facility2"]
                        .select()
                        .decodeList<ICHFacility2>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allICHFacility2s = mutableListOf<ICHFacility2>()

                    // Add the first batch
                    allICHFacility2s.addAll(testBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching ich_facility2s batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["ich_facility2"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<ICHFacility2>()

                            Log.d("supabase", "Fetched batch of ${batch.size} ich_facility2s")

                            allICHFacility2s.addAll(batch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching ich_facility2s batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allICHFacility2s.size} ich_facility2s out of $totalCount total")

                    // Verify we got all records
                    if (allICHFacility2s.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allICHFacility2s.size} vs $totalCount)")
                    }

                    // Log a sample facility for debugging
                    if (allICHFacility2s.isNotEmpty()) {
                        val sample = allICHFacility2s.first()
                        Log.d("supabase", "Sample ich_facility2: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.Category}, " +
                                "address=${sample.Address}, " +
                                "institution=${sample.Institution}")
                    }

                    allICHFacility2s
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getICHFacility2s inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getICHFacility2s: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific ich_facility2 by ID
    suspend fun getICHFacility2ById(ichFacility2Id: Int): ICHFacility2? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("ich_facility2")
                    .select(
                        filter = {
                            eq("id", ichFacility2Id)
                        }
                    )
                    .decodeSingleOrNull<ICHFacility2>()

                if (response != null) {
                    Log.d(TAG, "Retrieved ich_facility2 with ID: $ichFacility2Id")
                    response
                } else {
                    Log.d(TAG, "No ich_facility2 found with ID: $ichFacility2Id")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ich_facility2 by ID: ${e.message}")
            null
        }
    }
}