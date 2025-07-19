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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json


import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom
import android.util.Base64
import java.security.MessageDigest
import java.util.Objects.isNull


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

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true  // null 값을 기본값으로 변환
        isLenient = true
    }


    // Helper function to get ISO 8601 formatted timestamp
    private fun getCurrentTimestampIso8601(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.KOREA)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        return sdf.format(Date())
    }


    // Users 테이블 데이터 클래스
    @Serializable
    data class User(
        val id: String? = null,
        val kakao_id: String? = null,  // nullable로 변경 (일반 회원은 kakao_id가 없음)
        val user_id: String? = null,   // 추가
        val password: String? = null,   // 추가 (해시된 패스워드)
        val birth_date: String? = null, // 추가
        val address: String? = null,
        val tel: String? = null,
        val resume: String? = null,     // 기존 이력서 필드
        val resume_completed: String? = null,
        val created_at: String? = null,
        val updated_at: String? = null
    )


    // SearchHistory 테이블 데이터 클래스
    @Serializable
    data class SearchHistory(
        val id: String? = null,
        val user_id: String,
        val query_category: String,
        val query_content: String,
        val category: String,
        val answer: String,
        val created_at: String? = null
    )

    // ApplicationHistory 테이블 데이터 클래스
    @Serializable
    data class ApplicationHistory(
        val id: String? = null,
        val user_id: String,
        val application_category: String,
        val application_content: String,
        val search_history_id: String? = null,  // nullable로 변경
        val created_at: String? = null
    )


    // 안전한 패스워드 해싱 함수 (PBKDF2 사용)
    private fun hashPassword(password: String): String {
        return try {
            // Salt 생성 (16 bytes)
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)

            // PBKDF2 해싱
            val iterations = 10000
            val keyLength = 256

            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded

            // Salt와 해시를 합쳐서 저장 (Base64 인코딩)
            val combined = ByteArray(salt.size + hash.size)
            System.arraycopy(salt, 0, combined, 0, salt.size)
            System.arraycopy(hash, 0, combined, salt.size, hash.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error hashing password: ${e.message}")
            // 폴백: 기본 SHA-256 사용
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(password.toByteArray())
                Base64.encodeToString(hash, Base64.NO_WRAP)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback hash also failed: ${e2.message}")
                password // 최악의 경우 원본 반환 (개발용)
            }
        }
    }

    // 패스워드 검증 함수
    private fun verifyPassword(password: String, storedHash: String): Boolean {
        return try {
            // Base64 디코딩
            val combined = Base64.decode(storedHash, Base64.NO_WRAP)

            // Salt 추출 (처음 16 bytes)
            val salt = ByteArray(16)
            System.arraycopy(combined, 0, salt, 0, 16)

            // 저장된 해시 추출 (나머지 bytes)
            val storedHashBytes = ByteArray(combined.size - 16)
            System.arraycopy(combined, 16, storedHashBytes, 0, storedHashBytes.size)

            // 입력된 패스워드를 같은 salt로 해싱
            val iterations = 10000
            val keyLength = 256

            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val computedHash = factory.generateSecret(spec).encoded

            // 해시 비교
            storedHashBytes.contentEquals(computedHash)
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying password: ${e.message}")
            // 폴백: 단순 문자열 비교 (개발용)
            password == storedHash
        }
    }

    // 로그인 함수 수정 (verifyPassword 사용)
    suspend fun loginUser(userId: String, password: String): User? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Attempting login for user_id: $userId")

                // user_id로 사용자 조회
                val user = supabase.postgrest["users"]
                    .select(filter = {
                        eq("user_id", userId)
                    })
                    .decodeSingleOrNull<User>()

                if (user != null && user.password != null) {
                    // 패스워드 검증
                    if (verifyPassword(password, user.password)) {
                        Log.d(TAG, "Login successful for user: ${user.user_id}")
                        user
                    } else {
                        Log.d(TAG, "Login failed - invalid password")
                        null
                    }
                } else {
                    Log.d(TAG, "Login failed - user not found")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during login: ${e.message}", e)
            null
        }
    }

    // SupabaseDatabaseHelper의 registerUser 함수 수정
    suspend fun registerUser(
        userId: String,
        password: String,
        birthDate: String,
        phoneNumber: String
    ): User? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "=== registerUser 시작 ===")
                Log.d(TAG, "Registering new user: $userId")

                // 중복 user_id 확인
                Log.d(TAG, "중복 확인 중...")
                val existingUsers = supabase.postgrest["users"]
                    .select(filter = {
                        eq("user_id", userId)
                    })
                    .decodeList<User>()

                Log.d(TAG, "기존 사용자 수: ${existingUsers.size}")

                if (existingUsers.isNotEmpty()) {
                    Log.d(TAG, "User ID already exists: $userId")
                    return@withContext null
                }

                Log.d(TAG, "중복 없음 - 새 사용자 생성 진행")

                // 전화번호에서 하이픈 제거
                val cleanPhoneNumber = phoneNumber.replace("-", "")

                // 새 사용자 생성
                val newUser = buildJsonObject {
                    put("user_id", JsonPrimitive(userId))
                    put("password", JsonPrimitive(hashPassword(password)))
                    put("birth_date", JsonPrimitive(birthDate))
                    put("tel", JsonPrimitive(cleanPhoneNumber)) // 하이픈 제거된 번호 저장
                    put("created_at", JsonPrimitive(getCurrentTimestampIso8601()))
                }

                Log.d(TAG, "생성할 사용자 데이터: $newUser")

                try {
                    val response = supabase.postgrest["users"]
                        .insert(newUser)
                        .decodeSingle<User>()

                    Log.d(TAG, "User registered successfully: ${response.user_id}")
                    Log.d(TAG, "생성된 사용자 ID: ${response.id}")
                    response
                } catch (insertError: Exception) {
                    Log.e(TAG, "Insert 중 오류 발생: ${insertError.message}")
                    Log.e(TAG, "Insert 오류 상세: ", insertError)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== registerUser 전체 오류 ===")
            Log.e(TAG, "Error during registration: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    // user_id가 정확히 일치하는지 확인하는 별도 함수
    suspend fun checkUserIdExists(userId: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Checking if user_id exists: '$userId'")

                // 전체 users 목록을 가져와서 확인 (테스트용)
                val allUsers = supabase.postgrest["users"]
                    .select()
                    .decodeList<User>()

                Log.d(TAG, "전체 사용자 수: ${allUsers.size}")

                // user_id가 있는 사용자들만 필터링
                val usersWithUserId = allUsers.filter { !it.user_id.isNullOrEmpty() }
                Log.d(TAG, "user_id가 있는 사용자 수: ${usersWithUserId.size}")

                // 정확히 일치하는 user_id가 있는지 확인
                val exists = usersWithUserId.any { it.user_id == userId }
                Log.d(TAG, "user_id '$userId' 존재 여부: $exists")

                // 비슷한 user_id 출력 (디버깅용)
                usersWithUserId.forEach { user ->
                    if (user.user_id?.contains(userId) == true || userId.contains(user.user_id ?: "")) {
                        Log.d(TAG, "유사한 user_id 발견: '${user.user_id}'")
                    }
                }

                exists
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking user_id existence: ${e.message}")
            false
        }
    }


    // 통합 사용자 조회 함수 (kakao_id 또는 user_id로 조회)
    suspend fun getUserByIdentifier(identifier: String): User? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Getting user by identifier: $identifier")

                var user: User? = null

                // ✅ 카카오 사용자인지 확인 (identifier가 "kakao_"로 시작하는지 확인)
                if (identifier.startsWith("kakao_")) {
                    val kakaoId = identifier.removePrefix("kakao_")
                    Log.d(TAG, "Searching by kakao_id: $kakaoId")

                    user = supabase.postgrest["users"]
                        .select(filter = {
                            eq("kakao_id", kakaoId)
                        })
                        .decodeSingleOrNull<User>()
                } else {
                    // 일반 사용자 - user_id로 조회
                    Log.d(TAG, "Searching by user_id: $identifier")

                    user = supabase.postgrest["users"]
                        .select(filter = {
                            eq("user_id", identifier)
                        })
                        .decodeSingleOrNull<User>()
                }

                if (user != null) {
                    Log.d(TAG, "User found: id=${user.id}, kakao_id=${user.kakao_id}, user_id=${user.user_id}")
                } else {
                    Log.d(TAG, "No user found with identifier: $identifier")
                }

                user
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user by identifier: ${e.message}")
            null
        }
    }

    // user_id로 사용자 정보 가져오기
    suspend fun getUserByUserId(userId: String): User? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest["users"]
                    .select(filter = {
                        eq("user_id", userId)
                    })
                    .decodeSingleOrNull<User>()

                if (response != null) {
                    Log.d(TAG, "Retrieved user with user_id: $userId")
                } else {
                    Log.d(TAG, "No user found with user_id: $userId")
                }

                response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user by user_id: ${e.message}")
            null
        }
    }

    // 아이디 중복 확인 함수 (더 간단하게)
    suspend fun isUserIdAvailable(userId: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Checking availability for user_id: '$userId'")

                val existingUser = supabase.postgrest["users"]
                    .select(filter = {
                        eq("user_id", userId)
                    })
                    .decodeSingleOrNull<User>()

                val isAvailable = existingUser == null
                Log.d(TAG, "User ID '$userId' available: $isAvailable")

                isAvailable
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking user_id availability: ${e.message}")
            false
        }
    }

    // 전화번호 형식 검증 함수
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // 하이픈 제거
        val cleanNumber = phoneNumber.replace("-", "")

        // 010으로 시작하고 총 11자리인지 확인
        return cleanNumber.startsWith("010") && cleanNumber.length == 11 && cleanNumber.all { it.isDigit() }
    }

    // 생년월일 형식 검증 함수
    fun isValidBirthDate(birthDate: String): Boolean {
        if (birthDate.length != 8 || !birthDate.all { it.isDigit() }) {
            return false
        }

        try {
            val year = birthDate.substring(0, 4).toInt()
            val month = birthDate.substring(4, 6).toInt()
            val day = birthDate.substring(6, 8).toInt()

            // 기본 범위 검증
            if (year < 1900 || year > 2024) return false
            if (month < 1 || month > 12) return false
            if (day < 1 || day > 31) return false

            // 월별 일수 검증
            val daysInMonth = when (month) {
                2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }

            return day <= daysInMonth
        } catch (e: Exception) {
            return false
        }
    }

    // 카카오 사용자 정보 저장/업데이트 함수
    suspend fun upsertUser(
        kakaoId: String,
        address: String? = null,
        tel: String? = null
    ): User? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "=== upsertUser 시작 ===")
                Log.d(TAG, "kakaoId: $kakaoId")
                Log.d(TAG, "address: $address")
                Log.d(TAG, "tel: $tel")

                // 기존 사용자 확인
                val existingUser = supabase.postgrest["users"]
                    .select(filter = {
                        eq("kakao_id", kakaoId)
                    })
                    .decodeSingleOrNull<User>()

                Log.d(TAG, "기존 사용자 존재 여부: ${existingUser != null}")
                if (existingUser != null) {
                    Log.d(TAG, "기존 사용자 정보: id=${existingUser.id}, kakao_id=${existingUser.kakao_id}")
                }

                if (existingUser != null) {
                    // 기존 사용자 업데이트 (로그인 시마다 updated_at 갱신)
                    Log.d(TAG, "기존 사용자 업데이트 시작")

                    val updateData = buildJsonObject {
                        // address와 tel이 제공된 경우에만 업데이트
                        address?.let {
                            put("address", JsonPrimitive(it))
                            Log.d(TAG, "업데이트할 address: $it")
                        }
                        tel?.let {
                            put("tel", JsonPrimitive(it))
                            Log.d(TAG, "업데이트할 tel: $it")
                        }
                        // 항상 updated_at은 갱신
                        put("updated_at", JsonPrimitive(getCurrentTimestampIso8601()))
                    }

                    Log.d(TAG, "업데이트 데이터: $updateData")

                    // UPDATE 쿼리 실행
                    val updateResponse = supabase.postgrest["users"]
                        .update(updateData) {
                            eq("kakao_id", kakaoId)
                        }

                    Log.d(TAG, "UPDATE 쿼리 실행 완료")

                    // 업데이트된 데이터 다시 조회
                    val updatedUser = supabase.postgrest["users"]
                        .select(filter = {
                            eq("kakao_id", kakaoId)
                        })
                        .decodeSingleOrNull<User>()

                    Log.d(TAG, "업데이트 후 조회 결과: ${updatedUser?.toString()}")
                    if (updatedUser != null) {
                        Log.d(TAG, "업데이트된 address: ${updatedUser.address}")
                        Log.d(TAG, "업데이트된 tel: ${updatedUser.tel}")
                        Log.d(TAG, "업데이트된 updated_at: ${updatedUser.updated_at}")
                    }

                    updatedUser
                } else {
                    // 새 사용자 생성 (첫 가입)
                    Log.d(TAG, "새 사용자 생성 시작")

                    val newUser = buildJsonObject {
                        put("kakao_id", JsonPrimitive(kakaoId))
                        address?.let {
                            put("address", JsonPrimitive(it))
                            Log.d(TAG, "새 사용자 address: $it")
                        }
                        tel?.let {
                            put("tel", JsonPrimitive(it))
                            Log.d(TAG, "새 사용자 tel: $it")
                        }
                        put("created_at", JsonPrimitive(getCurrentTimestampIso8601()))
                        put("updated_at", JsonPrimitive(getCurrentTimestampIso8601()))
                    }

                    Log.d(TAG, "새 사용자 데이터: $newUser")

                    val response = supabase.postgrest["users"]
                        .insert(newUser)
                        .decodeSingle<User>()

                    Log.d(TAG, "새 사용자 생성 완료: ${response.toString()}")
                    Log.d(TAG, "생성된 사용자 id: ${response.id}")
                    Log.d(TAG, "생성된 사용자 address: ${response.address}")

                    response
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== upsertUser 오류 발생 ===")
            Log.e(TAG, "오류 메시지: ${e.message}")
            Log.e(TAG, "오류 타입: ${e.javaClass.simpleName}")
            e.printStackTrace()
            null
        }
    }

    // 사용자 ID로 사용자 정보 가져오기
    suspend fun getUserByKakaoId(kakaoId: String): User? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest["users"]
                    .select(filter = {
                        eq("kakao_id", kakaoId)
                    })
                    .decodeSingleOrNull<User>()

                if (response != null) {
                    Log.d(TAG, "Retrieved user with kakaoId: $kakaoId")
                } else {
                    Log.d(TAG, "No user found with kakaoId: $kakaoId")
                }

                response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user by kakaoId: ${e.message}")
            null
        }
    }

    // 검색 기록 저장 함수
    suspend fun saveSearchHistory(
        userId: String,
        queryCategory: String,
        queryContent: String,
        category: String = "일반",      // 기본값 추가
        answer: String = ""              // 기본값 추가
    ): SearchHistory? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Saving search history for user: $userId")
                Log.d(TAG, "Category: $category, Answer preview: $answer")

                val searchData = buildJsonObject {
                    put("user_id", JsonPrimitive(userId))
                    put("query_category", JsonPrimitive(queryCategory))
                    put("query_content", JsonPrimitive(queryContent))
                    put("category", JsonPrimitive(category))
                    put("answer", JsonPrimitive(answer))
                }

                val response = supabase.postgrest["search_history"]
                    .insert(searchData)
                    .decodeSingle<SearchHistory>()

                Log.d(TAG, "Search history saved successfully with id: ${response.id}")
                response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving search history: ${e.message}", e)
            null
        }
    }

    // 신청 기록 저장 함수
    suspend fun saveApplicationHistory(
        userId: String,
        applicationCategory: String,
        applicationContent: String,
        searchHistoryId: String? = null
    ): ApplicationHistory? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Saving application history for user: $userId")
                Log.d(TAG, "Category: $applicationCategory, Content: $applicationContent")
                Log.d(TAG, "SearchHistory ID: $searchHistoryId")

                val applicationData = buildJsonObject {
                    put("user_id", JsonPrimitive(userId))
                    put("application_category", JsonPrimitive(applicationCategory))
                    put("application_content", JsonPrimitive(applicationContent))
                    searchHistoryId?.let {
                        put("search_history_id", JsonPrimitive(it))
                    }
                }

                val response = supabase.postgrest["application_history"]
                    .insert(applicationData)
                    .decodeSingle<ApplicationHistory>()

                Log.d(TAG, "Application history saved successfully with id: ${response.id}")
                response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving application history: ${e.message}", e)
            null
        }
    }


    // 사용자의 검색 기록 가져오기
    suspend fun getUserSearchHistory(userId: String): List<SearchHistory> {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest["search_history"]
                    .select(filter = {
                        eq("user_id", userId)
                        order("created_at", Order.DESCENDING) // 최신순 정렬
                    })
                    .decodeList<SearchHistory>()

                Log.d(TAG, "Retrieved ${response.size} search history items for user: $userId")
                response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching search history: ${e.message}", e)
            emptyList()
        }
    }

    // 사용자의 신청 기록 가져오기
    suspend fun getUserApplicationHistory(userId: String): List<ApplicationHistory> {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest["application_history"]
                    .select(filter = {
                        eq("user_id", userId)
                        order("created_at", Order.DESCENDING) // 최신순 정렬
                    })
                    .decodeList<ApplicationHistory>()

                Log.d(TAG, "Retrieved ${response.size} application history items for user: $userId")
                response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching application history: ${e.message}", e)
            emptyList()
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

    // SupabaseDatabaseHelper.kt에 추가할 내용

    // 10. BS_Job data (부산광역시 일자리 데이터)
    @Serializable
    data class BSJob(
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

    suspend fun getBSJobs(): List<BSJob> {
        return try {
            Log.d("supabase", "Starting getBSJobs")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["bs_job"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of bs_jobs: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No jobs found in the 'bs_job' table")
                        return@withContext emptyList<BSJob>()
                    }

                    // Use the same approach - first make a test request
                    val testBatch = supabase.postgrest["bs_job"]
                        .select()
                        .decodeList<BSJob>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allBSJobs = mutableListOf<BSJob>()

                    // Clean the test batch data
                    val cleanedFirstBatch = testBatch.map { bsJob ->
                        bsJob.copy(WorkingHours = cleanWorkingHours(bsJob.WorkingHours))
                    }

                    // Add the cleaned first batch
                    allBSJobs.addAll(cleanedFirstBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching bs_jobs batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["bs_job"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<BSJob>()

                            Log.d("supabase", "Fetched batch of ${batch.size} bs_jobs")

                            // Clean the WorkingHours field for each job before adding to the list
                            val cleanedBatch = batch.map { bsJob ->
                                bsJob.copy(WorkingHours = cleanWorkingHours(bsJob.WorkingHours))
                            }

                            allBSJobs.addAll(cleanedBatch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching bs_jobs batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allBSJobs.size} bs_jobs out of $totalCount total")

                    // Verify we got all records
                    if (allBSJobs.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allBSJobs.size} vs $totalCount)")
                    }

                    // Log a sample job for debugging
                    if (allBSJobs.isNotEmpty()) {
                        val sample = allBSJobs.first()
                        Log.d("supabase", "Sample bs_job: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.JobCategory}, " +
                                "hours=${sample.WorkingHours}, " +
                                "deadline=${sample.Deadline}, " +
                                "address=${sample.Address}")
                    }

                    allBSJobs
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getBSJobs inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getBSJobs: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific bs_job by ID
    suspend fun getBSJobById(bsJobId: Int): BSJob? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("bs_job")
                    .select(
                        filter = {
                            eq("id", bsJobId)
                        }
                    )
                    .decodeSingleOrNull<BSJob>()

                if (response != null) {
                    Log.d(TAG, "Retrieved bs_job with ID: $bsJobId")

                    // Clean the WorkingHours field if present
                    response.copy(WorkingHours = cleanWorkingHours(response.WorkingHours))
                } else {
                    Log.d(TAG, "No bs_job found with ID: $bsJobId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching bs_job by ID: ${e.message}")
            null
        }
    }

    // 11. KB_Job data (경상북도 일자리 데이터)
    @Serializable
    data class KBJob(
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

    suspend fun getKBJobs(): List<KBJob> {
        return try {
            Log.d("supabase", "Starting getKBJobs")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["kb_job"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of kb_jobs: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No jobs found in the 'kb_job' table")
                        return@withContext emptyList<KBJob>()
                    }

                    // Use the same approach - first make a test request
                    val testBatch = supabase.postgrest["kb_job"]
                        .select()
                        .decodeList<KBJob>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allKBJobs = mutableListOf<KBJob>()

                    // Clean the test batch data
                    val cleanedFirstBatch = testBatch.map { kbJob ->
                        kbJob.copy(WorkingHours = cleanWorkingHours(kbJob.WorkingHours))
                    }

                    // Add the cleaned first batch
                    allKBJobs.addAll(cleanedFirstBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching kb_jobs batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["kb_job"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<KBJob>()

                            Log.d("supabase", "Fetched batch of ${batch.size} kb_jobs")

                            // Clean the WorkingHours field for each job before adding to the list
                            val cleanedBatch = batch.map { kbJob ->
                                kbJob.copy(WorkingHours = cleanWorkingHours(kbJob.WorkingHours))
                            }

                            allKBJobs.addAll(cleanedBatch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching kb_jobs batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allKBJobs.size} kb_jobs out of $totalCount total")

                    // Verify we got all records
                    if (allKBJobs.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allKBJobs.size} vs $totalCount)")
                    }

                    // Log a sample job for debugging
                    if (allKBJobs.isNotEmpty()) {
                        val sample = allKBJobs.first()
                        Log.d("supabase", "Sample kb_job: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.JobCategory}, " +
                                "hours=${sample.WorkingHours}, " +
                                "deadline=${sample.Deadline}, " +
                                "address=${sample.Address}")
                    }

                    allKBJobs
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getKBJobs inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getKBJobs: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific kb_job by ID
    suspend fun getKBJobById(kbJobId: Int): KBJob? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("kb_job")
                    .select(
                        filter = {
                            eq("id", kbJobId)
                        }
                    )
                    .decodeSingleOrNull<KBJob>()

                if (response != null) {
                    Log.d(TAG, "Retrieved kb_job with ID: $kbJobId")

                    // Clean the WorkingHours field if present
                    response.copy(WorkingHours = cleanWorkingHours(response.WorkingHours))
                } else {
                    Log.d(TAG, "No kb_job found with ID: $kbJobId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching kb_job by ID: ${e.message}")
            null
        }
    }

    // SupabaseDatabaseHelper.kt에 추가할 내용

    // 12. BS_Culture data (부산광역시 문화강좌 데이터)
    @Serializable
    data class BSCulture(
        val Id: Int,
        val Title: String? = null,
        val Recruitment_period: String? = null,
        val Education_period: String? = null,
        val Date: String? = null,
        val Quota: String? = null,
        val Institution: String? = null,
        val Address: String? = null,
        val Tel: String? = null,
        val Category: String? = null,
        val Fee: String? = null,
        val Detail: String? = null
    )

    suspend fun getBSCultures(): List<BSCulture> {
        return try {
            Log.d("supabase", "Starting getBSCultures")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["bs_culture"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of bs_cultures: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No cultures found in the 'bs_culture' table")
                        return@withContext emptyList<BSCulture>()
                    }

                    // Use the same approach - first make a test request
                    val testBatch = supabase.postgrest["bs_culture"]
                        .select()
                        .decodeList<BSCulture>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allBSCultures = mutableListOf<BSCulture>()

                    // Clean the test batch data
                    val cleanedFirstBatch = testBatch.map { bsCulture ->
                        // Remove newlines from period fields
                        val cleanedRecruitmentPeriod = bsCulture.Recruitment_period?.replace("\n", "")
                        val cleanedEducationPeriod = bsCulture.Education_period?.replace("\n", "")

                        bsCulture.copy(
                            Recruitment_period = cleanedRecruitmentPeriod,
                            Education_period = cleanedEducationPeriod
                        )
                    }

                    // Add the cleaned first batch
                    allBSCultures.addAll(cleanedFirstBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching bs_cultures batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["bs_culture"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<BSCulture>()

                            Log.d("supabase", "Fetched batch of ${batch.size} bs_cultures")

                            // Clean the batch data
                            val cleanedBatch = batch.map { bsCulture ->
                                // Remove newlines from period fields
                                val cleanedRecruitmentPeriod = bsCulture.Recruitment_period?.replace("\n", "")
                                val cleanedEducationPeriod = bsCulture.Education_period?.replace("\n", "")

                                bsCulture.copy(
                                    Recruitment_period = cleanedRecruitmentPeriod,
                                    Education_period = cleanedEducationPeriod
                                )
                            }

                            allBSCultures.addAll(cleanedBatch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching bs_cultures batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allBSCultures.size} bs_cultures out of $totalCount total")

                    // Verify we got all records
                    if (allBSCultures.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allBSCultures.size} vs $totalCount)")
                    }

                    // Log a sample culture for debugging
                    if (allBSCultures.isNotEmpty()) {
                        val sample = allBSCultures.first()
                        Log.d("supabase", "Sample bs_culture: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "institution=${sample.Institution}, " +
                                "address=${sample.Address}, " +
                                "category=${sample.Category}")
                    }

                    allBSCultures
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getBSCultures inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getBSCultures: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific bs_culture by ID
    suspend fun getBSCultureById(bsCultureId: Int): BSCulture? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("bs_culture")
                    .select(
                        filter = {
                            eq("id", bsCultureId)
                        }
                    )
                    .decodeSingleOrNull<BSCulture>()

                if (response != null) {
                    Log.d(TAG, "Retrieved bs_culture with ID: $bsCultureId")

                    // Clean the period fields if present
                    val cleanedRecruitmentPeriod = response.Recruitment_period?.replace("\n", "")
                    val cleanedEducationPeriod = response.Education_period?.replace("\n", "")

                    response.copy(
                        Recruitment_period = cleanedRecruitmentPeriod,
                        Education_period = cleanedEducationPeriod
                    )
                } else {
                    Log.d(TAG, "No bs_culture found with ID: $bsCultureId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching bs_culture by ID: ${e.message}")
            null
        }
    }

    // 13. KB_Culture data (경상북도 문화강좌 데이터)
    @Serializable
    data class KBCulture(
        val Id: Int,
        val Title: String? = null,
        val Education_period: String? = null,
        val Quota: String? = null,
        val Fee: String? = null,
        val Address: String? = null,
        val Category: String? = null,
        val Detail: String? = null,
        val Recruitment_period: String? = null,
        val Institution: String? = null
    )

    suspend fun getKBCultures(): List<KBCulture> {
        return try {
            Log.d("supabase", "Starting getKBCultures")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["kb_culture"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of kb_cultures: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No cultures found in the 'kb_culture' table")
                        return@withContext emptyList<KBCulture>()
                    }

                    // Use the same approach - first make a test request
                    val testBatch = supabase.postgrest["kb_culture"]
                        .select()
                        .decodeList<KBCulture>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allKBCultures = mutableListOf<KBCulture>()

                    // Clean the test batch data
                    val cleanedFirstBatch = testBatch.map { kbCulture ->
                        // Remove newlines from period fields
                        val cleanedRecruitmentPeriod = kbCulture.Recruitment_period?.replace("\n", "")
                        val cleanedEducationPeriod = kbCulture.Education_period?.replace("\n", "")

                        kbCulture.copy(
                            Recruitment_period = cleanedRecruitmentPeriod,
                            Education_period = cleanedEducationPeriod
                        )
                    }

                    // Add the cleaned first batch
                    allKBCultures.addAll(cleanedFirstBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching kb_cultures batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["kb_culture"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<KBCulture>()

                            Log.d("supabase", "Fetched batch of ${batch.size} kb_cultures")

                            // Clean the batch data
                            val cleanedBatch = batch.map { kbCulture ->
                                // Remove newlines from period fields
                                val cleanedRecruitmentPeriod = kbCulture.Recruitment_period?.replace("\n", "")
                                val cleanedEducationPeriod = kbCulture.Education_period?.replace("\n", "")

                                kbCulture.copy(
                                    Recruitment_period = cleanedRecruitmentPeriod,
                                    Education_period = cleanedEducationPeriod
                                )
                            }

                            allKBCultures.addAll(cleanedBatch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching kb_cultures batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allKBCultures.size} kb_cultures out of $totalCount total")

                    // Verify we got all records
                    if (allKBCultures.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allKBCultures.size} vs $totalCount)")
                    }

                    // Log a sample culture for debugging
                    if (allKBCultures.isNotEmpty()) {
                        val sample = allKBCultures.first()
                        Log.d("supabase", "Sample kb_culture: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "institution=${sample.Institution}, " +
                                "address=${sample.Address}, " +
                                "category=${sample.Category}")
                    }

                    allKBCultures
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getKBCultures inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getKBCultures: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific kb_culture by ID
    suspend fun getKBCultureById(kbCultureId: Int): KBCulture? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("kb_culture")
                    .select(
                        filter = {
                            eq("id", kbCultureId)
                        }
                    )
                    .decodeSingleOrNull<KBCulture>()

                if (response != null) {
                    Log.d(TAG, "Retrieved kb_culture with ID: $kbCultureId")

                    // Clean the period fields if present
                    val cleanedRecruitmentPeriod = response.Recruitment_period?.replace("\n", "")
                    val cleanedEducationPeriod = response.Education_period?.replace("\n", "")

                    response.copy(
                        Recruitment_period = cleanedRecruitmentPeriod,
                        Education_period = cleanedEducationPeriod
                    )
                } else {
                    Log.d(TAG, "No kb_culture found with ID: $kbCultureId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching kb_culture by ID: ${e.message}")
            null
        }
    }

    // SupabaseDatabaseHelper.kt에 추가할 내용

    // 14. BS_Facility data (부산광역시 시설 데이터)
    @Serializable
    data class BSFacility(
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

    suspend fun getBSFacilities(): List<BSFacility> {
        return try {
            Log.d("supabase", "Starting getBSFacilities")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["bs_facility"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of bs_facilities: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No facilities found in the 'bs_facility' table")
                        return@withContext emptyList<BSFacility>()
                    }

                    // Use the same approach - first make a test request
                    val testBatch = supabase.postgrest["bs_facility"]
                        .select()
                        .decodeList<BSFacility>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allBSFacilities = mutableListOf<BSFacility>()

                    // Add the first batch
                    allBSFacilities.addAll(testBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching bs_facilities batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["bs_facility"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<BSFacility>()

                            Log.d("supabase", "Fetched batch of ${batch.size} bs_facilities")

                            allBSFacilities.addAll(batch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching bs_facilities batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allBSFacilities.size} bs_facilities out of $totalCount total")

                    // Verify we got all records
                    if (allBSFacilities.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allBSFacilities.size} vs $totalCount)")
                    }

                    // Log a sample facility for debugging
                    if (allBSFacilities.isNotEmpty()) {
                        val sample = allBSFacilities.first()
                        Log.d("supabase", "Sample bs_facility: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.Category}, " +
                                "address=${sample.Address}")
                    }

                    allBSFacilities
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getBSFacilities inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getBSFacilities: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific bs_facility by ID
    suspend fun getBSFacilityById(bsFacilityId: Int): BSFacility? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("bs_facility")
                    .select(
                        filter = {
                            eq("id", bsFacilityId)
                        }
                    )
                    .decodeSingleOrNull<BSFacility>()

                if (response != null) {
                    Log.d(TAG, "Retrieved bs_facility with ID: $bsFacilityId")
                    response
                } else {
                    Log.d(TAG, "No bs_facility found with ID: $bsFacilityId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching bs_facility by ID: ${e.message}")
            null
        }
    }

    // 15. KB_Facility data (경상북도 시설 데이터)
    @Serializable
    data class KBFacility(
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

    suspend fun getKBFacilities(): List<KBFacility> {
        return try {
            Log.d("supabase", "Starting getKBFacilities")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["kb_facility"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of kb_facilities: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No facilities found in the 'kb_facility' table")
                        return@withContext emptyList<KBFacility>()
                    }

                    // Use the same approach - first make a test request
                    val testBatch = supabase.postgrest["kb_facility"]
                        .select()
                        .decodeList<KBFacility>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allKBFacilities = mutableListOf<KBFacility>()

                    // Add the first batch
                    allKBFacilities.addAll(testBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching kb_facilities batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["kb_facility"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<KBFacility>()

                            Log.d("supabase", "Fetched batch of ${batch.size} kb_facilities")

                            allKBFacilities.addAll(batch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching kb_facilities batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allKBFacilities.size} kb_facilities out of $totalCount total")

                    // Verify we got all records
                    if (allKBFacilities.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allKBFacilities.size} vs $totalCount)")
                    }

                    // Log a sample facility for debugging
                    if (allKBFacilities.isNotEmpty()) {
                        val sample = allKBFacilities.first()
                        Log.d("supabase", "Sample kb_facility: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.Category}, " +
                                "address=${sample.Address}")
                    }

                    allKBFacilities
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getKBFacilities inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getKBFacilities: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific kb_facility by ID
    suspend fun getKBFacilityById(kbFacilityId: Int): KBFacility? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("kb_facility")
                    .select(
                        filter = {
                            eq("id", kbFacilityId)
                        }
                    )
                    .decodeSingleOrNull<KBFacility>()

                if (response != null) {
                    Log.d(TAG, "Retrieved kb_facility with ID: $kbFacilityId")
                    response
                } else {
                    Log.d(TAG, "No kb_facility found with ID: $kbFacilityId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching kb_facility by ID: ${e.message}")
            null
        }
    }

    // SupabaseDatabaseHelper.kt에 추가할 내용

    // 16. BS_Facility2 data (부산광역시 시설2 데이터)
    @Serializable
    data class BSFacility2(
        val Id: Int,
        val Category: String? = null,
        val Service1: String? = null,
        val Service2: String? = null,
        val Title: String? = null,
        val Address: String? = null,
        val Institution: String? = null,
        val Tel: String? = null,
        val Users: Int? = null,
        val Quota: Int? = null,
        val Detail: String? = null,
        val created_at: String? = null,
    )

    suspend fun getBSFacility2s(): List<BSFacility2> {
        return try {
            Log.d("supabase", "Starting getBSFacility2s")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["bs_facility2"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of bs_facility2s: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No facilities found in the 'bs_facility2' table")
                        return@withContext emptyList<BSFacility2>()
                    }

                    // Use the same approach - first make a test request
                    val testBatch = supabase.postgrest["bs_facility2"]
                        .select()
                        .decodeList<BSFacility2>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allBSFacility2s = mutableListOf<BSFacility2>()

                    // Add the first batch
                    allBSFacility2s.addAll(testBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching bs_facility2s batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["bs_facility2"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<BSFacility2>()

                            Log.d("supabase", "Fetched batch of ${batch.size} bs_facility2s")

                            allBSFacility2s.addAll(batch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching bs_facility2s batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allBSFacility2s.size} bs_facility2s out of $totalCount total")

                    // Verify we got all records
                    if (allBSFacility2s.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allBSFacility2s.size} vs $totalCount)")
                    }

                    // Log a sample facility for debugging
                    if (allBSFacility2s.isNotEmpty()) {
                        val sample = allBSFacility2s.first()
                        Log.d("supabase", "Sample bs_facility2: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.Category}, " +
                                "address=${sample.Address}, " +
                                "institution=${sample.Institution}")
                    }

                    allBSFacility2s
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getBSFacility2s inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getBSFacility2s: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific bs_facility2 by ID
    suspend fun getBSFacility2ById(bsFacility2Id: Int): BSFacility2? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("bs_facility2")
                    .select(
                        filter = {
                            eq("id", bsFacility2Id)
                        }
                    )
                    .decodeSingleOrNull<BSFacility2>()

                if (response != null) {
                    Log.d(TAG, "Retrieved bs_facility2 with ID: $bsFacility2Id")
                    response
                } else {
                    Log.d(TAG, "No bs_facility2 found with ID: $bsFacility2Id")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching bs_facility2 by ID: ${e.message}")
            null
        }
    }

    // 17. KB_Facility2 data (경상북도 시설2 데이터)
    @Serializable
    data class KBFacility2(
        val Id: Int,
        val Category: String? = null,
        val Service1: String? = null,
        val Service2: String? = null,
        val Title: String? = null,
        val Address: String? = null,
        val Institution: String? = null,
        val Tel: String? = null,
        val Users: Int? = null,
        val Quota: Int? = null,
        val Detail: String? = null,
        val created_at: String? = null,
    )

    suspend fun getKBFacility2s(): List<KBFacility2> {
        return try {
            Log.d("supabase", "Starting getKBFacility2s")

            withContext(Dispatchers.IO) {
                try {
                    // First get the total count
                    val totalCount = supabase.postgrest["kb_facility2"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L

                    Log.d("supabase", "Total count of kb_facility2s: $totalCount")

                    if (totalCount == 0L) {
                        Log.d("supabase", "No facilities found in the 'kb_facility2' table")
                        return@withContext emptyList<KBFacility2>()
                    }

                    // Use the same approach - first make a test request
                    val testBatch = supabase.postgrest["kb_facility2"]
                        .select()
                        .decodeList<KBFacility2>()

                    // The batch size is whatever limit Supabase applied to our first request
                    val batchSize = testBatch.size
                    Log.d("supabase", "Detected batch size from Supabase: $batchSize")

                    // Now we know the batch size, fetch all records
                    val allKBFacility2s = mutableListOf<KBFacility2>()

                    // Add the first batch
                    allKBFacility2s.addAll(testBatch)

                    var currentStart = batchSize

                    // Continue fetching until we have all records
                    while (currentStart < totalCount) {
                        val currentEnd = currentStart + batchSize - 1
                        Log.d("supabase", "Fetching kb_facility2s batch: $currentStart to $currentEnd")

                        try {
                            // Fetch a batch using range
                            val batch = supabase.postgrest["kb_facility2"]
                                .select(filter = {
                                    range(from = currentStart.toLong(), to = currentEnd.toLong())
                                })
                                .decodeList<KBFacility2>()

                            Log.d("supabase", "Fetched batch of ${batch.size} kb_facility2s")

                            allKBFacility2s.addAll(batch)

                            // If we got an empty batch or fewer items than requested, we might be done
                            if (batch.isEmpty() || batch.size < batchSize) {
                                break
                            }

                            // Move to next batch
                            currentStart += batchSize
                        } catch (e: Exception) {
                            Log.e("supabase", "Error fetching kb_facility2s batch $currentStart-$currentEnd: ${e.message}", e)
                            e.printStackTrace()
                            // Continue to next batch despite error
                            currentStart += batchSize
                        }
                    }

                    Log.d("supabase", "Retrieved ${allKBFacility2s.size} kb_facility2s out of $totalCount total")

                    // Verify we got all records
                    if (allKBFacility2s.size < totalCount) {
                        Log.w("supabase", "Warning: Retrieved fewer records than expected (${allKBFacility2s.size} vs $totalCount)")
                    }

                    // Log a sample facility for debugging
                    if (allKBFacility2s.isNotEmpty()) {
                        val sample = allKBFacility2s.first()
                        Log.d("supabase", "Sample kb_facility2: id=${sample.Id}, " +
                                "title=${sample.Title}, " +
                                "category=${sample.Category}, " +
                                "address=${sample.Address}, " +
                                "institution=${sample.Institution}")
                    }

                    allKBFacility2s
                } catch (e: Exception) {
                    Log.e("supabase", "Error in getKBFacility2s inner block: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("supabase", "Error in getKBFacility2s: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Also add a function to get a specific kb_facility2 by ID
    suspend fun getKBFacility2ById(kbFacility2Id: Int): KBFacility2? {
        return try {
            withContext(Dispatchers.IO) {
                val response = supabase.postgrest.from("kb_facility2")
                    .select(
                        filter = {
                            eq("id", kbFacility2Id)
                        }
                    )
                    .decodeSingleOrNull<KBFacility2>()

                if (response != null) {
                    Log.d(TAG, "Retrieved kb_facility2 with ID: $kbFacility2Id")
                    response
                } else {
                    Log.d(TAG, "No kb_facility2 found with ID: $kbFacility2Id")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching kb_facility2 by ID: ${e.message}")
            null
        }
    }

// SupabaseDatabaseHelper.kt

    // 필터링된 문화 강좌 가져오기
    suspend fun getFilteredLectures(city: String, district: String): List<Lecture> {
        return try {
            withContext(Dispatchers.IO) {
                // 서울특별시만 Category로 필터링 가능
                if (city == "서울특별시" && district != "전체") {
                    supabase.postgrest["lecture"]
                        .select(filter = {
                            eq("Category", district)
                        })
                        .decodeList<Lecture>()
                } else {
                    // 전체 데이터 가져오기
                    supabase.postgrest["lecture"]
                        .select()
                        .decodeList<Lecture>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered lectures: ${e.message}")
            emptyList()
        }
    }

    // 경기도 문화 강좌 필터링
    suspend fun getFilteredKKCultures(district: String): List<KKCulture> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["kk_culture"]
                        .select(filter = {
                            eq("Category", district)
                        })
                        .decodeList<KKCulture>()
                } else {
                    supabase.postgrest["kk_culture"]
                        .select()
                        .decodeList<KKCulture>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered kk_cultures: ${e.message}")
            emptyList()
        }
    }

    // 인천광역시 문화 강좌 필터링
    suspend fun getFilteredICHCultures(district: String): List<ICHCulture> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["ich_culture"]
                        .select(filter = {
                            eq("Category", district)
                        })
                        .decodeList<ICHCulture>()
                } else {
                    supabase.postgrest["ich_culture"]
                        .select()
                        .decodeList<ICHCulture>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered ich_cultures: ${e.message}")
            emptyList()
        }
    }

    // 부산광역시 문화 강좌 필터링
    suspend fun getFilteredBSCultures(district: String): List<BSCulture> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["bs_culture"]
                        .select(filter = {
                            eq("Category", district)
                        })
                        .decodeList<BSCulture>()
                } else {
                    supabase.postgrest["bs_culture"]
                        .select()
                        .decodeList<BSCulture>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered bs_cultures: ${e.message}")
            emptyList()
        }
    }

    // 경상북도 문화 강좌 필터링
    suspend fun getFilteredKBCultures(district: String): List<KBCulture> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["kb_culture"]
                        .select(filter = {
                            eq("Category", district)
                        })
                        .decodeList<KBCulture>()
                } else {
                    supabase.postgrest["kb_culture"]
                        .select()
                        .decodeList<KBCulture>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered kb_cultures: ${e.message}")
            emptyList()
        }
    }

    // 페이징 처리가 포함된 버전
    suspend fun getFilteredLecturesPaged(
        city: String,
        district: String,
        page: Int,
        pageSize: Int = 20
    ): List<Lecture> {
        return try {
            withContext(Dispatchers.IO) {
                val from = page * pageSize
                val to = from + pageSize - 1

                if (city == "서울특별시" && district != "전체") {
                    supabase.postgrest["lecture"]
                        .select(filter = {
                            eq("Category", district)
                            range(from = from.toLong(), to = to.toLong())
                        })
                        .decodeList<Lecture>()
                } else {
                    supabase.postgrest["lecture"]
                        .select(filter = {
                            range(from = from.toLong(), to = to.toLong())
                        })
                        .decodeList<Lecture>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching paged lectures: ${e.message}")
            emptyList()
        }
    }

    // 전체 개수와 함께 가져오는 버전
    suspend fun getFilteredLecturesWithCount(
        city: String,
        district: String,
        page: Int,
        pageSize: Int = 20
    ): Pair<List<Lecture>, Int> {
        return try {
            withContext(Dispatchers.IO) {
                // 전체 개수 조회
                val totalCount = if (city == "서울특별시" && district != "전체") {
                    supabase.postgrest["lecture"]
                        .select(head = true, count = Count.EXACT, filter = {
                            eq("Category", district)
                        })
                        .count() ?: 0L
                } else {
                    supabase.postgrest["lecture"]
                        .select(head = true, count = Count.EXACT)
                        .count() ?: 0L
                }

                // 실제 데이터 조회
                val from = page * pageSize
                val to = from + pageSize - 1

                val data = if (city == "서울특별시" && district != "전체") {
                    supabase.postgrest["lecture"]
                        .select(filter = {
                            eq("Category", district)
                            range(from = from.toLong(), to = to.toLong())
                        })
                        .decodeList<Lecture>()
                } else {
                    supabase.postgrest["lecture"]
                        .select(filter = {
                            range(from = from.toLong(), to = to.toLong())
                        })
                        .decodeList<Lecture>()
                }

                Pair(data, totalCount.toInt())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lectures with count: ${e.message}")
            Pair(emptyList(), 0)
        }
    }


    // 필터링된 일자리 가져오기
    suspend fun getFilteredJobs(city: String, district: String): List<Job> {
        return try {
            withContext(Dispatchers.IO) {
                if (city == "서울특별시" && district != "전체") {
                    supabase.postgrest["job"]
                        .select(filter = {
                            like("Address", "%$city%")
                            like("Address", "%$district%")
                        })
                        .decodeList<Job>()
                } else if (city != "전체") {
                    supabase.postgrest["job"]
                        .select(filter = {
                            like("Address", "%$city%")
                        })
                        .decodeList<Job>()
                } else {
                    supabase.postgrest["job"]
                        .select()
                        .decodeList<Job>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered jobs: ${e.message}")
            emptyList()
        }
    }

    // 경기도 일자리 필터링
    suspend fun getFilteredKKJobs(district: String): List<KKJob> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["kk_job"]
                        .select(filter = {
                            like("Address", "%경기%")
                            like("Address", "%$district%")
                        })
                        .decodeList<KKJob>()
                } else {
                    supabase.postgrest["kk_job"]
                        .select(filter = {
                            like("Address", "%경기%")
                        })
                        .decodeList<KKJob>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered kk_jobs: ${e.message}")
            emptyList()
        }
    }

    // 인천광역시 일자리 필터링
    suspend fun getFilteredICHJobs(district: String): List<ICHJob> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["ich_job"]
                        .select(filter = {
                            like("Address", "%인천%")
                            like("Address", "%$district%")
                        })
                        .decodeList<ICHJob>()
                } else {
                    supabase.postgrest["ich_job"]
                        .select(filter = {
                            like("Address", "%인천%")
                        })
                        .decodeList<ICHJob>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered ich_jobs: ${e.message}")
            emptyList()
        }
    }

    // 부산광역시 일자리 필터링
    suspend fun getFilteredBSJobs(district: String): List<BSJob> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["bs_job"]
                        .select(filter = {
                            like("Address", "%부산%")
                            like("Address", "%$district%")
                        })
                        .decodeList<BSJob>()
                } else {
                    supabase.postgrest["bs_job"]
                        .select(filter = {
                            like("Address", "%부산%")
                        })
                        .decodeList<BSJob>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered bs_jobs: ${e.message}")
            emptyList()
        }
    }

    // 경상북도 일자리 필터링
    suspend fun getFilteredKBJobs(district: String): List<KBJob> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["kb_job"]
                        .select(filter = {
                            like("Address", "%경상북도%")
                            like("Address", "%$district%")
                        })
                        .decodeList<KBJob>()
                } else {
                    supabase.postgrest["kb_job"]
                        .select(filter = {
                            like("Address", "%경상북도%")
                        })
                        .decodeList<KBJob>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered kb_jobs: ${e.message}")
            emptyList()
        }
    }

    suspend fun getFilteredFacilities(city: String, district: String): List<facilities> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["facilities"]
                        .select(filter = {
                            like("address", "%$city%")
                            like("address", "%$district%")
                        })
                        .decodeList<facilities>()
                } else {
                    supabase.postgrest["facilities"]
                        .select(filter = {
                            like("address", "%$city%")
                        })
                        .decodeList<facilities>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered facilities: ${e.message}")
            emptyList()
        }
    }

    // 경기도 시설 필터링
    suspend fun getFilteredKKFacilities(district: String): List<KKFacility> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["kk_facility"]
                        .select(filter = {
                            like("Address", "%경기%")
                            like("Address", "%$district%")
                        })
                        .decodeList<KKFacility>()
                } else {
                    supabase.postgrest["kk_facility"]
                        .select(filter = {
                            like("Address", "%경기%")
                        })
                        .decodeList<KKFacility>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered kk_facilities: ${e.message}")
            emptyList()
        }
    }

    // 경기도 시설2 필터링
    suspend fun getFilteredKKFacility2s(district: String): List<KKFacility2> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["kk_facility2"]
                        .select(filter = {
                            like("Address", "%경기%")
                            like("Address", "%$district%")
                        })
                        .decodeList<KKFacility2>()
                } else {
                    supabase.postgrest["kk_facility2"]
                        .select(filter = {
                            like("Address", "%경기%")
                        })
                        .decodeList<KKFacility2>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered kk_facility2s: ${e.message}")
            emptyList()
        }
    }

    // 인천광역시 시설 필터링
    suspend fun getFilteredICHFacilities(district: String): List<ICHFacility> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["ich_facility"]
                        .select(filter = {
                            like("Address", "%인천%")
                            like("Address", "%$district%")
                        })
                        .decodeList<ICHFacility>()
                } else {
                    supabase.postgrest["ich_facility"]
                        .select(filter = {
                            like("Address", "%인천%")
                        })
                        .decodeList<ICHFacility>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered ich_facilities: ${e.message}")
            emptyList()
        }
    }

    // 인천광역시 시설2 필터링
    suspend fun getFilteredICHFacility2s(district: String): List<ICHFacility2> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["ich_facility2"]
                        .select(filter = {
                            like("Address", "%인천%")
                            like("Address", "%$district%")
                        })
                        .decodeList<ICHFacility2>()
                } else {
                    supabase.postgrest["ich_facility2"]
                        .select(filter = {
                            like("Address", "%인천%")
                        })
                        .decodeList<ICHFacility2>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered ich_facility2s: ${e.message}")
            emptyList()
        }
    }

    // 부산광역시 시설 필터링
    suspend fun getFilteredBSFacilities(district: String): List<BSFacility> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["bs_facility"]
                        .select(filter = {
                            like("Address", "%부산%")
                            like("Address", "%$district%")
                        })
                        .decodeList<BSFacility>()
                } else {
                    supabase.postgrest["bs_facility"]
                        .select(filter = {
                            like("Address", "%부산%")
                        })
                        .decodeList<BSFacility>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered bs_facilities: ${e.message}")
            emptyList()
        }
    }

    // 부산광역시 시설2 필터링
    suspend fun getFilteredBSFacility2s(district: String): List<BSFacility2> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["bs_facility2"]
                        .select(filter = {
                            like("Address", "%부산%")
                            like("Address", "%$district%")
                        })
                        .decodeList<BSFacility2>()
                } else {
                    supabase.postgrest["bs_facility2"]
                        .select(filter = {
                            like("Address", "%부산%")
                        })
                        .decodeList<BSFacility2>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered bs_facility2s: ${e.message}")
            emptyList()
        }
    }

    // 경상북도 시설 필터링
    suspend fun getFilteredKBFacilities(district: String): List<KBFacility> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["kb_facility"]
                        .select(filter = {
                            like("Address", "%경상북도%")
                            like("Address", "%$district%")
                        })
                        .decodeList<KBFacility>()
                } else {
                    supabase.postgrest["kb_facility"]
                        .select(filter = {
                            like("Address", "%경상북도%")
                        })
                        .decodeList<KBFacility>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered kb_facilities: ${e.message}")
            emptyList()
        }
    }

    // 경상북도 시설2 필터링
    suspend fun getFilteredKBFacility2s(district: String): List<KBFacility2> {
        return try {
            withContext(Dispatchers.IO) {
                if (district != "전체") {
                    supabase.postgrest["kb_facility2"]
                        .select(filter = {
                            like("Address", "%경상북도%")
                            like("Address", "%$district%")
                        })
                        .decodeList<KBFacility2>()
                } else {
                    supabase.postgrest["kb_facility2"]
                        .select(filter = {
                            like("Address", "%경상북도%")
                        })
                        .decodeList<KBFacility2>()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filtered kb_facility2s: ${e.message}")
            emptyList()
        }
    }

    // SupabaseDatabaseHelper.kt에 추가할 이력서 관련 함수들

    /**
     * 사용자의 이력서를 업데이트하는 함수 (사용자 ID로)
     */
    suspend fun updateUserResume(userId: String, resumeContent: String): User? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "=== 이력서 업데이트 시작 ===")
                Log.d(TAG, "사용자 ID: $userId")
                Log.d(TAG, "이력서 내용 길이: ${resumeContent.length}자")

                // Supabase users 테이블에서 해당 사용자의 resume 열 업데이트
                val updateData = buildJsonObject {
                    put("resume", JsonPrimitive(resumeContent))
                    put("updated_at", JsonPrimitive(getCurrentTimestampIso8601()))
                }

                Log.d(TAG, "업데이트 데이터 준비 완료")

                // UPDATE 쿼리 실행
                val updateResponse = supabase.postgrest["users"]
                    .update(updateData) {
                        eq("id", userId)
                    }

                Log.d(TAG, "UPDATE 쿼리 실행 완료")

                // 업데이트된 데이터 다시 조회
                val updatedUser = supabase.postgrest["users"]
                    .select(filter = {
                        eq("id", userId)
                    })
                    .decodeSingleOrNull<User>()

                if (updatedUser != null) {
                    Log.d(TAG, "이력서 업데이트 성공")
                    Log.d(TAG, "업데이트된 사용자: ${updatedUser.user_id ?: updatedUser.kakao_id}")
                    Log.d(TAG, "이력서 길이: ${updatedUser.resume?.length ?: 0}자")
                } else {
                    Log.e(TAG, "이력서 업데이트 후 사용자 조회 실패")
                }

                updatedUser
            }
        } catch (e: Exception) {
            Log.e(TAG, "이력서 업데이트 실패: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 사용자의 이력서를 조회하는 함수
     */
    suspend fun getUserResume(userId: String): String? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "=== 사용자 이력서 조회 ===")
                Log.d(TAG, "사용자 ID: $userId")

                val user = supabase.postgrest["users"]
                    .select(filter = {
                        eq("id", userId)
                    })
                    .decodeSingleOrNull<User>()

                if (user != null) {
                    Log.d(TAG, "이력서 조회 성공")
                    Log.d(TAG, "이력서 내용 길이: ${user.resume?.length ?: 0}자")
                    user.resume
                } else {
                    Log.d(TAG, "사용자를 찾을 수 없음")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "이력서 조회 실패: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 사용자 ID로 이력서 유무 확인
     */
    suspend fun hasUserResume(userId: String): Boolean {
        return try {
            val resume = getUserResume(userId)
            !resume.isNullOrEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "이력서 유무 확인 실패: ${e.message}", e)
            false
        }
    }

    /**
     * 통합 사용자 조회 후 이력서 업데이트 (kakao_id 또는 user_id로 조회)
     */
    suspend fun updateResumeByIdentifier(identifier: String, resumeContent: String): User? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "=== 식별자로 이력서 업데이트 ===")
                Log.d(TAG, "식별자: $identifier")

                // 먼저 사용자 찾기
                val user = getUserByIdentifier(identifier)

                if (user?.id != null) {
                    Log.d(TAG, "사용자 찾음: ${user.user_id ?: user.kakao_id}")

                    // 이력서 업데이트
                    updateUserResume(user.id, resumeContent)
                } else {
                    Log.e(TAG, "식별자로 사용자를 찾을 수 없음: $identifier")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "식별자로 이력서 업데이트 실패: ${e.message}", e)
            null
        }

    }

    suspend fun getResumeByIdentifier(identifier: String): String? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "=== 식별자로 이력서 조회 ===")
                Log.d(TAG, "식별자: $identifier")

                val user = getUserByIdentifier(identifier)

                if (user != null) {
                    Log.d(TAG, "사용자 찾음: ${user.user_id ?: user.kakao_id}")
                    Log.d(TAG, "이력서 길이: ${user.resume?.length ?: 0}자")
                    user.resume
                } else {
                    Log.d(TAG, "식별자로 사용자를 찾을 수 없음: $identifier")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "식별자로 이력서 조회 실패: ${e.message}", e)
            null
        }
    }

    /**
     * 이력서 삭제 함수
     */
    suspend fun deleteResumeByIdentifier(identifier: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "=== 이력서 삭제 ===")
                Log.d(TAG, "식별자: $identifier")

                val user = getUserByIdentifier(identifier)

                if (user?.id != null) {
                    val updateData = buildJsonObject {
                        put("resume", JsonPrimitive(null as String?))
                        put("updated_at", JsonPrimitive(getCurrentTimestampIso8601()))
                    }

                    val updateResponse = supabase.postgrest["users"]
                        .update(updateData) {
                            eq("id", user.id)
                        }

                    Log.d(TAG, "이력서 삭제 성공")
                    true
                } else {
                    Log.e(TAG, "사용자를 찾을 수 없어 이력서 삭제 실패")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "이력서 삭제 실패: ${e.message}", e)
            false
        }
    }

    suspend fun getResumeCompletedByIdentifier(identifier: String): String? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Getting resume_completed for identifier: $identifier")

                val user = if (identifier.matches(Regex("\\d+"))) {
                    // 숫자만 있으면 kakao_id로 검색
                    Log.d(TAG, "Searching by kakao_id: $identifier")
                    supabase.postgrest["users"]
                        .select(filter = {
                            eq("kakao_id", identifier)
                        })
                        .decodeSingleOrNull<User>()
                } else {
                    // 그 외에는 user_id로 검색
                    Log.d(TAG, "Searching by user_id: $identifier")
                    supabase.postgrest["users"]
                        .select(filter = {
                            eq("user_id", identifier)
                        })
                        .decodeSingleOrNull<User>()
                }

                Log.d(TAG, "User found: ${user != null}")
                if (user != null) {
                    Log.d(TAG, "User details: id=${user.id}, kakao_id=${user.kakao_id}, user_id=${user.user_id}")
                    Log.d(TAG, "resume_completed is null: ${user.resume_completed == null}")
                    Log.d(TAG, "resume_completed value: ${user.resume_completed}")

                    if (user.resume_completed != null) {
                        Log.d(TAG, "resume_completed length: ${user.resume_completed.length}")

                        try {
                            // JSON 문자열을 파싱하여 content 추출
                            val jsonObject = org.json.JSONObject(user.resume_completed)
                            val content = jsonObject.optString("content", null)

                            Log.d(TAG, "Parsed content length: ${content?.length ?: 0}")
                            Log.d(TAG, "Content preview: ${content?.take(100)}")

                            if (content.isNullOrEmpty()) {
                                Log.d(TAG, "Content is empty in resume_completed")
                                null
                            } else {
                                content
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing resume_completed JSON: ${e.message}")
                            Log.d(TAG, "Raw resume_completed data: ${user.resume_completed}")
                            // JSON 파싱 실패 시 원본 문자열 반환
                            user.resume_completed
                        }
                    } else {
                        Log.d(TAG, "resume_completed field is null for user")
                        null
                    }
                } else {
                    Log.d(TAG, "No user found for identifier: $identifier")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting resume_completed: ${e.message}", e)
            null
        }
    }

    // resume_completed 삭제 함수
    suspend fun deleteResumeCompletedByIdentifier(identifier: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Deleting resume_completed for identifier: $identifier")

                val updateData = buildJsonObject {
                    put("resume_completed", JsonPrimitive(null as String?))
                    put("updated_at", JsonPrimitive(getCurrentTimestampIso8601()))
                }

                val result = if (identifier.matches(Regex("\\d+"))) {
                    // 숫자만 있으면 kakao_id로 업데이트
                    supabase.postgrest["users"]
                        .update(updateData) {
                            eq("kakao_id", identifier)
                        }
                } else {
                    // 그 외에는 user_id로 업데이트
                    supabase.postgrest["users"]
                        .update(updateData) {
                            eq("user_id", identifier)
                        }
                }

                Log.d(TAG, "Resume completed deleted successfully for identifier: $identifier")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting resume_completed: ${e.message}", e)
            false
        }
    }

    /**
     * 일반 사용자의 updated_at만 업데이트하는 함수
     */
    suspend fun updateUserTimestamp(userId: String): User? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "=== 사용자 타임스탬프 업데이트 ===")
                Log.d(TAG, "사용자 ID: $userId")

                val updateData = buildJsonObject {
                    put("updated_at", JsonPrimitive(getCurrentTimestampIso8601()))
                }

                Log.d(TAG, "업데이트 데이터: $updateData")

                // UPDATE 쿼리 실행
                val updateResponse = supabase.postgrest["users"]
                    .update(updateData) {
                        eq("id", userId)
                    }

                Log.d(TAG, "UPDATE 쿼리 실행 완료")

                // 업데이트된 데이터 다시 조회
                val updatedUser = supabase.postgrest["users"]
                    .select(filter = {
                        eq("id", userId)
                    })
                    .decodeSingleOrNull<User>()

                if (updatedUser != null) {
                    Log.d(TAG, "타임스탬프 업데이트 성공")
                    Log.d(TAG, "업데이트된 시간: ${updatedUser.updated_at}")
                } else {
                    Log.e(TAG, "타임스탬프 업데이트 후 사용자 조회 실패")
                }

                updatedUser
            }
        } catch (e: Exception) {
            Log.e(TAG, "타임스탬프 업데이트 실패: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 일반 로그인 시 updated_at 업데이트
     */
    suspend fun updateLoginTimestamp(userId: String): User? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "=== 로그인 타임스탬프 업데이트 ===")
                Log.d(TAG, "user_id: $userId")

                val updateData = buildJsonObject {
                    put("updated_at", JsonPrimitive(getCurrentTimestampIso8601()))
                }

                // user_id로 업데이트
                val updateResponse = supabase.postgrest["users"]
                    .update(updateData) {
                        eq("user_id", userId)
                    }

                Log.d(TAG, "UPDATE 쿼리 실행 완료")

                // 업데이트된 데이터 다시 조회
                val updatedUser = supabase.postgrest["users"]
                    .select(filter = {
                        eq("user_id", userId)
                    })
                    .decodeSingleOrNull<User>()

                if (updatedUser != null) {
                    Log.d(TAG, "로그인 타임스탬프 업데이트 성공")
                    Log.d(TAG, "업데이트된 시간: ${updatedUser.updated_at}")
                } else {
                    Log.e(TAG, "로그인 타임스탬프 업데이트 후 사용자 조회 실패")
                }

                updatedUser
            }
        } catch (e: Exception) {
            Log.e(TAG, "로그인 타임스탬프 업데이트 실패: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }


}