package com.example.comcare

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import java.util.*
import kotlin.math.ceil

// 카카오 로그인 관련 imports
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import android.widget.Toast

// 기존 imports 유지
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp

// 위치 관련 import 추가
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import android.location.Geocoder
import java.util.Locale
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract.CommonDataKinds.Website.URL
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.provider.Settings
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember

import androidx.navigation.NavType
import androidx.navigation.navArgument


import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.lazy.*
import androidx.compose.ui.viewinterop.AndroidView

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.flow.collect

import android.webkit.WebChromeClient
import android.webkit.JsResult
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.*

import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.ui.text.TextStyle

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Row

import coil.compose.AsyncImage
import coil.request.ImageRequest

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.PlayArrow

import android.content.SharedPreferences

import org.json.JSONArray
import org.json.JSONException


// 사용자 정보 데이터 클래스
data class UserInfo(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?
)

class MainActivity : ComponentActivity() {

    lateinit var chatService: ChatService
    var currentUserId: String = "guest"  // private -> public

    // 사용자 정보 저장
    private var userInfo: UserInfo? = null

    // 위치 관련 변수 - public으로 변경
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    var userCity: String = ""  // private -> public
    var userDistrict: String = ""  // private -> public
    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0
    private var user_add: String = ""

    // 추가: 현재 사용자의 kakaoId 저장
    var currentUserKakaoId: String? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "Location"

        // SharedPreferences 키 상수 추가
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_CURRENT_USER_ID = "current_user_id"
        private const val KEY_CURRENT_KAKAO_ID = "current_kakao_id"
        private const val KEY_USER_CITY = "user_city"
        private const val KEY_USER_DISTRICT = "user_district"

        private const val KEY_CHAT_MESSAGES = "chat_messages"
        private const val KEY_CHAT_SESSION_ID = "chat_session_id"
    }

    // SharedPreferences 추가
    private val sharedPrefs: SharedPreferences by lazy {
        getSharedPreferences("AppState", Context.MODE_PRIVATE)
    }

    // saveAppState 메서드를 public으로 추가
    fun saveAppState() {
        val editor = sharedPrefs.edit()

        editor.putBoolean(KEY_IS_LOGGED_IN, userInfo != null)
        editor.putString(KEY_CURRENT_USER_ID, currentUserId)
        editor.putString(KEY_CURRENT_KAKAO_ID, currentUserKakaoId)
        editor.putString(KEY_USER_CITY, userCity)
        editor.putString(KEY_USER_DISTRICT, userDistrict)

        // UserInfo 저장
        userInfo?.let {
            editor.putLong("user_info_id", it.id)
            editor.putString("user_info_nickname", it.nickname)
            editor.putString("user_info_profile_url", it.profileImageUrl)
        }

        editor.apply()

        Log.d("MainActivity", "App state saved")
    }

    // 앱 상태 복원 메서드
    private fun restoreAppState() {
        currentUserId = sharedPrefs.getString(KEY_CURRENT_USER_ID, "guest") ?: "guest"
        currentUserKakaoId = sharedPrefs.getString(KEY_CURRENT_KAKAO_ID, null)
        userCity = sharedPrefs.getString(KEY_USER_CITY, "") ?: ""
        userDistrict = sharedPrefs.getString(KEY_USER_DISTRICT, "") ?: ""

        // UserInfo 복원
        val userId = sharedPrefs.getLong("user_info_id", -1L)
        if (userId != -1L) {
            userInfo = UserInfo(
                id = userId,
                nickname = sharedPrefs.getString("user_info_nickname", "사용자") ?: "사용자",
                profileImageUrl = sharedPrefs.getString("user_info_profile_url", null)
            )
        }

        Log.d("MainActivity", "App state restored - userId: $currentUserId, city: $userCity")
    }

    // 채팅 메시지 저장
    fun saveChatMessages(messages: List<ChatMessage>, sessionId: String) {
        try {
            val jsonArray = JSONArray()
            messages.forEach { message ->
                // isWaiting이 true인 메시지는 저장하지 않음
                if (!message.isWaiting) {
                    jsonArray.put(message.toJson())
                }
            }

            sharedPrefs.edit().apply {
                putString(KEY_CHAT_MESSAGES, jsonArray.toString())
                putString(KEY_CHAT_SESSION_ID, sessionId)
            }.apply()

            Log.d("MainActivity", "Chat messages saved: ${messages.size}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving chat messages", e)
        }
    }

    // 채팅 메시지 복원
    fun loadChatMessages(): Pair<List<ChatMessage>, String> {
        return try {
            val messagesJson = sharedPrefs.getString(KEY_CHAT_MESSAGES, null)
            val sessionId = sharedPrefs.getString(KEY_CHAT_SESSION_ID, null)

            if (messagesJson != null) {
                val jsonArray = JSONArray(messagesJson)
                val messages = mutableListOf<ChatMessage>()

                for (i in 0 until jsonArray.length()) {
                    messages.add(ChatMessage.fromJson(jsonArray.getJSONObject(i)))
                }

                Log.d("MainActivity", "Chat messages loaded: ${messages.size}")
                Pair(messages, sessionId ?: UUID.randomUUID().toString().replace("-", ""))
            } else {
                Pair(emptyList(), UUID.randomUUID().toString().replace("-", ""))
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading chat messages", e)
            Pair(emptyList(), UUID.randomUUID().toString().replace("-", ""))
        }
    }

    // 채팅 메시지 삭제
    fun clearChatMessages() {
        sharedPrefs.edit().apply {
            remove(KEY_CHAT_MESSAGES)
            remove(KEY_CHAT_SESSION_ID)
        }.apply()
    }


    fun onMessageSent(message: String, sessionId: String) {
        chatService.sendChatMessageToWorkflow(
            currentUserId,
            message,
            sessionId,
            userCity,      // 추가: userCity 전달
            userDistrict   // 추가: userDistrict 전달
        )
    }

    class PlaceViewModelFactory(
        private val supabaseHelper: SupabaseDatabaseHelper
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlaceViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PlaceViewModel(supabaseHelper) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 저장된 상태 복원
        restoreAppState()

        chatService = ChatService(this)
        RequestCounterHelper.init(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val supabaseHelper = SupabaseDatabaseHelper(this)

        setContent {

            // 다크모드 확인
            val isDarkTheme = isSystemInDarkTheme()

            // 다크모드 상태 로그
            LaunchedEffect(isDarkTheme) {
                Log.d("ThemeMode", "=== 테마 모드 확인 ===")
                Log.d("ThemeMode", "현재 다크모드 상태: ${if (isDarkTheme) "다크모드" else "라이트모드"}")
                Log.d("ThemeMode", "isDarkTheme = $isDarkTheme")
                Log.d("ThemeMode", "===================")
            }

            // 저장된 상태로 초기화
            var userCityState by remember { mutableStateOf(userCity.ifEmpty { "위치 확인 중..." }) }
            var userDistrictState by remember { mutableStateOf(userDistrict.ifEmpty { "위치 확인 중..." }) }
            var locationPermissionGranted by remember { mutableStateOf(false) }

            // 로그인 상태 관리 - 저장된 상태로 초기화
            var isLoggedIn by remember {
                mutableStateOf(sharedPrefs.getBoolean("is_logged_in", false) && userInfo != null)
            }
            var currentUserInfo by remember { mutableStateOf(userInfo) }

            // 네비게이션 컨트롤러 추가
            val loginNavController = rememberNavController()

            val viewModelFactory = remember { PlaceViewModelFactory(supabaseHelper) }

            // 위치 권한 런처
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                Log.d(TAG, "위치 권한 런처 콜백 실행")
                val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                Log.d(TAG, "Fine location: $fineLocationGranted, Coarse location: $coarseLocationGranted")

                if (fineLocationGranted || coarseLocationGranted) {
                    locationPermissionGranted = true
                    Log.d(TAG, "위치 권한 승인됨")

                    getLastKnownLocation { city, district ->
                        Log.d(TAG, "getLastKnownLocation 콜백 받음 - city: $city, district: $district")

                        // 변수 업데이트
                        userCity = city
                        userDistrict = district
                        userCityState = city
                        userDistrictState = district

                        // 상태 저장
                        saveAppState()

                        Log.d(TAG, "위치 정보 획득 완료 - city: $city, district: $district")
                        Log.d(TAG, "currentUserKakaoId 체크: $currentUserKakaoId")

                        // Supabase에 주소 정보 업데이트
                        if (!currentUserKakaoId.isNullOrEmpty() && city.isNotEmpty() && district.isNotEmpty()) {
                            Log.d(TAG, "Supabase 업데이트 조건 충족")
                            val supabaseHelper = SupabaseDatabaseHelper(this@MainActivity)

                            lifecycleScope.launch {
                                try {
                                    // 주소 형식: "인천광역시 연수구"
                                    val address = "$city $district"
                                    Log.d(TAG, "업데이트할 주소: $address")

                                    val updatedUser = supabaseHelper.upsertUser(
                                        kakaoId = currentUserKakaoId!!,
                                        address = address
                                    )

                                    if (updatedUser != null) {
                                        Log.d(TAG, "위치 권한 승인 후 사용자 주소 업데이트 성공: $address")
                                        Log.d(TAG, "업데이트된 사용자 정보: id=${updatedUser.id}, address=${updatedUser.address}")
                                    } else {
                                        Log.e(TAG, "위치 권한 승인 후 사용자 주소 업데이트 실패 - updatedUser가 null")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "위치 권한 승인 후 사용자 주소 업데이트 중 오류: ${e.message}", e)
                                    e.printStackTrace()
                                }
                            }
                        } else {
                            Log.w(TAG, "업데이트 조건 미충족:")
                            Log.w(TAG, "- currentUserKakaoId: $currentUserKakaoId")
                            Log.w(TAG, "- city: $city")
                            Log.w(TAG, "- district: $district")
                        }
                    }
                } else {
                    locationPermissionGranted = false
                    userCityState = "위치 권한 없음"
                    userDistrictState = "위치 권한 없음"
                    Log.d(TAG, "위치 권한 거부됨")
                    // 위치 권한 거부 시 토스트 메시지 표시
                    Toast.makeText(
                        this@MainActivity,
                        "오비서 앱은 사용자님의 지역에 맞는 정보를 제공하기 위해 위치 권한이 필요합니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // 1. 로그인 후에만 위치 권한 확인
            LaunchedEffect(isLoggedIn) {
                Log.d(TAG, "LaunchedEffect - isLoggedIn: $isLoggedIn")

                if (isLoggedIn) {
                    Log.d(TAG, "로그인 상태 확인됨")
                    Log.d(TAG, "currentUserKakaoId: $currentUserKakaoId")

                    when {
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            Log.d(TAG, "위치 권한이 이미 승인됨")
                            locationPermissionGranted = true

                            getLastKnownLocation { city, district ->
                                userCity = city
                                userDistrict = district
                                userCityState = city
                                userDistrictState = district
                                Log.d(TAG, "위치 정보 획득 - city: $city, district: $district")
                                Log.d(TAG, "currentUserKakaoId 체크: $currentUserKakaoId")

                                // 상태 저장
                                saveAppState()

                                // 이미 위치 권한이 있는 경우에도 Supabase 업데이트
                                if (!currentUserKakaoId.isNullOrEmpty() && city.isNotEmpty() && district.isNotEmpty()) {
                                    Log.d(TAG, "Supabase 업데이트 시작")
                                    val supabaseHelper = SupabaseDatabaseHelper(this@MainActivity)

                                    lifecycleScope.launch {
                                        try {
                                            // 주소 형식: "인천광역시 연수구"
                                            val address = "$city $district"
                                            Log.d(TAG, "업데이트할 주소: $address")

                                            val updatedUser = supabaseHelper.upsertUser(
                                                kakaoId = currentUserKakaoId!!,
                                                address = address
                                            )

                                            if (updatedUser != null) {
                                                Log.d(TAG, "로그인 후 사용자 주소 업데이트 성공: $address")
                                                Log.d(TAG, "업데이트된 사용자 정보: id=${updatedUser.id}, address=${updatedUser.address}")
                                            } else {
                                                Log.e(TAG, "로그인 후 사용자 주소 업데이트 실패 - updatedUser가 null")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "로그인 후 사용자 주소 업데이트 중 오류: ${e.message}", e)
                                            e.printStackTrace()
                                        }
                                    }
                                } else {
                                    Log.w(TAG, "업데이트 조건 미충족:")
                                    Log.w(TAG, "- currentUserKakaoId: $currentUserKakaoId")
                                    Log.w(TAG, "- city: $city")
                                    Log.w(TAG, "- district: $district")
                                }
                            }
                        }
                        else -> {
                            Log.d(TAG, "위치 권한이 없음 - 권한 요청")
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                } else {
                    Log.d(TAG, "아직 로그인하지 않음")
                }
            }

            // 2. currentUserKakaoId가 변경될 때 위치 정보 업데이트
            LaunchedEffect(currentUserKakaoId) {
                Log.d(TAG, "=== LaunchedEffect(currentUserKakaoId) 시작 ===")
                Log.d(TAG, "currentUserKakaoId: '$currentUserKakaoId'")
                Log.d(TAG, "userCity: '$userCity'")
                Log.d(TAG, "userDistrict: '$userDistrict'")

                if (!currentUserKakaoId.isNullOrEmpty()) {
                    Log.d(TAG, "currentUserKakaoId가 설정됨")

                    // 위치 정보가 없으면 위치 정보를 먼저 가져오기
                    if (userCity.isEmpty() || userDistrict.isEmpty() ||
                        userCity == "위치 확인 중..." || userCity == "위치 권한 없음") {

                        Log.d(TAG, "위치 정보가 없음 - 위치 정보 가져오기 시도")

                        // 위치 권한이 있는지 확인
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED) {

                            Log.d(TAG, "위치 권한 있음 - getLastKnownLocation 호출")
                            getLastKnownLocation { city, district ->
                                Log.d(TAG, "getLastKnownLocation 콜백 - city: '$city', district: '$district'")
                                userCity = city
                                userDistrict = district
                                userCityState = city
                                userDistrictState = district

                                // 상태 저장
                                saveAppState()

                                // 위치 정보를 가져온 후 Supabase 업데이트
                                if (city.isNotEmpty() && district.isNotEmpty()) {
                                    Log.d(TAG, "위치 정보 획득 성공 - Supabase 업데이트 시작")
                                    val supabaseHelper = SupabaseDatabaseHelper(this@MainActivity)
                                    lifecycleScope.launch {
                                        try {
                                            val address = "$city $district"
                                            Log.d(TAG, "Supabase에 업데이트할 주소: '$address'")

                                            val updatedUser = supabaseHelper.upsertUser(
                                                kakaoId = currentUserKakaoId!!,
                                                address = address
                                            )

                                            if (updatedUser != null) {
                                                Log.d(TAG, "위치 정보 업데이트 성공: ${updatedUser.address}")
                                            } else {
                                                Log.e(TAG, "위치 정보 업데이트 실패 - updatedUser가 null")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "위치 정보 업데이트 중 오류: ${e.message}", e)
                                        }
                                    }
                                }
                            }
                        } else {
                            Log.d(TAG, "위치 권한 없음")
                        }
                    } else {
                        // 이미 위치 정보가 있는 경우
                        Log.d(TAG, "위치 정보 이미 있음 - 바로 Supabase 업데이트")
                        val supabaseHelper = SupabaseDatabaseHelper(this@MainActivity)
                        lifecycleScope.launch {
                            try {
                                val address = "$userCity $userDistrict"
                                Log.d(TAG, "Supabase에 업데이트할 주소: '$address'")

                                val updatedUser = supabaseHelper.upsertUser(
                                    kakaoId = currentUserKakaoId!!,
                                    address = address
                                )

                                if (updatedUser != null) {
                                    Log.d(TAG, "위치 정보 업데이트 성공: ${updatedUser.address}")
                                } else {
                                    Log.e(TAG, "위치 정보 업데이트 실패 - updatedUser가 null")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "위치 정보 업데이트 중 오류: ${e.message}", e)
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "currentUserKakaoId가 null 또는 empty")
                }

                Log.d(TAG, "=== LaunchedEffect(currentUserKakaoId) 종료 ===")
            }

            // 3. 위치 정보가 변경될 때 Supabase 업데이트 (새로 추가)
            LaunchedEffect(userCity, userDistrict, currentUserKakaoId) {
                Log.d(TAG, "=== LaunchedEffect(위치 정보 변경) 시작 ===")
                Log.d(TAG, "userCity: '$userCity'")
                Log.d(TAG, "userDistrict: '$userDistrict'")
                Log.d(TAG, "currentUserKakaoId: '$currentUserKakaoId'")

                // 모든 조건이 충족되었을 때만 업데이트
                if (!currentUserKakaoId.isNullOrEmpty() &&
                    userCity.isNotEmpty() &&
                    userDistrict.isNotEmpty() &&
                    userCity != "위치 확인 중..." &&
                    userCity != "위치 권한 없음") {

                    Log.d(TAG, "모든 조건 충족 - Supabase 업데이트 시작")

                    val supabaseHelper = SupabaseDatabaseHelper(this@MainActivity)
                    lifecycleScope.launch {
                        try {
                            val address = "$userCity $userDistrict"
                            Log.d(TAG, "Supabase에 업데이트할 주소: '$address'")

                            val updatedUser = supabaseHelper.upsertUser(
                                kakaoId = currentUserKakaoId!!,
                                address = address
                            )

                            if (updatedUser != null) {
                                Log.d(TAG, "위치 정보 업데이트 성공: ${updatedUser.address}")
                                Log.d(TAG, "업데이트된 사용자 전체 정보: $updatedUser")
                            } else {
                                Log.e(TAG, "위치 정보 업데이트 실패 - updatedUser가 null")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "위치 정보 업데이트 중 오류: ${e.message}", e)
                            e.printStackTrace()
                        }
                    }
                } else {
                    Log.d(TAG, "업데이트 조건 미충족:")
                    Log.d(TAG, "- currentUserKakaoId 비어있음: ${currentUserKakaoId.isNullOrEmpty()}")
                    Log.d(TAG, "- userCity 비어있음: ${userCity.isEmpty()}")
                    Log.d(TAG, "- userDistrict 비어있음: ${userDistrict.isEmpty()}")
                    Log.d(TAG, "- userCity가 '위치 확인 중...': ${userCity == "위치 확인 중..."}")
                    Log.d(TAG, "- userCity가 '위치 권한 없음': ${userCity == "위치 권한 없음"}")
                }

                Log.d(TAG, "=== LaunchedEffect(위치 정보 변경) 종료 ===")
            }

            PlaceComparisonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isLoggedIn) {
                        // 로그인 네비게이션
                        val loginNavController = rememberNavController()

                        NavHost(
                            navController = loginNavController,
                            startDestination = "login"
                        ) {
                            composable("login") {
                                LoginScreen(
                                    onLoginSuccess = { userInfo ->
                                        currentUserInfo = userInfo
                                        currentUserId = userInfo.id.toString()
                                        currentUserKakaoId = if (userInfo.id > 0) userInfo.id.toString() else null
                                        this@MainActivity.userInfo = userInfo
                                        isLoggedIn = true

                                        // 상태 저장
                                        saveAppState()
                                    },
                                    onNavigateToNormalLogin = {
                                        loginNavController.navigate("normalLogin")
                                    }
                                )
                            }

                            composable("normalLogin") {
                                NormalLoginScreen(
                                    navController = loginNavController,
                                    onLoginSuccess = { userInfo ->
                                        currentUserInfo = userInfo
                                        // currentUserId는 NormalLoginScreen 내부에서 이미 설정됨
                                        // currentUserId = userInfo.id.toString()  // ← 이 줄 제거!
                                        this@MainActivity.userInfo = userInfo
                                        isLoggedIn = true

                                        // 상태 저장
                                        saveAppState()

                                        Log.d("MainActivity", "Normal login successful")
                                        Log.d("MainActivity", "currentUserId: $currentUserId")
                                        Log.d("MainActivity", "currentUserKakaoId: $currentUserKakaoId")
                                    }
                                )
                            }

                            composable("signup") {
                                SignUpScreen(
                                    navController = loginNavController,
                                    onSignUpSuccess = { userInfo ->
                                        currentUserInfo = userInfo
                                        currentUserId = userInfo.id.toString()
                                        this@MainActivity.userInfo = userInfo
                                        isLoggedIn = true

                                        // 상태 저장
                                        saveAppState()
                                    }
                                )
                            }
                        }
                    } else {

                        // 로그인 후 메인 화면 표시
                        val navController = rememberNavController()
                        val viewModelFactory = remember { PlaceViewModelFactory(supabaseHelper) }
                        val viewModel: PlaceViewModel = viewModel(factory = viewModelFactory)

                        LaunchedEffect(userCityState, userDistrictState) {
                            if (userCityState != "위치 확인 중..." &&
                                userCityState != "위치 권한 없음" &&
                                userCityState.isNotEmpty() &&
                                userDistrictState.isNotEmpty()) {
                                viewModel.setUserLocation(userCityState, userDistrictState)
                                Log.d(TAG, "ViewModel에 위치 정보 설정: $userCityState $userDistrictState")
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = "chat"
                        ) {
                            composable(
                                "home/{section}",
                                arguments = listOf(
                                    navArgument("section") {
                                        type = NavType.StringType
                                        defaultValue = "home"
                                    }
                                )
                            ) { backStackEntry ->
                                val startSection = backStackEntry.arguments?.getString("section") ?: "home"
                                PlaceComparisonApp(
                                    navController = navController,
                                    viewModel = viewModel,
                                    userCity = userCityState,
                                    userDistrict = userDistrictState,
                                    userInfo = currentUserInfo,
                                    startSection = startSection  // 새로 추가
                                )
                            }

                            composable("home") {
                                PlaceComparisonApp(
                                    navController = navController,
                                    viewModel = viewModel,
                                    userCity = userCityState,
                                    userDistrict = userDistrictState,
                                    userInfo = currentUserInfo,
                                    startSection = "home"
                                )
                            }

                            composable("searchResults") {
                                SearchResultsScreen(
                                    viewModel = viewModel,
                                    navController = navController
                                )
                            }

                            composable(
                                "jobSearchResults?returnSection={returnSection}",
                                arguments = listOf(
                                    navArgument("returnSection") {
                                        type = NavType.StringType
                                        defaultValue = "jobs"
                                    }
                                )
                            ) { backStackEntry ->
                                val returnSection = backStackEntry.arguments?.getString("returnSection") ?: "jobs"
                                JobSearchResultsScreen(
                                    viewModel = viewModel,
                                    navController = navController,
                                    returnSection = returnSection
                                )
                            }
                            // 기존 route도 유지 (호환성)
                            composable("jobSearchResults") {
                                JobSearchResultsScreen(
                                    viewModel = viewModel,
                                    navController = navController,
                                    returnSection = "jobs"
                                )
                            }
                            composable(
                                "cultureSearchResults?returnSection={returnSection}",
                                arguments = listOf(
                                    navArgument("returnSection") {
                                        type = NavType.StringType
                                        defaultValue = "culture"
                                    }
                                )
                            ) { backStackEntry ->
                                val returnSection = backStackEntry.arguments?.getString("returnSection") ?: "culture"
                                CultureSearchResultsScreen(
                                    viewModel = viewModel,
                                    navController = navController,
                                    returnSection = returnSection
                                )
                            }

                            composable("chat") {
                                ChatScreen(
                                    activity = this@MainActivity,
                                    navController = navController,
                                    showBackButton = false,
                                    userCity = userCityState,
                                    userDistrict = userDistrictState,
                                    userInfo = currentUserInfo
                                )
                            }
                            composable(
                                "webview/{url}",
                                arguments = listOf(navArgument("url") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                                val decodedUrl = Uri.decode(encodedUrl)
                                WebViewScreen(
                                    url = decodedUrl,
                                    navController = navController
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Activity 생명주기 메서드들
    override fun onPause() {
        super.onPause()
        // 앱이 백그라운드로 갈 때 상태 저장
        saveAppState()
    }

    override fun onResume() {
        super.onResume()
        // 앱이 포그라운드로 돌아올 때 ChatService 재연결
        if (::chatService.isInitialized) {
            chatService.restoreNavigationState()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // singleTop으로 인해 새 인텐트가 올 때
        Log.d("MainActivity", "onNewIntent called")
    }

    // 위치 정보를 가져오는 함수들은 그대로 유지
    fun getLastKnownLocation(callback: (String, String) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "위치 권한이 없습니다.")
            callback("", "")
            return
        }

        Log.d(TAG, "마지막 위치 정보 요청 중...")
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                userLatitude = location.latitude
                userLongitude = location.longitude

                Log.d(TAG, "위치 정보 획득 성공 - 위도: $userLatitude, 경도: $userLongitude")

                getAddressFromLocation(location.latitude, location.longitude) { city, district ->
                    callback(city, district)

                    // 위치 정보가 업데이트되고 로그인된 상태라면 Supabase에 업데이트
                    if (currentUserKakaoId != null && city.isNotEmpty() && district.isNotEmpty()) {
                        val supabaseHelper = SupabaseDatabaseHelper(this)
                        lifecycleScope.launch {
                            try {
                                val updatedUser = supabaseHelper.upsertUser(
                                    kakaoId = currentUserKakaoId!!,
                                    address = "$city $district"
                                )
                                if (updatedUser != null) {
                                    Log.d(TAG, "사용자 위치 정보 업데이트 성공")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "사용자 위치 정보 업데이트 실패: ${e.message}")
                            }
                        }
                    }
                }
            } else {
                Log.d(TAG, "마지막 위치 정보가 없음 - 새로운 위치 요청")
                requestNewLocationData(callback)
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "위치 정보 획득 실패: ${exception.message}")
            callback("", "")
        }
    }

    private fun requestNewLocationData(callback: (String, String) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L
        ).apply {
            setMinUpdateIntervalMillis(5000L)
            setMaxUpdates(1)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    userLatitude = location.latitude
                    userLongitude = location.longitude

                    Log.d(TAG, "새로운 위치 정보 획득 - 위도: $userLatitude, 경도: $userLongitude")

                    getAddressFromLocation(location.latitude, location.longitude) { city, district ->
                        callback(city, district)

                        // 위치 정보가 업데이트되고 로그인된 상태라면 Supabase에 업데이트
                        if (currentUserKakaoId != null && city.isNotEmpty() && district.isNotEmpty()) {
                            val supabaseHelper = SupabaseDatabaseHelper(this@MainActivity)
                            lifecycleScope.launch {
                                try {
                                    val updatedUser = supabaseHelper.upsertUser(
                                        kakaoId = currentUserKakaoId!!,
                                        address = "$city $district"
                                    )
                                    if (updatedUser != null) {
                                        Log.d(TAG, "사용자 위치 정보 업데이트 성공")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "사용자 위치 정보 업데이트 실패: ${e.message}")
                                }
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "새로운 위치 정보를 가져올 수 없습니다.")
                    callback("", "")
                }
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double, callback: (String, String) -> Unit) {
        try {
            val geocoder = Geocoder(this, Locale.KOREAN)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val city = address.adminArea ?: ""
                        val district = address.subLocality ?: address.locality ?: ""
                        val fullAddress = address.getAddressLine(0) ?: ""
                        user_add = fullAddress

                        Log.d(TAG, "========== 위치 정보 ==========")
                        Log.d(TAG, "전체 주소 (user_add): $user_add")
                        Log.d(TAG, "시/도: $city")
                        Log.d(TAG, "구/군: $district")
                        Log.d(TAG, "currentUserKakaoId: $currentUserKakaoId")
                        Log.d(TAG, "==============================")

                        // 메인 스레드에서 콜백 실행 - 이것이 중요!
                        runOnUiThread {
                            Log.d(TAG, "메인 스레드에서 콜백 실행 - city: $city, district: $district")
                            callback(city, district)
                        }

                        // 위치 정보 획득 직후 Supabase 업데이트도 메인 스레드에서
                        if (!currentUserKakaoId.isNullOrEmpty() && city.isNotEmpty() && district.isNotEmpty()) {
                            Log.d(TAG, "getAddressFromLocation에서 Supabase 업데이트 시작")

                            runOnUiThread {
                                val supabaseHelper = SupabaseDatabaseHelper(this@MainActivity)

                                lifecycleScope.launch {
                                    try {
                                        val addressForDB = "$city $district"
                                        Log.d(TAG, "DB에 저장할 주소: $addressForDB")

                                        val updatedUser = supabaseHelper.upsertUser(
                                            kakaoId = currentUserKakaoId!!,
                                            address = addressForDB
                                        )

                                        if (updatedUser != null) {
                                            Log.d(TAG, "getAddressFromLocation에서 주소 업데이트 성공")
                                            Log.d(TAG, "저장된 주소: ${updatedUser.address}")
                                        } else {
                                            Log.e(TAG, "getAddressFromLocation에서 주소 업데이트 실패")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "getAddressFromLocation 주소 업데이트 오류: ${e.message}", e)
                                    }
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "주소를 찾을 수 없습니다.")
                        user_add = ""
                        runOnUiThread {
                            callback("", "")
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val city = address.adminArea ?: ""
                    val district = address.subLocality ?: address.locality ?: ""
                    val fullAddress = address.getAddressLine(0) ?: ""
                    user_add = fullAddress

                    Log.d(TAG, "========== 위치 정보 ==========")
                    Log.d(TAG, "시/도: $city")
                    Log.d(TAG, "구/군: $district")
                    Log.d(TAG, "currentUserKakaoId: $currentUserKakaoId")
                    Log.d(TAG, "==============================")

                    // 메인 스레드에서 콜백 실행
                    Log.d(TAG, "메인 스레드에서 콜백 실행 - city: $city, district: $district")
                    callback(city, district)

                    // 위치 정보 획득 직후 Supabase 업데이트
                    if (!currentUserKakaoId.isNullOrEmpty() && city.isNotEmpty() && district.isNotEmpty()) {
                        Log.d(TAG, "getAddressFromLocation에서 Supabase 업데이트 시작")
                        val supabaseHelper = SupabaseDatabaseHelper(this@MainActivity)

                        lifecycleScope.launch {
                            try {
                                val addressForDB = "$city $district"
                                Log.d(TAG, "DB에 저장할 주소: $addressForDB")

                                val updatedUser = supabaseHelper.upsertUser(
                                    kakaoId = currentUserKakaoId!!,
                                    address = addressForDB
                                )

                                if (updatedUser != null) {
                                    Log.d(TAG, "getAddressFromLocation에서 주소 업데이트 성공")
                                    Log.d(TAG, "저장된 주소: ${updatedUser.address}")
                                } else {
                                    Log.e(TAG, "getAddressFromLocation에서 주소 업데이트 실패")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "getAddressFromLocation 주소 업데이트 오류: ${e.message}", e)
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "주소를 찾을 수 없습니다.")
                    user_add = ""
                    callback("", "")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding 실패: ${e.message}", e)
            user_add = ""
            callback("", "")
        }
    }



    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 추가적인 상태 저장이 필요한 경우
        outState.putBoolean(KEY_IS_LOGGED_IN, userInfo != null)
        outState.putString(KEY_CURRENT_USER_ID, currentUserId)
        outState.putString(KEY_CURRENT_KAKAO_ID, currentUserKakaoId)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // 시스템에 의해 앱이 종료되었다가 재시작될 때
        currentUserId = savedInstanceState.getString(KEY_CURRENT_USER_ID, "guest") ?: "guest"
        currentUserKakaoId = savedInstanceState.getString(KEY_CURRENT_KAKAO_ID)
    }
}


@Composable
fun LoginScreen(
    onLoginSuccess: (UserInfo) -> Unit,
    onNavigateToNormalLogin: () -> Unit  // navController 대신 콜백 사용
) {
    val context = LocalContext.current
    val TAG = "KakaoLogin"

    // Supabase 헬퍼 인스턴스 생성
    val supabaseHelper = remember { SupabaseDatabaseHelper(context) }
    val coroutineScope = rememberCoroutineScope()

    // 카카오 로그인 콜백
    val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
        if (error != null) {
            Log.e(TAG, "카카오 로그인 실패", error)
            Toast.makeText(context, "로그인 실패: ${error.message}", Toast.LENGTH_SHORT).show()
        } else if (token != null) {
            Log.i(TAG, "카카오 로그인 성공 ${token.accessToken}")

            // 사용자 정보 가져오기
            UserApiClient.instance.me { user, error ->
                if (error != null) {
                    Log.e(TAG, "사용자 정보 요청 실패", error)
                    Toast.makeText(context, "사용자 정보 요청 실패", Toast.LENGTH_SHORT).show()
                } else if (user != null) {
                    Log.i(TAG, "사용자 정보 요청 성공" +
                            "\n회원번호: ${user.id}" +
                            "\n닉네임: ${user.kakaoAccount?.profile?.nickname}" +
                            "\n프로필사진: ${user.kakaoAccount?.profile?.thumbnailImageUrl}")

                    val userInfo = UserInfo(
                        id = user.id ?: 0L,
                        nickname = user.kakaoAccount?.profile?.nickname ?: "사용자",
                        profileImageUrl = user.kakaoAccount?.profile?.thumbnailImageUrl
                    )


                    // Supabase에 사용자 정보 저장/업데이트 부분

                    coroutineScope.launch {
                        try {
                            Log.d(TAG, "=== LoginScreen Supabase 저장 시작 ===")

                            // MainActivity의 currentUserKakaoId를 먼저 설정
                            val mainActivity = context as? MainActivity
                            if (mainActivity != null) {
                                mainActivity.currentUserKakaoId = userInfo.id.toString()
                                Log.d(TAG, "MainActivity.currentUserKakaoId 설정 완료: ${userInfo.id}")

                                // 상태 저장 추가
                                mainActivity.saveAppState()
                                // 현재 위치 정보 확인
                                Log.d(TAG, "현재 위치 정보 - city: '${mainActivity.userCity}', district: '${mainActivity.userDistrict}'")

                                var userAddress: String? = null

                                // 위치 정보가 있으면 주소 생성
                                if (mainActivity.userCity.isNotEmpty() &&
                                    mainActivity.userDistrict.isNotEmpty() &&
                                    mainActivity.userCity != "위치 확인 중..." &&
                                    mainActivity.userCity != "위치 권한 없음") {
                                    userAddress = "${mainActivity.userCity} ${mainActivity.userDistrict}"
                                    Log.d(TAG, "저장할 주소: '$userAddress'")
                                } else {
                                    Log.d(TAG, "위치 정보가 아직 없음")
                                }

                                // Supabase에 사용자 정보 저장/업데이트 (주소는 null일 수 있음)
                                val savedUser = supabaseHelper.upsertUser(
                                    kakaoId = userInfo.id.toString(),
                                    address = userAddress  // null일 수 있음
                                )

                                if (savedUser != null) {
                                    Log.d(TAG, "Supabase에 사용자 정보 저장 성공: ${savedUser.id}")
                                    Log.d(TAG, "저장된 주소: ${savedUser.address}")
                                } else {
                                    Log.e(TAG, "Supabase에 사용자 정보 저장 실패")
                                }
                            }

                            // 로그인 성공 처리
                            onLoginSuccess(userInfo)


                        } catch (e: Exception) {
                            Log.e(TAG, "사용자 정보 저장 중 오류: ${e.message}", e)
                            // 오류가 발생해도 로그인은 진행
                            onLoginSuccess(userInfo)
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFc6f584)), // 앱의 메인 컬러 사용
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // 앱 로고나 타이틀
            Text(
                text = "오비서",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "시니어를 위한 AI 검색 서비스",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 48.dp)
            )


            // 카카오 로그인 버튼
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable {
                        // 카카오톡 설치 여부 확인
                        if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
                            // 카카오톡으로 로그인
                            UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                                if (error != null) {
                                    Log.e(TAG, "카카오톡으로 로그인 실패", error)

                                    // 사용자가 카카오톡 설치 후 로그인을 취소한 경우
                                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                                        return@loginWithKakaoTalk
                                    }

                                    // 카카오톡에 연결된 카카오계정이 없는 경우, 카카오계정으로 로그인 시도
                                    UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
                                } else if (token != null) {
                                    callback(token, null)
                                }
                            }
                        } else {
                            // 카카오톡이 설치되어 있지 않은 경우, 카카오계정으로 로그인
                            UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFEE500) // 카카오 노란색
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 카카오 로고 (실제로는 이미지 리소스를 사용해야 함)
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Kakao",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "카카오 로그인",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 둘러보기 버튼 추가
//            Text(
//                text = "둘러보기",
//                style = MaterialTheme.typography.titleMedium,
//                fontWeight = FontWeight.Bold,
//                color = Color.Gray,
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .clickable {
//                        // 게스트 사용자 정보 생성
//                        val guestUserInfo = UserInfo(
//                            id = -1L, // 게스트 사용자를 나타내는 특별한 ID
//                            nickname = "게스트",
//                            profileImageUrl = null
//                        )
//
//                        // MainActivity의 currentUserId를 게스트로 설정
//                        val mainActivity = context as? MainActivity
//                        mainActivity?.currentUserId = "guest"
//                        mainActivity?.currentUserKakaoId = null // 게스트는 카카오 ID가 없음
//
//                        // 로그인 성공 콜백 호출
//                        onLoginSuccess(guestUserInfo)
//                    }
//                    .padding(vertical = 16.dp),
//                textAlign = TextAlign.Center
//            )
//
//            Spacer(modifier = Modifier.height(16.dp))

            // 일반 로그인 버튼 추가
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable {
                        onNavigateToNormalLogin()  // 콜백 호출
                    },
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(1.dp, Color.Gray)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Login,
                        contentDescription = "Login",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "로그인",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "제공되는 정보는 \n 공개된 정부 웹사이트에서 수집한 것으로 \n 공식 정부 서비스가 아닙니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun NormalLoginScreen(
    navController: NavController,
    onLoginSuccess: (UserInfo) -> Unit
) {
    val context = LocalContext.current
    val supabaseHelper = remember { SupabaseDatabaseHelper(context) }
    val coroutineScope = rememberCoroutineScope()

    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFc6f584)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 상단 타이틀
            Text(
                text = "로그인",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            // 아이디 입력
            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                label = {
                    Text(
                        "아이디",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black.copy(alpha = 0.7f),
                    cursorColor = Color.Black
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 비밀번호 입력
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = {
                    Text(
                        "비밀번호",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                ),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black.copy(alpha = 0.7f),
                    cursorColor = Color.Black
                )
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 로그인 버튼
            Button(
                onClick = {
                    if (userId.isNotEmpty() && password.isNotEmpty()) {
                        coroutineScope.launch {
                            try {
                                // Supabase에서 사용자 확인
                                val user = supabaseHelper.loginUser(userId, password)
                                if (user != null) {
                                    // 로그인 성공
                                    val userInfo = UserInfo(
                                        id = user.id?.hashCode()?.toLong() ?: -1L,  // ID는 고유값으로
                                        nickname = user.user_id ?: "사용자",
                                        profileImageUrl = null
                                    )

                                    // MainActivity의 currentUserId 설정
                                    val mainActivity = context as? MainActivity
                                    mainActivity?.currentUserId = user.user_id ?: ""  // ← 중요!
                                    mainActivity?.currentUserKakaoId = null


                                    // 상태 저장 추가
                                    mainActivity?.saveAppState()

                                    Log.d("NormalLogin", "Login successful - currentUserId set to: ${user.user_id}")

                                    onLoginSuccess(userInfo)
                                } else {
                                    Toast.makeText(context, "아이디 또는 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "로그인 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "로그인",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 가입하기 버튼
            TextButton(
                onClick = { navController.navigate("signup") }
            ) {
                Text(
                    "계정이 없으신가요? 가입하기",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Black
                )
            }
        }
    }
}
@Composable
fun SignUpScreen(
    navController: NavController,
    onSignUpSuccess: (UserInfo) -> Unit
) {
    val context = LocalContext.current
    val supabaseHelper = remember { SupabaseDatabaseHelper(context) }
    val coroutineScope = rememberCoroutineScope()

    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // 추가 상태 변수
    var isCheckingDuplicate by remember { mutableStateOf(false) }
    var isUserIdAvailable by remember { mutableStateOf<Boolean?>(null) }
    var userIdChecked by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFc6f584))
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Spacer(modifier = Modifier.height(40.dp))

            // 상단 타이틀
            Text(
                text = "회원가입",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            // 아이디 입력 및 중복 확인
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = userId,
                    onValueChange = {
                        userId = it
                        // 아이디가 변경되면 중복 확인 상태 초기화
                        isUserIdAvailable = null
                        userIdChecked = false
                    },
                    label = {
                        Text(
                            "아이디",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = when {
                            isUserIdAvailable == true -> Color.Green
                            isUserIdAvailable == false -> Color.Red
                            else -> Color.Black
                        },
                        unfocusedBorderColor = when {
                            isUserIdAvailable == true -> Color.Green
                            isUserIdAvailable == false -> Color.Red
                            else -> Color.Gray
                        },
                        focusedLabelColor = Color.Black,
                        unfocusedLabelColor = Color.Black.copy(alpha = 0.7f),
                        cursorColor = Color.Black
                    )
                )

                Button(
                    onClick = {
                        if (userId.isNotEmpty()) {
                            isCheckingDuplicate = true
                            coroutineScope.launch {
                                isUserIdAvailable = supabaseHelper.isUserIdAvailable(userId)
                                userIdChecked = true
                                isCheckingDuplicate = false

                                if (isUserIdAvailable == true) {
                                    Toast.makeText(context, "사용 가능한 아이디입니다.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "이미 사용중인 아이디입니다.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.height(56.dp),
                    enabled = !isCheckingDuplicate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.DarkGray,
                        contentColor = Color.White
                    )
                ) {
                    if (isCheckingDuplicate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    } else {
                        Text(
                            "중복확인",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 아이디 상태 메시지
            if (isUserIdAvailable != null && userIdChecked) {
                Text(
                    text = if (isUserIdAvailable == true) "✓ 사용 가능한 아이디입니다" else "✗ 이미 사용중인 아이디입니다",
                    color = if (isUserIdAvailable == true) Color.Green else Color.Red,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 비밀번호 입력
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = {
                    Text(
                        "비밀번호",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                ),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black.copy(alpha = 0.7f),
                    cursorColor = Color.Black
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 생년월일 입력
            OutlinedTextField(
                value = birthDate,
                onValueChange = {
                    // 숫자만 입력 가능하도록 필터링
                    if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                        birthDate = it
                    }
                },
                label = {
                    Text(
                        "생년월일 (YYYYMMDD)",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                placeholder = {
                    Text(
                        "19700101",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black.copy(alpha = 0.7f),
                    cursorColor = Color.Black,
                    focusedPlaceholderColor = Color.Gray,
                    unfocusedPlaceholderColor = Color.Gray
                ),
                supportingText = {
                    Text(
                        "예: 19900315 (1990년 3월 15일)",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 핸드폰 번호 입력
            // 핸드폰 번호 입력
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = {
                    // 숫자만 입력 가능 (하이픈 제거)
                    if (it.all { char -> char.isDigit() }) {
                        phoneNumber = it
                    }
                },
                label = {
                    Text(
                        "핸드폰 번호",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                placeholder = {
                    Text(
                        "01012345678",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), // Phone에서 Number로 변경
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black.copy(alpha = 0.7f),
                    cursorColor = Color.Black,
                    focusedPlaceholderColor = Color.Gray,
                    unfocusedPlaceholderColor = Color.Gray
                ),
                supportingText = {
                    Text(
                        "010으로 시작하는 11자리 숫자를 입력해주세요",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 가입하기 버튼
            Button(
                onClick = {
                    when {
                        userId.isEmpty() || password.isEmpty() || birthDate.isEmpty() || phoneNumber.isEmpty() -> {
                            Toast.makeText(context, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
                        }
                        !userIdChecked || isUserIdAvailable != true -> {
                            Toast.makeText(context, "아이디 중복 확인을 해주세요.", Toast.LENGTH_SHORT).show()
                        }
                        !supabaseHelper.isValidBirthDate(birthDate) -> {
                            Toast.makeText(
                                context,
                                "올바른 생년월일 형식이 아닙니다.\nYYYYMMDD 형식으로 입력해주세요.\n예: 19900315",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        // 가입하기 버튼 onClick 내부
                        !supabaseHelper.isValidPhoneNumber(phoneNumber) -> {
                            Toast.makeText(
                                context,
                                "올바른 전화번호 형식이 아닙니다.\n010으로 시작하는 11자리 숫자를 입력해주세요.",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        else -> {
                            // 모든 검증 통과 - 회원가입 진행
                            coroutineScope.launch {
                                try {
                                    val newUser = supabaseHelper.registerUser(
                                        userId = userId,
                                        password = password,
                                        birthDate = birthDate,
                                        phoneNumber = phoneNumber
                                    )

                                    if (newUser != null) {
                                        Toast.makeText(context, "회원가입이 완료되었습니다.", Toast.LENGTH_SHORT).show()

                                        // 자동 로그인
                                        val userInfo = UserInfo(
                                            id = newUser.id?.hashCode()?.toLong() ?: -1L,
                                            nickname = newUser.user_id ?: "사용자",
                                            profileImageUrl = null
                                        )

                                        // MainActivity의 currentUserId를 user_id로 설정
                                        val mainActivity = context as? MainActivity
                                        mainActivity?.currentUserId = newUser.user_id ?: ""
                                        mainActivity?.currentUserKakaoId = null

                                        Log.d("SignUp", "Registration successful - currentUserId set to: ${newUser.user_id}")

                                        onSignUpSuccess(userInfo)
                                    } else {
                                        Toast.makeText(context, "회원가입에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("SignUp", "Registration error: ${e.message}", e)
                                    Toast.makeText(context, "회원가입 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "가입하기",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 뒤로가기 버튼
            TextButton(
                onClick = { navController.navigateUp() }
            ) {
                Text(
                    "이미 계정이 있으신가요? 로그인",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceComparisonApp(
    navController: NavController,
    viewModel: PlaceViewModel,
    userCity: String = "",  // 파라미터 추가
    userDistrict: String = "",  // 파라미터 추가
    userInfo: UserInfo? = null,
    startSection: String = "home"
) {
    Log.d("PlaceComparisonApp", "받은 위치 정보: City=$userCity, District=$userDistrict")
    var currentSection by remember { mutableStateOf(startSection) }  // startSection으로 초기화
    var selectedCity by remember { mutableStateOf("전체") }
    var selectedDistrict by remember { mutableStateOf("전체") }
    var selectedServiceCategory by remember { mutableStateOf("전체") }
    var selectedServiceSubcategory by remember { mutableStateOf("전체") }

    var expandedCityMenu by remember { mutableStateOf(false) }
    var expandedDistrictMenu by remember { mutableStateOf(false) }


    var expandedServiceMenu by remember { mutableStateOf(false) }
    var expandedServiceSubcategoryMenu by remember { mutableStateOf(false) }

    var showFilters by remember { mutableStateOf(false) }

    // 사용자 메뉴 표시 상태
    var showUserMenu by remember { mutableStateOf(false) }

    var showLocationDialog by remember { mutableStateOf(false) }

    // Get available districts for the selected city
    val availableDistricts = remember(selectedCity) {
        viewModel.districts.value[selectedCity] ?: listOf("전체")
    }

    // Get available service subcategories for the selected category
    val availableServiceSubcategories = remember(selectedServiceCategory) {
        viewModel.serviceSubcategories.value[selectedServiceCategory] ?: listOf("전체")
    }

    // 사용자 위치 또는 기본 위치 결정
    val effectiveCity = remember(userCity) {
        if (userCity.isEmpty() || userCity == "위치 확인 중..." || userCity == "위치 권한 없음") {
            "서울특별시"
        } else {
            userCity
        }
    }

    val effectiveDistrict = remember(userDistrict) {
        if (userDistrict.isEmpty() || userDistrict == "위치 확인 중..." || userDistrict == "위치 권한 없음") {
            "강북구"
        } else {
            userDistrict
        }
    }

    // 필요한 도시의 데이터만 로드
    LaunchedEffect(effectiveCity) {
        when (effectiveCity) {
            "서울특별시" -> {
                viewModel.fetchSeoulData() // 서울 데이터만 로드
            }
            "경기도" -> {
                viewModel.fetchGyeonggiData() // 경기도 데이터만 로드
            }
            "인천광역시" -> {
                viewModel.fetchIncheonData() // 인천 데이터만 로드
            }
            "부산광역시" -> {
                viewModel.fetchBusanData() // 부산 데이터만 로드
            }
            "경상북도" -> {
                viewModel.fetchGyeongbukData() // 경북 데이터만 로드
            }
        }
    }

    // 사용자 위치 설정
    LaunchedEffect(userCity, userDistrict) {
        if (userCity.isNotEmpty() && userDistrict.isNotEmpty() &&
            userCity != "위치 확인 중..." && userCity != "위치 권한 없음") {
            viewModel.setUserLocation(userCity, userDistrict)
        } else {
            // 기본 위치 설정
            viewModel.setUserLocation("서울특별시", "강북구")
        }
    }

    // Reset district when city changes
    LaunchedEffect(selectedCity) {
        selectedDistrict = "전체"
        viewModel.filterPlaces(selectedCity, selectedDistrict, selectedServiceCategory, selectedServiceSubcategory)
    }

    // Reset subcategory when service category changes
    LaunchedEffect(selectedServiceCategory) {
        selectedServiceSubcategory = "전체"
        viewModel.filterPlaces(selectedCity, selectedDistrict, selectedServiceCategory, selectedServiceSubcategory)
    }

    // Initialize filtering
    LaunchedEffect(Unit) {
        viewModel.filterPlaces(selectedCity, selectedDistrict, selectedServiceCategory, selectedServiceSubcategory)
    }

    val places = viewModel.filteredPlaces.value

    val userCity = viewModel.getUserCity()
    val userDistrict = viewModel.getUserDistrict()

    Column(modifier = Modifier.fillMaxSize()) {
        // App Bar with rounded corners
        // App Bar with Silverland card - changed color to #cacdca
//        Box(
//            modifier = Modifier
//                .padding(horizontal = 0.dp, vertical = 0.dp)  // Removed spacing
//                .fillMaxWidth()
//        ) {
//            Card(
//                modifier = Modifier.fillMaxWidth(),
//                shape = RectangleShape,  // Made rectangular
//                colors = CardDefaults.cardColors(
//                    containerColor = Color(0xFFcacdca)  // New color #cacdca
//                )
//            ) {
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(2.dp),
//                    contentAlignment = Alignment.Center
//                ) {
//                    Text(
//                        text = "실버랜드",
//                        style = MaterialTheme.typography.titleLarge,
//                        color = Color.Black
//                    )
//                }
//            }
//        }
        if (showLocationDialog) {
            AlertDialog(
                onDismissRequest = { showLocationDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = Color(0xFFc6f584),  // 아이콘은 연두색 유지
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "지역 선택 필요",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White  // 타이틀 흰색
                    )
                },
                text = {
                    Text(
                        text = "검색하려면 지역(시/도와 구/군)을 선택해주세요.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White  // 본문 흰색
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { showLocationDialog = false }
                    ) {
                        Text(
                            "확인",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFc6f584)  // 버튼 텍스트는 연두색
                        )
                    }
                },
                containerColor = Color.DarkGray,  // 배경색 다크 그레이
                shape = RoundedCornerShape(12.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Button 1 - Home
            Button(
                onClick = {
                    currentSection = "home"
                    showFilters = false
                },
                modifier = Modifier
                    .weight(1f)
                    .border(width = 1.dp, color = Color.Black, shape = RectangleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if(currentSection == "home") Color(0xFFcacdca) else Color(0xFFcacdca),
                    contentColor = Color.Black
                ),
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp) // Minimized padding
            ) {
                Text(
                    "홈",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp  // Increased font size
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,  // Ensure single line
                    overflow = TextOverflow.Visible  // Don't cut off text
                )
            }

            // Button 2 - Facilities
            Button(
                onClick = {
                    currentSection = "welfareFacilities"
                    showFilters = true  // Always show filters when facilities button is pressed
                    viewModel.initializeServiceCategories()

                    // 디버그 로그 (선택사항)
                    Log.d("FacilityDebug", "========== 시설 데이터 현황 ==========")
                    Log.d("FacilityDebug", "전체 시설: ${viewModel.filteredPlaces.value.size}개")
                    Log.d("FacilityDebug", "서울 API 시설: ${viewModel.filteredPlaces.value.filter { it.id.toIntOrNull() == null && it.address.contains("서울") }.size}개")
                    Log.d("FacilityDebug", "서울 Supabase 시설: ${viewModel.filteredPlaces.value.filter { it.id.toIntOrNull() != null && it.address.contains("서울") }.size}개")
                    Log.d("FacilityDebug", "KK 시설 (경기): ${viewModel.kkFacilities.value.size}개")
                    Log.d("FacilityDebug", "KK2 시설 (경기): ${viewModel.kkFacility2s.value.size}개")
                    Log.d("FacilityDebug", "ICH 시설 (인천): ${viewModel.ichFacilities.value.size}개")
                    Log.d("FacilityDebug", "ICH2 시설 (인천): ${viewModel.ichFacility2s.value.size}개")
                    Log.d("FacilityDebug", "BS 시설 (부산): ${viewModel.bsFacilities.value.size}개")
                    Log.d("FacilityDebug", "BS2 시설 (부산): ${viewModel.bsFacility2s.value.size}개")
                    Log.d("FacilityDebug", "KB 시설 (경북): ${viewModel.kbFacilities.value.size}개")
                    Log.d("FacilityDebug", "KB2 시설 (경북): ${viewModel.kbFacility2s.value.size}개")
                    Log.d("FacilityDebug", "=====================================")
                },
                modifier = Modifier
                    .weight(1f)
                    .border(width = 1.dp, color = Color.Black, shape = RectangleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if(currentSection == "welfareFacilities") Color(0xFFcacdca) else Color(0xFFcacdca),
                    contentColor = Color.Black
                ),
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp) // Minimized padding
            ) {
                Text(
                    "시설",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp  // Increased font size
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,  // Ensure single line
                    overflow = TextOverflow.Visible  // Don't cut off text
                )
            }

            // Button 3 - Policy
//            Button(
//                onClick = {
//                    currentSection = "seniorPolicies"
//                    showFilters = false
//                },
//                modifier = Modifier
//                    .weight(1f)
//                    .border(width = 1.dp, color = Color.Black, shape = RectangleShape),
//                colors = ButtonDefaults.buttonColors(
//                    containerColor = if(currentSection == "seniorPolicies") Color(0xFFcacdca) else Color(0xFFcacdca),
//                    contentColor = Color.Black
//                ),
//                shape = RectangleShape,
//                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp) // Minimized padding
//            ) {
//                Text(
//                    "정책",
//                    style = MaterialTheme.typography.titleLarge.copy(
//                        fontSize = 22.sp  // Increased font size
//                    ),
//                    fontWeight = FontWeight.Bold,
//                    maxLines = 1,  // Ensure single line
//                    overflow = TextOverflow.Visible  // Don't cut off text
//                )
//            }

            // Button 4 - Jobs
            Button(
                onClick = {
                    currentSection = "jobs"
                    showFilters = false
                    // 데이터를 미리 가져오지 않음 (검색 버튼을 눌렀을 때만 가져옴)

                    // 디버그 로그 (선택사항)
                    Log.d("JobsDebug", "서울 일자리: ${viewModel.jobs.value.size}개")
                    Log.d("JobsDebug", "KK 일자리: ${viewModel.kkJobs.value.size}개")
                    Log.d("JobsDebug", "ICH 일자리: ${viewModel.ichJobs.value.size}개")
                    Log.d("JobsDebug", "BS 일자리: ${viewModel.bsJobs.value.size}개")
                    Log.d("JobsDebug", "KB 일자리: ${viewModel.kbJobs.value.size}개")
                },
                modifier = Modifier
                    .weight(1f)
                    .border(width = 1.dp, color = Color.Black, shape = RectangleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if(currentSection == "jobs") Color(0xFFcacdca) else Color(0xFFcacdca),
                    contentColor = Color.Black
                ),
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp) // Minimized padding
            ) {
                Text(
                    "고용",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp  // Increased font size
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,  // Ensure single line
                    overflow = TextOverflow.Visible  // Don't cut off text
                )
            }

            // Button 5 - Culture
            Button(
                onClick = {
                    currentSection = "culture"
                    // Fetch lecture data when this button is clicked
//                    viewModel.fetchLectureData()

                    Log.d("CultureDebug", "SEOUL문화: ${viewModel.lectures.value.size}개")
                    Log.d("CultureDebug", "KK문화: ${viewModel.kkCultures.value.size}개")
                    Log.d("CultureDebug", "ICH문화: ${viewModel.ichCultures.value.size}개")
                    Log.d("CultureDebug", "BS문화: ${viewModel.bsCultures.value.size}개")
                    Log.d("CultureDebug", "KB문화: ${viewModel.kbCultures.value.size}개")
                },
                modifier = Modifier
                    .weight(1f)
                    .border(width = 1.dp, color = Color.Black, shape = RectangleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if(currentSection == "culture") Color(0xFFcacdca) else Color(0xFFcacdca),
                    contentColor = Color.Black
                ),
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp) // Minimized padding
            ) {
                Text(
                    "문화",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp  // Increased font size
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,  // Ensure single line
                    overflow = TextOverflow.Visible  // Don't cut off text
                )
            }
        }

        // 오비서 버튼과 사용자 버튼을 포함하는 Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 오비서에게 물어보기 버튼 (가로 크기 줄임)
            Button(
                onClick = { navController.navigate("chat") },
                modifier = Modifier
                    .weight(0.7f), // 전체의 70%만 차지
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFc6f584),
                    contentColor = Color.Black
                ),
                shape = RectangleShape
            ) {
                Text(
                    "오비서에게 물어보기",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // 사용자 버튼 (남은 공간 차지)
            Box(
                modifier = Modifier.weight(0.3f) // 전체의 30% 차지
            ) {
                Button(
                    onClick = { showUserMenu = !showUserMenu },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9E9E9E), // 회색 배경
                        contentColor = Color.White
                    ),
                    shape = RectangleShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // 사용자 아이콘
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // 사용자 이름 (닉네임)
                        Text(
                            text = userInfo?.nickname ?: "사용자",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 드롭다운 메뉴
                val context = LocalContext.current
// PlaceComparisonApp 내부의 DropdownMenu 부분 수정
                DropdownMenu(
                    expanded = showUserMenu,
                    onDismissRequest = { showUserMenu = false },
                    modifier = Modifier.fillMaxWidth(0.5f)
                ) {
                    // 사용자 정보
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = userInfo?.nickname ?: "사용자",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (userInfo?.id == -1L) "게스트 모드" else "ID: ${userInfo?.id ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        },
                        onClick = { /* 프로필 보기 등의 기능 */ },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Profile"
                            )
                        }
                    )

                    Divider()

                    // 위치 정보
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (userCity.isNotEmpty() && userDistrict.isNotEmpty()) {
                                    "$userCity $userDistrict"
                                } else {
                                    "위치 정보 없음"
                                }
                            )
                        },
                        onClick = { /* 위치 설정 */ },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location"
                            )
                        }
                    )

                    // 게스트가 아닌 경우에만 회원 탈퇴 메뉴 표시
                    if (userInfo?.id != -1L) {
                        Divider()

                        // 회원 탈퇴
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "회원 탈퇴",
                                    color = Color.Red
                                )
                            },
                            onClick = {
                                // ... 기존 회원 탈퇴 코드 ...
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "Unlink",
                                    tint = Color.Red
                                )
                            }
                        )
                    }
                }
            }
        }

        // Add Chat button
//        Button(
//            onClick = { navController.navigate("chat") },
//            modifier = Modifier
//                .align(Alignment.End)
//                .padding(horizontal = 16.dp),
//            colors = ButtonDefaults.buttonColors(
//                containerColor = Color(0xFFc6f584),
//                contentColor = Color.Black
//            )
//        ) {
//            Text("채팅 문의")
//        }

        // Show filters only when button 4 is pressed and showFilters is true
        if (showFilters && currentSection == "welfareFacilities") {
            // Filters Section


            // Filters Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Location Filters (City and District side by side)
                    Text(
                        "위치:",
                        style = MaterialTheme.typography.headlineSmall, // 크게 증가
                        fontWeight = FontWeight.Bold,  // 더 두껍게
                        color = Color(0xFFffffff)  // 더 어둡게
                    )

                    Spacer(modifier = Modifier.height(8.dp))  // 간격 증가

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // City Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expandedCityMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "$selectedCity",
                                        style = MaterialTheme.typography.titleLarge, // 크기 증가
                                        color = Color(0xFFffffff), // 더 어둡게
                                        fontWeight = FontWeight.Bold // 두껍게
                                    )
                                    Text(
                                        "▼",
                                        style = MaterialTheme.typography.titleLarge, // 크기 증가
                                        color = Color(0xFFffffff) // 더 어둡게
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = expandedCityMenu,
                                onDismissRequest = { expandedCityMenu = false }
                            ) {
                                viewModel.cities.value.forEach { city ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                city,
                                                style = MaterialTheme.typography.titleLarge, // 크기 증가
                                                color = Color(0xFFffffff), // 더 어둡게
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        onClick = {
                                            selectedCity = city
                                            expandedCityMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // District Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expandedDistrictMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "$selectedDistrict",
                                        style = MaterialTheme.typography.titleLarge, // 크기 증가
                                        color = Color(0xFFffffff), // 더 어둡게
                                        fontWeight = FontWeight.Bold // 두껍게
                                    )
                                    Text(
                                        "▼",
                                        style = MaterialTheme.typography.titleLarge, // 크기 증가
                                        color = Color(0xFFffffff) // 더 어둡게
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = expandedDistrictMenu,
                                onDismissRequest = { expandedDistrictMenu = false }
                            ) {
                                availableDistricts.forEach { district ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                district,
                                                style = MaterialTheme.typography.titleLarge, // 크기 증가
                                                color = Color(0xFFffffff), // 더 어둡게
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        onClick = {
                                            selectedDistrict = district
                                            expandedDistrictMenu = false
                                            viewModel.filterPlaces(selectedCity, selectedDistrict, selectedServiceCategory, selectedServiceSubcategory)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))  // 간격 증가

                    // Service Filters (Category and Subcategory side by side)
                    Text(
                        "서비스:",
                        style = MaterialTheme.typography.headlineSmall, // 크게 증가
                        fontWeight = FontWeight.Bold,  // 더 두껍게
                        color = Color(0xFFffffff)  // 더 어둡게
                    )

                    Spacer(modifier = Modifier.height(8.dp))  // 간격 증가

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Service Category Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expandedServiceMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "$selectedServiceCategory",
                                        style = MaterialTheme.typography.titleLarge, // 크기 증가
                                        color = Color(0xFFffffff), // 더 어둡게
                                        fontWeight = FontWeight.Bold // 두껍게
                                    )
                                    Text(
                                        "▼",
                                        style = MaterialTheme.typography.titleLarge, // 크기 증가
                                        color = Color(0xFFffffff) // 더 어둡게
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = expandedServiceMenu,
                                onDismissRequest = { expandedServiceMenu = false }
                            ) {
                                viewModel.serviceCategories.value.forEach { category ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                category,
                                                style = MaterialTheme.typography.titleLarge, // 크기 증가
                                                color = Color(0xFFffffff), // 더 어둡게
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        onClick = {
                                            selectedServiceCategory = category
                                            expandedServiceMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Service Subcategory Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expandedServiceSubcategoryMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "$selectedServiceSubcategory",
                                        style = MaterialTheme.typography.titleLarge, // 크기 증가
                                        color = Color(0xFFffffff), // 더 어둡게
                                        fontWeight = FontWeight.Bold // 두껍게
                                    )
                                    Text(
                                        "▼",
                                        style = MaterialTheme.typography.titleLarge, // 크기 증가
                                        color = Color(0xFFffffff) // 더 어둡게
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = expandedServiceSubcategoryMenu,
                                onDismissRequest = { expandedServiceSubcategoryMenu = false }
                            ) {
                                availableServiceSubcategories.forEach { subcategory ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                subcategory,
                                                style = MaterialTheme.typography.titleLarge, // 크기 증가
                                                color = Color(0xFFffffff), // 더 어둡게
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        onClick = {
                                            selectedServiceSubcategory = subcategory
                                            expandedServiceSubcategoryMenu = false
                                            viewModel.filterPlaces(
                                                selectedCity,
                                                selectedDistrict,
                                                selectedServiceCategory,
                                                selectedServiceSubcategory
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Add search button (기존 스타일 유지)
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (selectedCity == "전체" || selectedDistrict == "전체") {
                                showLocationDialog = true  // 👈 다이얼로그 표시
                            } else {
                                viewModel.searchAndFilterFacilities(
                                    selectedCity,
                                    selectedDistrict,
                                    selectedServiceCategory,
                                    selectedServiceSubcategory
                                )
                                navController.navigate("searchResults")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFc6f584),
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            "검색하기",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

        }

        // Content based on the current section
        when (currentSection) {
            "home" -> {
                val highlightColor = Color(0xFFc6f584)

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // Today's Facilities Section
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "오늘의 시설",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // 사용자 위치 기반 필터링된 시설 가져오기
                                    val filteredPlaces = viewModel.getFilteredPlacesByUserLocation()

                                    if (filteredPlaces.isNotEmpty()) {
                                        val randomPlace = remember(filteredPlaces) {
                                            filteredPlaces.random()
                                        }

                                        Text(
                                            randomPlace.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            "주소: ${randomPlace.address}",
                                            style = MaterialTheme.typography.bodyLarge
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        if (randomPlace.service1.isNotEmpty()) {
                                            Text(
                                                "시설 종류: ${randomPlace.service1.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    currentSection = "welfareFacilities"
                                                    showFilters = true
                                                    // 시설 섹션으로 이동 시 사용자 위치로 필터 설정
                                                    selectedCity = if (userCity.isNotEmpty()) userCity else "전체"
                                                    selectedDistrict = if (userDistrict.isNotEmpty()) userDistrict else "전체"
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = highlightColor,
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    "더보기",
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            when {
                                                // 위치 정보가 아직 로드되지 않은 경우
                                                userCity.isEmpty() || userDistrict.isEmpty() ||
                                                        userCity == "위치 확인 중..." -> {
                                                    "위치 정보를 확인하는 중..."
                                                }
                                                // 위치 권한이 없는 경우
                                                userCity == "위치 권한 없음" -> {
                                                    "위치 권한이 필요합니다."
                                                }
                                                // 데이터가 아직 로드되지 않은 경우
                                                !viewModel.isDataLoaded() -> {
                                                    "시설 정보를 불러오는 중..."
                                                }
                                                // 데이터는 로드되었지만 해당 지역에 시설이 없는 경우
                                                else -> {
                                                    "$userCity $userDistrict 지역의 시설 정보가 없습니다."
                                                }
                                            },
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }

                        // Today's Jobs Section
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "오늘의 일자리",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // 사용자 위치 기반 필터링된 일자리 가져오기
                                    val filteredJobs = viewModel.getFilteredJobsByUserLocation()

                                    if (filteredJobs.isNotEmpty()) {
                                        val randomJob = remember(filteredJobs) {
                                            filteredJobs.random()
                                        }

                                        when (randomJob) {
                                            is SupabaseDatabaseHelper.Job -> {
                                                Text(
                                                    randomJob.Title ?: "제목 없음",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Row {
                                                    Text(
                                                        "근무형태: ",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        randomJob.WorkingType ?: "정보 없음",
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Row {
                                                    Text(
                                                        "급여: ",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        randomJob.Salary ?: "정보 없음",
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                }
                                            }
                                            is SupabaseDatabaseHelper.KKJob -> {
                                                Text(
                                                    randomJob.Title ?: "제목 없음",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    "위치: ${randomJob.Address ?: "정보 없음"}",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                if (!randomJob.WorkingHours.isNullOrEmpty()) {
                                                    Row {
                                                        Text(
                                                            "근무시간: ",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            randomJob.WorkingHours,
                                                            style = MaterialTheme.typography.bodyLarge
                                                        )
                                                    }
                                                }
                                            }
                                            is SupabaseDatabaseHelper.ICHJob -> {  // ICH Job 추가
                                                Text(
                                                    randomJob.Title ?: "제목 없음",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    "위치: ${randomJob.Address ?: "정보 없음"}",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                if (!randomJob.WorkingHours.isNullOrEmpty()) {
                                                    Row {
                                                        Text(
                                                            "근무시간: ",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            randomJob.WorkingHours,
                                                            style = MaterialTheme.typography.bodyLarge
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    currentSection = "jobs"
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = highlightColor,
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    "더보기",
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            if (userCity.isNotEmpty() && userDistrict.isNotEmpty()) {
                                                "$userCity $userDistrict 지역의 일자리 정보가 없습니다."
                                            } else {
                                                "일자리 정보를 불러오는 중..."
                                            },
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }

                        // Today's Culture Section
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "오늘의 문화",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // 사용자 위치 기반 필터링된 문화강좌 가져오기
                                    val filteredCultures = viewModel.getFilteredCulturesByUserLocation()

                                    if (filteredCultures.isNotEmpty()) {
                                        val randomCulture = remember(filteredCultures) {
                                            filteredCultures.random()
                                        }

                                        // Display the random culture based on its type
                                        when (randomCulture) {
                                            is SupabaseDatabaseHelper.Lecture -> {
                                                // Display lecture
                                                Text(
                                                    randomCulture.Title ?: "강좌명 없음",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                // Extract the clean institution name
                                                val institutionText = randomCulture.Institution?.let {
                                                    val regionStart = it.indexOf("[REGION:")
                                                    if (regionStart >= 0) {
                                                        it.substring(0, regionStart).trim()
                                                    } else {
                                                        it
                                                    }
                                                } ?: "정보 없음"

                                                Text(
                                                    "기관: $institutionText",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Row {
                                                    Text(
                                                        "수강료: ",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        randomCulture.Fee ?: "무료",
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                }
                                            }
                                            is SupabaseDatabaseHelper.KKCulture -> {
                                                // Display kk_culture
                                                Text(
                                                    randomCulture.Title ?: "강좌명 없음",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    "기관: ${randomCulture.Institution ?: "정보 없음"}",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                // Show category as location for kk_culture
                                                randomCulture.Category?.let { category ->
                                                    Text(
                                                        "지역: 경기도 $category",
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }

                                                Row {
                                                    Text(
                                                        "수강료: ",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        randomCulture.Fee ?: "무료",
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                }
                                            }
                                            is SupabaseDatabaseHelper.ICHCulture -> {
                                                // Display ich_culture
                                                Text(
                                                    randomCulture.Title ?: "강좌명 없음",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    "기관: ${randomCulture.Institution ?: "정보 없음"}",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                // Show category as location for ich_culture
                                                randomCulture.Category?.let { category ->
                                                    Text(
                                                        "지역: 인천광역시 $category",
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }

                                                Row {
                                                    Text(
                                                        "수강료: ",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        randomCulture.Fee ?: "무료",
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // New "More" button with right alignment and updated color
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    currentSection = "culture"
                                                    viewModel.fetchLectureData()
                                                    viewModel.fetchKKCulturesData()
                                                    viewModel.fetchICHCulturesData()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = highlightColor,
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    "더보기",
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            if (userCity.isNotEmpty() && userDistrict.isNotEmpty() &&
                                                userCity != "위치 확인 중..." && userCity != "위치 권한 없음") {
                                                "$userCity $userDistrict 지역의 문화 강좌 정보가 없습니다."
                                            } else {
                                                "문화 강좌 정보를 불러오는 중..."
                                            },
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }

                        // Add extra space at the bottom for better scroll experience
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }


            "longTermCare" -> {
                // Long-term care facilities content
                Text(
                    "장기요양기관 정보",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                // Places List for long-term care facilities
                if (places.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        items(places) { place ->
                            PlaceCard(place = place)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("시설 정보를 불러오는 중...")
                    }
                }
            }
            "seniorPolicies" -> {
                // Senior policies content
                Text(
                    "노인정책 정보",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("노인정책 정보 섹션")
                }
            }

// "jobs" 섹션 수정
            "jobs" -> {
                // Jobs content with filter section
                Column(modifier = Modifier.fillMaxSize()) {
                    // Add location filter section - 시설과 동일한 디자인 적용
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Location Filter Title
                            Text(
                                "위치:",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFffffff)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // City and District selection
                            var expandedCityMenu by remember { mutableStateOf(false) }
                            var expandedDistrictMenu by remember { mutableStateOf(false) }
                            var selectedCity by remember { mutableStateOf("전체") }
                            var selectedDistrict by remember { mutableStateOf("전체") }

                            val availableDistricts = remember(selectedCity) {
                                viewModel.jobDistricts.value[selectedCity] ?: listOf("전체")
                            }

                            LaunchedEffect(selectedCity) {
                                selectedDistrict = "전체"
                            }

                            // City and District Dropdowns side by side
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // City Dropdown
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { expandedCityMenu = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                selectedCity,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color(0xFFffffff),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "▼",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color(0xFFffffff)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = expandedCityMenu,
                                        onDismissRequest = { expandedCityMenu = false }
                                    ) {
                                        viewModel.jobCities.value.forEach { city ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        city,
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color = Color(0xFFffffff),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                },
                                                onClick = {
                                                    selectedCity = city
                                                    expandedCityMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // District Dropdown
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { expandedDistrictMenu = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                selectedDistrict,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color(0xFFffffff),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "▼",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color(0xFFffffff)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = expandedDistrictMenu,
                                        onDismissRequest = { expandedDistrictMenu = false }
                                    ) {
                                        availableDistricts.forEach { district ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        district,
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color = Color(0xFFffffff),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                },
                                                onClick = {
                                                    selectedDistrict = district
                                                    expandedDistrictMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Add search button
                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    if (selectedCity == "전체" || selectedDistrict == "전체") {
                                        showLocationDialog = true
                                    } else {
                                        viewModel.searchAndFilterJobs(selectedCity, selectedDistrict)
                                        navController.navigate("jobSearchResults?returnSection=jobs")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFc6f584),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text(
                                    "검색하기",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

// "culture" 섹션 수정
            "culture" -> {
                // Lectures content with pagination
                Column(modifier = Modifier.fillMaxSize()) {
                    // Add location filter section - 시설과 동일한 디자인 적용
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Location Filter Title
                            Text(
                                "위치:",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFffffff)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // City and District selection
                            var expandedCityMenu by remember { mutableStateOf(false) }
                            var expandedDistrictMenu by remember { mutableStateOf(false) }
                            var selectedCity by remember { mutableStateOf("전체") }
                            var selectedDistrict by remember { mutableStateOf("전체") }

                            val availableDistricts = remember(selectedCity) {
                                viewModel.cultureDistricts.value[selectedCity] ?: listOf("전체")
                            }

                            LaunchedEffect(selectedCity) {
                                selectedDistrict = "전체"
                                viewModel.filterAllCultures(selectedCity, selectedDistrict)
                            }

                            // City and District Dropdowns side by side
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // City Dropdown
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { expandedCityMenu = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                selectedCity,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color(0xFFffffff),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "▼",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color(0xFFffffff)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = expandedCityMenu,
                                        onDismissRequest = { expandedCityMenu = false }
                                    ) {
                                        viewModel.cultureCities.value.forEach { city ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        city,
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color = Color(0xFFffffff),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                },
                                                onClick = {
                                                    selectedCity = city
                                                    expandedCityMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // District Dropdown
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { expandedDistrictMenu = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                selectedDistrict,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color(0xFFffffff),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "▼",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color(0xFFffffff)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = expandedDistrictMenu,
                                        onDismissRequest = { expandedDistrictMenu = false }
                                    ) {
                                        availableDistricts.forEach { district ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        district,
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color = Color(0xFFffffff),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                },
                                                onClick = {
                                                    selectedDistrict = district
                                                    expandedDistrictMenu = false
                                                    viewModel.filterAllCultures(
                                                        selectedCity,
                                                        selectedDistrict
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Add search button
                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    if (selectedCity == "전체" || selectedDistrict == "전체") {
                                        showLocationDialog = true
                                    } else {
                                        viewModel.searchAndFilterCultures(selectedCity, selectedDistrict)
                                        navController.navigate("cultureSearchResults?returnSection=culture")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFc6f584),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text(
                                    "검색하기",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Show guidance message
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 빈 공간으로 유지
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultsScreen(
    viewModel: PlaceViewModel,
    navController: NavController
) {
    val places = viewModel.filteredPlaces.value
    val isLoading = viewModel.isInitialLoading

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar는 그대로 유지
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                text = "검색 결과 : ${places.size}개",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 1.dp)
            )
        }

        Divider()

        // 로딩 상태에 따라 다른 UI 표시
        if (isLoading) {
            // 로딩 화면
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 오비서 아이콘 이미지
                    Image(
                        painter = painterResource(id = R.drawable.main_icon),
                        contentDescription = "오비서 로고",
                        modifier = Modifier.size(120.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    CircularProgressIndicator(
                        color = Color(0xFFc6f584),
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "검색 중...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray
                    )
                }
            }
        } else if (places.isNotEmpty()) {
            // 기존 결과 표시 코드
            var currentPage by remember { mutableStateOf(0) }
            val itemsPerPage = 5
            val totalPages = ceil(places.size.toFloat() / itemsPerPage).toInt()

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Main content area - scrollable and limited to visible area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Slicing the list to get current page items
                    val startIndex = currentPage * itemsPerPage
                    val endIndex = minOf(startIndex + itemsPerPage, places.size)
                    val currentPageItems = places.subList(startIndex, endIndex)

                    // Scrollable container for the items
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        items(currentPageItems) { place ->
                            PlaceCard(place = place)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Pagination controls - with both previous and next buttons
                if (totalPages > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Calculate which page numbers to show
                        val pageGroupSize = 4
                        val startPage = (currentPage / pageGroupSize) * pageGroupSize
                        val endPage = minOf(startPage + pageGroupSize, totalPages)

                        // "이전" (previous) button if we're not on the first group
                        if (startPage > 0) {
                            Text(
                                text = "이전",
                                modifier = Modifier
                                    .clickable {
                                        // Go to last page of previous group
                                        currentPage = startPage - 1
                                    }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = Color(0xFF4A7C25),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Page numbers as simple text with click handling
                        for (i in startPage until endPage) {
                            val pageNumber = i + 1
                            Text(
                                text = pageNumber.toString(),
                                modifier = Modifier
                                    .clickable { currentPage = i }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = if (currentPage == i) Color(0xFF4A7C25) else Color(0xFF757575),
                                fontWeight = if (currentPage == i) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // "다음" (next) button if there are more pages
                        if (endPage < totalPages) {
                            Text(
                                text = "다음",
                                modifier = Modifier
                                    .clickable { currentPage = endPage }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = Color(0xFF4A7C25),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("검색 결과가 없습니다. 다른 조건으로 검색해보세요.")
            }
        }
    }
}

@Composable
fun JobSearchResultsScreen(
    viewModel: PlaceViewModel,
    navController: NavController,
    returnSection: String = "jobs"
) {
    val regularJobs = viewModel.filteredJobs.value
    val kkJobs = viewModel.filteredKKJobs.value
    val ichJobs = viewModel.filteredICHJobs.value
    val bsJobs = viewModel.filteredBSJobs.value
    val kbJobs = viewModel.filteredKBJobs.value

    // 통합된 일자리 리스트 생성
    val allJobs = regularJobs + kkJobs + ichJobs + bsJobs + kbJobs

    // 시스템 뒤로 가기 버튼 처리
    BackHandler(enabled = true) {
        navController.navigate("home/$returnSection") {
            popUpTo("jobSearchResults") { inclusive = true }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar with back button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFc6f584),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        navController.navigate("home/$returnSection") {
                            popUpTo("jobSearchResults") { inclusive = true }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(28.dp),
                        tint = Color.Black
                    )
                }

                Text(
                    text = "검색 결과 : ${allJobs.size}개",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Divider()

        // Results list with pagination
        if (allJobs.isNotEmpty()) {
            var currentPage by remember { mutableStateOf(0) }
            val itemsPerPage = 5
            val totalPages = ceil(allJobs.size.toFloat() / itemsPerPage).toInt()

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Main content area - shows jobs for current page
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val startIndex = currentPage * itemsPerPage
                    val endIndex = minOf(startIndex + itemsPerPage, allJobs.size)
                    val currentPageItems = allJobs.subList(startIndex, endIndex)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(currentPageItems) { job ->
                            when (job) {
                                is SupabaseDatabaseHelper.Job -> JobCard(job = job)
                                is SupabaseDatabaseHelper.KKJob -> KKJobCard(kkJob = job)
                                is SupabaseDatabaseHelper.ICHJob -> ICHJobCard(ichJob = job)
                                is SupabaseDatabaseHelper.BSJob -> BSJobCard(bsJob = job)
                                is SupabaseDatabaseHelper.KBJob -> KBJobCard(kbJob = job)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Pagination controls
                if (totalPages > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Calculate which page numbers to show
                        val pageGroupSize = 4
                        val startPage = (currentPage / pageGroupSize) * pageGroupSize
                        val endPage = minOf(startPage + pageGroupSize, totalPages)

                        // Previous button
                        if (startPage > 0) {
                            Text(
                                text = "이전",
                                modifier = Modifier
                                    .clickable {
                                        currentPage = startPage - 1
                                    }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = Color(0xFF4A7C25),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Page numbers
                        for (i in startPage until endPage) {
                            val pageNumber = i + 1
                            Text(
                                text = pageNumber.toString(),
                                modifier = Modifier
                                    .clickable { currentPage = i }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = if (currentPage == i) Color(0xFF4A7C25) else Color(0xFF757575),
                                fontWeight = if (currentPage == i) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        // Next button
                        if (endPage < totalPages) {
                            Text(
                                text = "다음",
                                modifier = Modifier
                                    .clickable { currentPage = endPage }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = Color(0xFF4A7C25),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else if (viewModel.isLoading || viewModel.isLoadingKKJobs || viewModel.isLoadingICHJobs ||
            viewModel.isLoadingBSJobs || viewModel.isLoadingKBJobs) {
            // Show loading indicator
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF4A7C25))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("일자리 정보를 불러오는 중...")
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("검색 결과가 없습니다. 다른 조건으로 검색해보세요.")
            }
        }
    }
}

// MainActivity.kt에 추가할 CultureSearchResultsScreen Composable
// 다음 import들이 필요합니다:
// import androidx.compose.ui.text.style.TextAlign
// 기타 필요한 import들은 이미 MainActivity에 포함되어 있습니다.

@Composable
fun CultureSearchResultsScreen(
    viewModel: PlaceViewModel,
    navController: NavController,
    returnSection: String = "culture"
) {
    val regularLectures = viewModel.filteredLectures.value
    val kkCultures = viewModel.filteredKKCultures.value
    val ichCultures = viewModel.filteredICHCultures.value
    val bsCultures = viewModel.filteredBSCultures.value
    val kbCultures = viewModel.filteredKBCultures.value

    // 통합된 문화 강좌 리스트 생성
    val allCultures = regularLectures + kkCultures + ichCultures + bsCultures + kbCultures

    // 시스템 뒤로 가기 버튼 처리
    BackHandler(enabled = true) {
        navController.navigate("home/$returnSection") {
            popUpTo("cultureSearchResults") { inclusive = true }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar with back button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFc6f584),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        navController.navigate("home/$returnSection") {
                            popUpTo("cultureSearchResults") { inclusive = true }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(28.dp),
                        tint = Color.Black
                    )
                }

                Text(
                    text = "검색 결과 : ${allCultures.size}개",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Divider()

        // Results list with pagination
        if (allCultures.isNotEmpty()) {
            var currentPage by remember { mutableStateOf(0) }
            val itemsPerPage = 5
            val totalPages = ceil(allCultures.size.toFloat() / itemsPerPage).toInt()

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Main content area - shows cultures for current page
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val startIndex = currentPage * itemsPerPage
                    val endIndex = minOf(startIndex + itemsPerPage, allCultures.size)
                    val currentPageItems = allCultures.subList(startIndex, endIndex)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(currentPageItems) { culture ->
                            when (culture) {
                                is SupabaseDatabaseHelper.Lecture -> LectureCard(lecture = culture)
                                is SupabaseDatabaseHelper.KKCulture -> KKCultureCard(kkCulture = culture)
                                is SupabaseDatabaseHelper.ICHCulture -> ICHCultureCard(ichCulture = culture)
                                is SupabaseDatabaseHelper.BSCulture -> BSCultureCard(bsCulture = culture)
                                is SupabaseDatabaseHelper.KBCulture -> KBCultureCard(kbCulture = culture)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Pagination controls
                if (totalPages > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Calculate which page numbers to show
                        val pageGroupSize = 4
                        val startPage = (currentPage / pageGroupSize) * pageGroupSize
                        val endPage = minOf(startPage + pageGroupSize, totalPages)

                        // Previous button
                        if (startPage > 0) {
                            Text(
                                text = "이전",
                                modifier = Modifier
                                    .clickable {
                                        currentPage = startPage - 1
                                    }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = Color(0xFF4A7C25),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Page numbers
                        for (i in startPage until endPage) {
                            val pageNumber = i + 1
                            Text(
                                text = pageNumber.toString(),
                                modifier = Modifier
                                    .clickable { currentPage = i }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = if (currentPage == i) Color(0xFF4A7C25) else Color(0xFF757575),
                                fontWeight = if (currentPage == i) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        // Next button
                        if (endPage < totalPages) {
                            Text(
                                text = "다음",
                                modifier = Modifier
                                    .clickable { currentPage = endPage }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = Color(0xFF4A7C25),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else if (viewModel.isLoadingLectures || viewModel.isLoadingKKCultures || viewModel.isLoadingICHCultures ||
            viewModel.isLoadingBSCultures || viewModel.isLoadingKBCultures) {
            // Show loading indicator
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF4A7C25))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("문화 강좌 정보를 불러오는 중...")
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("검색 결과가 없습니다. 다른 조건으로 검색해보세요.")
            }
        }
    }
}


@Composable
fun PlaceCard(place: Place) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 이름이 12자 이상일 경우 줄바꿈 처리
            val formattedName = if (place.name.length > 12) {
                val midPoint = place.name.length / 2
                // 중간 지점에서 가장 가까운 공백 찾기
                var breakIndex = place.name.lastIndexOf(" ", midPoint)

                // 공백이 없거나 너무 앞쪽에 있는 경우 그냥 12자리에서 자르기
                if (breakIndex == -1 || breakIndex < 4) {
                    breakIndex = 12
                }

                val firstLine = place.name.substring(0, breakIndex)
                val secondLine = place.name.substring(breakIndex).trimStart()
                "$firstLine\n$secondLine"
            } else {
                place.name
            }

            // 이름만 상단에 표시
            Text(
                formattedName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (place.rating.isNotEmpty()) {
                val gradeText = "평가등급: ${place.rating}"

                // 등급에 따라 다른 색상 적용
                val gradeColor = when {
                    place.rating.contains("A") -> Color(0xFF4CAF50)  // 녹색 (A 포함)
                    place.rating.contains("B") -> Color(0xFF2196F3)  // 파란색 (B 포함)
                    place.rating.contains("C") -> Color(0xFFFFC107)  // 노란색 (C 포함)
                    place.rating.contains("D") -> Color(0xFFFF9800)  // 주황색 (D 포함)
                    place.rating.contains("E") -> Color(0xFFF44336)  // 빨간색 (E 포함)
                    else -> Color.Black
                }
                Text(
                    text = gradeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = gradeColor
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 주소
            Text(
                "주소: ${place.address}",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 서비스 정보
            if (place.service1.isNotEmpty()) {
                Text(
                    "시설 종류: ${place.service1.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (place.service2.isNotEmpty()) {
                Text(
                    "시설 유형: ${place.facilityKind}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 수용 인원 정보 - 0이 아닌 경우에만 표시
            if (place.full != "0" || place.now != "0" || place.wating != "0") {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "정원: ${place.full}명",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "현재: ${place.now}명",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "대기: ${place.wating}명",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun JobCard(job: SupabaseDatabaseHelper.Job) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Job Title
            Text(
                text = job.Title ?: "제목 없음",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Working Type
            Row {
                Text(
                    "근무형태: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    job.WorkingType ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Working Hours
            Row {
                Text(
                    "근무시간: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    job.WorkingHours ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Salary (Fee)
            Row {
                Text(
                    "급여: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    job.Salary ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }


            // Apply button aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        job.Detail?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } ?: run {
                            Toast.makeText(context, "신청 페이지 정보가 없습니다", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Yellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "신청",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun KKJobCard(kkJob: SupabaseDatabaseHelper.KKJob) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Job Title
            Text(
                text = kkJob.Title ?: "제목 없음",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Location
            Row {
                Text(
                    "위치: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    kkJob.Address ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Working Hours
            if (!kkJob.WorkingHours.isNullOrEmpty()) {
                Row {
                    Text(
                        "근무시간: ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        kkJob.WorkingHours,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Salary
            kkJob.Salary?.let { salary ->
                Row {
                    Text(
                        "급여: ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        salary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Apply button aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        kkJob.Detail?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } ?: run {
                            Toast.makeText(context, "신청 페이지 정보가 없습니다", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Yellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "신청",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ICHJobCard(ichJob: SupabaseDatabaseHelper.ICHJob) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Job Title
            Text(
                text = ichJob.Title ?: "제목 없음",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Location
            Row {
                Text(
                    "위치: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    ichJob.Address ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Working Hours
            if (!ichJob.WorkingHours.isNullOrEmpty()) {
                Row {
                    Text(
                        "근무시간: ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        ichJob.WorkingHours,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Salary
            ichJob.Salary?.let { salary ->
                Row {
                    Text(
                        "급여: ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        salary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }


            // Apply button aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        ichJob.Detail?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } ?: run {
                            Toast.makeText(context, "신청 페이지 정보가 없습니다", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Yellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "신청",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

// MainActivity.kt에 추가할 JobCard 컴포저블

@Composable
fun BSJobCard(bsJob: SupabaseDatabaseHelper.BSJob) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Job Title
            Text(
                text = bsJob.Title ?: "제목 없음",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Location
            Row {
                Text(
                    "위치: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    bsJob.Address ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Working Hours
            if (!bsJob.WorkingHours.isNullOrEmpty()) {
                Row {
                    Text(
                        "근무시간: ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        bsJob.WorkingHours,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Salary
            bsJob.Salary?.let { salary ->
                Row {
                    Text(
                        "급여: ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        salary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Apply button aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        bsJob.Detail?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } ?: run {
                            Toast.makeText(context, "신청 페이지 정보가 없습니다", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Yellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "신청",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun KBJobCard(kbJob: SupabaseDatabaseHelper.KBJob) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Job Title
            Text(
                text = kbJob.Title ?: "제목 없음",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Location
            Row {
                Text(
                    "위치: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    kbJob.Address ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Working Hours
            if (!kbJob.WorkingHours.isNullOrEmpty()) {
                Row {
                    Text(
                        "근무시간: ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        kbJob.WorkingHours,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Salary
            kbJob.Salary?.let { salary ->
                Row {
                    Text(
                        "급여: ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        salary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Apply button aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        kbJob.Detail?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } ?: run {
                            Toast.makeText(context, "신청 페이지 정보가 없습니다", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Yellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "신청",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun LectureCard(lecture: SupabaseDatabaseHelper.Lecture) {
    // Extract the clean institution name by removing the region marker completely
    val institutionText = lecture.Institution?.let {
        val regionStart = it.indexOf("[REGION:")
        if (regionStart >= 0) {
            // Return the part before the region marker
            it.substring(0, regionStart).trim()
        } else {
            it // No region marker, return as is
        }
    } ?: "정보 없음"

    // Trim institution text to only show up to "센터"
    val trimmedInstitutionText = institutionText.let {
        val centerIndex = it.indexOf("센터")
        if (centerIndex >= 0) {
            it.substring(0, centerIndex + "센터".length)
        } else {
            it
        }
    }

    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Lecture Title
            Text(
                text = lecture.Title ?: "강좌명 없음",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Institution only (without region or brackets)
            Text(
                text = "기관: $trimmedInstitutionText",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Recruitment Period
            Row {
                Text(
                    "모집기간: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    lecture.Recruitment_period ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Education Period
            Row {
                Text(
                    "교육기간: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    lecture.Education_period ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fee
            Text(
                "수강료: ${lecture.Fee ?: "무료"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Tel if available
            lecture.Tel?.let { tel ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "연락처: $tel",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Apply button aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        lecture.Detail?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } ?: run {
                            Toast.makeText(context, "신청 페이지 정보가 없습니다", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Yellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "신청",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun KKCultureCard(kkCulture: SupabaseDatabaseHelper.KKCulture) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Culture Title
            Text(
                text = kkCulture.Title ?: "강좌명 없음",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Institution
            Text(
                text = "기관: ${kkCulture.Institution ?: "정보 없음"}",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Address
            kkCulture.Address?.let { address ->
                Text(
                    text = "위치: $address",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Category (이제 지역 정보로 표시)
            kkCulture.Category?.let { category ->
                Text(
                    text = "지역: 경기도 $category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Recruitment Period
            Row {
                Text(
                    "모집기간: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    kkCulture.Recruitment_period ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Education Period
            Row {
                Text(
                    "교육기간: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    kkCulture.Education_period ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fee
            Text(
                "수강료: ${kkCulture.Fee ?: "무료"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Tel if available
            kkCulture.Tel?.let { tel ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "연락처: $tel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
//                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Apply button aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        kkCulture.Detail?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } ?: run {
                            Toast.makeText(context, "신청 페이지 정보가 없습니다", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Yellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "신청",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ICHCultureCard(ichCulture: SupabaseDatabaseHelper.ICHCulture) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Culture Title
            Text(
                text = ichCulture.Title ?: "강좌명 없음",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Institution
            Text(
                text = "기관: ${ichCulture.Institution ?: "정보 없음"}",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Address
            ichCulture.Address?.let { address ->
                Text(
                    text = "위치: $address",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Category (이제 지역 정보로 표시)
            ichCulture.Category?.let { category ->
                Text(
                    text = "지역: 인천광역시 $category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Recruitment Period
            Row {
                Text(
                    "모집기간: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    ichCulture.Recruitment_period ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Education Period
            Row {
                Text(
                    "교육기간: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    ichCulture.Education_period ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Date (교육 날짜)
            ichCulture.Date?.let { date ->
                Row {
                    Text(
                        "교육일시: ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        date,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Fee
            Text(
                "수강료: ${ichCulture.Fee ?: "무료"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Tel if available
            ichCulture.Tel?.let { tel ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "연락처: $tel",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
//                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Apply button aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        ichCulture.Detail?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } ?: run {
                            Toast.makeText(context, "신청 페이지 정보가 없습니다", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Yellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "신청",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

// MainActivity.kt에 추가할 CultureCard 컴포저블

// MainActivity.kt에 추가할 CultureCard 컴포저블

@Composable
fun BSCultureCard(bsCulture: SupabaseDatabaseHelper.BSCulture) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Culture Title
            Text(
                text = bsCulture.Title ?: "강좌명 없음",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Institution
            Text(
                text = "기관: ${bsCulture.Institution ?: "정보 없음"}",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Address
            bsCulture.Address?.let { address ->
                Text(
                    text = "위치: $address",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Category (이제 지역 정보로 표시)
            bsCulture.Category?.let { category ->
                Text(
                    text = "지역: 부산광역시 $category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Recruitment Period
            Row {
                Text(
                    "모집기간: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    bsCulture.Recruitment_period ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Education Period
            Row {
                Text(
                    "교육기간: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    bsCulture.Education_period ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Date (교육 날짜)
            bsCulture.Date?.let { date ->
                Row {
                    Text(
                        "교육일시: ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        date,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Fee
            Text(
                "수강료: ${bsCulture.Fee ?: "무료"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Tel if available
            bsCulture.Tel?.let { tel ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "연락처: $tel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Apply button aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        bsCulture.Detail?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } ?: run {
                            Toast.makeText(context, "신청 페이지 정보가 없습니다", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Yellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "신청",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun KBCultureCard(kbCulture: SupabaseDatabaseHelper.KBCulture) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Culture Title
            Text(
                text = kbCulture.Title ?: "강좌명 없음",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Institution
            Text(
                text = "기관: ${kbCulture.Institution ?: "정보 없음"}",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Address
            kbCulture.Address?.let { address ->
                Text(
                    text = "위치: $address",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Category (이제 지역 정보로 표시)
            kbCulture.Category?.let { category ->
                Text(
                    text = "지역: 경상북도 $category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Recruitment Period
            Row {
                Text(
                    "모집기간: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    kbCulture.Recruitment_period ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Education Period
            Row {
                Text(
                    "교육기간: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    kbCulture.Education_period ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fee
            Text(
                "수강료: ${kbCulture.Fee ?: "무료"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Apply button aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        kbCulture.Detail?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } ?: run {
                            Toast.makeText(context, "신청 페이지 정보가 없습니다", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Yellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "신청",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    activity: MainActivity,
    navController: NavController,
    showBackButton: Boolean = true,
    userCity: String = "",
    userDistrict: String = "",
    userInfo: UserInfo? = null
) {
    val (savedMessages, savedSessionId) = remember { activity.loadChatMessages() }

    // Use rememberSaveable to persist state across recompositions
    var messageText by rememberSaveable { mutableStateOf("") }
    var messages by remember {
        mutableStateOf(
            if (savedMessages.isNotEmpty()) {
                savedMessages
            } else {
                listOf(
                    ChatMessage(
                        text = "안녕하세요! 오비서입니다. 시니어에 관련된 정책, 일자리, 복지시설에 관해 무엇이든 물어보세요!",
                        isFromUser = false
                    )
                )
            }
        )
    }
    val sessionId = rememberSaveable { savedSessionId }


    // Speech recognition state
    var isListening by remember { mutableStateOf(false) }

    // Focus manager for keyboard control
    val focusManager = LocalFocusManager.current

    // Navigation state for search results
    val showNavigationState = rememberSaveable { mutableStateOf(false) }
    val hasPreviousState = rememberSaveable { mutableStateOf(false) }
    val hasNextState = rememberSaveable { mutableStateOf(false) }
    val currentPageState = rememberSaveable { mutableStateOf(0) }
    val totalPagesState = rememberSaveable { mutableStateOf(0) }

    var showNavigation by showNavigationState
    var hasPrevious by hasPreviousState
    var hasNext by hasNextState
    var currentPage by currentPageState
    var totalPages by totalPagesState

    // Create speech recognizer
    val context = LocalContext.current
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    // 위치 권한 런처 추가
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // 권한이 승인되면 위치 정보를 다시 가져오도록 메인 액티비티에 요청
            Toast.makeText(
                context,
                "위치 권한이 승인되었습니다. 위치 정보를 가져오는 중...",
                Toast.LENGTH_SHORT
            ).show()

            // MainActivity의 위치 정보 업데이트 트리거
            // 실제로는 MainActivity에서 위치 정보를 다시 가져와야 함
            activity.getLastKnownLocation { city, district ->
                // 위치 정보가 업데이트되면 ChatScreen에 반영됨
            }
        } else {
            // 권한이 거부된 경우 토스트 메시지 표시
            Toast.makeText(
                context,
                "오비서 앱은 사용자님의 지역에 맞는 정보를 제공하기 위해 위치 권한이 필요합니다.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val speechRecognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀해주세요...")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // BackHandler 추가 - 뒤로 가기 버튼 동작 제어
    BackHandler(enabled = true) {
        if (showBackButton) {
            // 뒤로 가기 버튼이 표시되는 경우 이전 화면으로
            navController.navigateUp()
        } else {
            // 뒤로 가기 버튼이 없는 경우 (메인 채팅 화면)
            // 홈 화면으로 이동
            navController.navigate("home") {
                popUpTo("chat") { inclusive = true }
            }
        }
    }

    // Set up the callback to receive responses from n8n
    LaunchedEffect(Unit) {
        // 네비게이션 상태 복원
        activity.chatService.restoreNavigationState()

        if (messages.isEmpty()) {
            messages = listOf(
                ChatMessage(
                    text = "안녕하세요! 오비서입니다. 시니어에 관련된 정책, 일자리, 복지시설에 관해 무엇이든 물어보세요!",
                    isFromUser = false
                )
            )
        }


        // Set up the speech recognizer listener with real-time transcription
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    messageText = recognizedText
                }
                isListening = false
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    messageText = partialText
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Log.e("SpeechRecognition", "Error code: $error")
                isListening = false
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // 일반 응답 콜백 - 통합 처리
        activity.chatService.responseCallback = { aiResponse ->
            Log.d("ChatScreen", "Received response: $aiResponse")

            // "AI가 검색중..." 메시지가 아닌 경우에만 SearchHistory 저장
            if (aiResponse != "AI가 검색중...") {
                // SearchHistory 저장을 위한 코루틴
                val supabaseHelper = SupabaseDatabaseHelper(context)
                val queryContent = activity.chatService.lastQueryContent ?: ""

                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        // 현재 사용자의 식별자 가져오기 (kakao_id 또는 user_id)
                        val identifier = activity.currentUserId
                        Log.d("ChatScreen", "Current user identifier: $identifier")

                        // Supabase에서 사용자 정보 조회
                        val user = supabaseHelper.getUserByIdentifier(identifier)

                        if (user != null && queryContent.isNotEmpty()) {
                            // ChatService에서 저장한 category와 answer 가져오기
                            val category = activity.chatService.lastSearchCategory ?: "일반"
                            var answer = activity.chatService.lastSearchAnswer

                            // answer가 null이거나 비어있으면 aiResponse에서 추출
                            if (answer.isNullOrEmpty()) {
                                // aiResponse에서 실제 내용 추출 (포맷팅 제거)
                                val cleanedResponse = aiResponse
                                    .replace(
                                        Regex("[\uD83C-\uDBFF\uDC00-\uDFFF]+"),
                                        ""
                                    ) // 모든 이모지 제거
                                    .trim()

                                answer = if (cleanedResponse.length > 100) {
                                    cleanedResponse.substring(0, 100)
                                } else {
                                    cleanedResponse
                                }
                            }

                            // 검색 기록 저장
                            val searchHistory = supabaseHelper.saveSearchHistory(
                                userId = user.id!!,
                                queryCategory = "Chat",
                                queryContent = queryContent,
                                category = category,
                                answer = answer
                            )

                            if (searchHistory != null) {
                                Log.d("ChatScreen", "검색 기록 저장 성공: ${searchHistory.id}")
                                Log.d("ChatScreen", "Query: $queryContent")
                                Log.d("ChatScreen", "Category: $category, Answer: $answer")

                                // ✅ SearchHistory ID를 ChatService에 저장
                                activity.chatService.lastSearchHistoryId = searchHistory.id
                                Log.d("ChatScreen", "Saved SearchHistory ID to ChatService: ${searchHistory.id}")
                            } else {
                                Log.e("ChatScreen", "검색 기록 저장 실패")
                            }

                            // 저장 후 임시 데이터 초기화 (lastSearchHistoryId는 유지)
                            activity.chatService.lastSearchCategory = null
                            activity.chatService.lastSearchAnswer = null
                            activity.chatService.lastQueryContent = null
                        } else {
                            if (user == null) {
                                Log.e("ChatScreen", "사용자 정보를 찾을 수 없음: identifier = $identifier")
                            }
                            if (queryContent.isEmpty()) {
                                Log.e("ChatScreen", "질문 내용이 비어있음")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatScreen", "검색 기록 저장 중 오류: ${e.message}", e)
                    }
                }
            }

            // 검색 결과인지 확인
            var isSearchResult = false
            var processedResponse = aiResponse

            // JSON 응답인지 확인하고 results 배열의 content를 확인
            try {
                val jsonResponse = org.json.JSONObject(aiResponse)
                if (jsonResponse.has("results")) {
                    val results = jsonResponse.getJSONArray("results")
                    if (results.length() > 0) {
                        // 모든 결과의 content를 확인
                        for (i in 0 until results.length()) {
                            val result = results.getJSONObject(i)
                            if (result.has("content")) {
                                val content = result.getString("content")
                                // content가 존재하면 검색 결과로 판단
                                if (content.isNotEmpty()) {
                                    isSearchResult = true
                                    // 첫 번째 content를 기준으로 판단
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // JSON 파싱 실패 - "📋"로 시작하는지 확인
                isSearchResult = aiResponse.startsWith("📋")
                Log.d("ChatScreen", "JSON parsing failed, checking for 📋: $isSearchResult")
            }

            if (!isSearchResult) {
                // 일반 응답: 새로운 메시지 추가 또는 waiting 메시지 교체
                val updatedMessages = messages.toMutableList()
                val waitingIndex = updatedMessages.indexOfLast { it.isWaiting }

                if (waitingIndex >= 0) {
                    updatedMessages[waitingIndex] = ChatMessage(
                        text = aiResponse,
                        isFromUser = false
                    )
                } else {
                    updatedMessages.add(
                        ChatMessage(
                            text = aiResponse,
                            isFromUser = false
                        )
                    )
                }
                messages = updatedMessages
            } else {
                // 검색 결과 처리
                val updatedMessages = messages.toMutableList()

                // 가장 최근의 사용자 메시지 찾기
                val lastUserMessageIndex = updatedMessages.indexOfLast { it.isFromUser }

                if (lastUserMessageIndex >= 0) {
                    // 해당 사용자 메시지 다음의 첫 번째 봇 메시지 찾기 (검색 결과 또는 waiting)
                    var targetIndex = -1
                    for (i in lastUserMessageIndex + 1 until updatedMessages.size) {
                        if (!updatedMessages[i].isFromUser) {
                            // JSON 응답이거나 📋로 시작하거나 waiting 상태인 메시지 찾기
                            val msgText = updatedMessages[i].text
                            if (msgText.startsWith("📋") ||
                                msgText.contains("\"results\"") ||
                                msgText.contains("\"content\"") ||
                                updatedMessages[i].isWaiting
                            ) {
                                targetIndex = i
                                break
                            }
                        }
                    }

                    if (targetIndex >= 0) {
                        // 찾은 위치의 메시지 업데이트
                        updatedMessages[targetIndex] = ChatMessage(
                            text = aiResponse,
                            isFromUser = false
                        )
                        messages = updatedMessages
                    } else {
                        // 못 찾은 경우 waiting 메시지 찾아서 교체
                        val waitingIndex = updatedMessages.indexOfLast { it.isWaiting }
                        if (waitingIndex >= 0) {
                            updatedMessages[waitingIndex] = ChatMessage(
                                text = aiResponse,
                                isFromUser = false
                            )
                            messages = updatedMessages
                        } else {
                            // 그것도 없으면 새로 추가
                            messages = messages + ChatMessage(
                                text = aiResponse,
                                isFromUser = false
                            )
                        }
                    }
                } else {
                    // 사용자 메시지가 없는 경우 (이상한 케이스)
                    messages = messages + ChatMessage(
                        text = aiResponse,
                        isFromUser = false
                    )
                }
            }
        }

        // 탐색 모드 전용 콜백
        activity.chatService.exploreResponseCallback = { aiResponse ->
            Log.d("ChatScreen", "Received explore response: $aiResponse")

            val lastExploreIndex = messages.indexOfLast {
                it.text.startsWith("📋") && !it.isFromUser
            }

            if (lastExploreIndex >= 0) {
                val updatedMessages = messages.toMutableList()
                updatedMessages[lastExploreIndex] = ChatMessage(
                    text = aiResponse,
                    isFromUser = false
                )
                messages = updatedMessages
            } else {
                messages = messages + ChatMessage(
                    text = aiResponse,
                    isFromUser = false
                )
            }
        }

        // Set up navigation callback
        activity.chatService.navigationCallback = { hasPrev, hasNextResult, current, total ->
            hasPrevious = hasPrev
            hasNext = hasNextResult
            currentPage = current
            totalPages = total
            showNavigation = total > 1

            // 상태가 업데이트될 때마다 저장
            activity.chatService.saveNavigationState()
        }
    }

// 메시지가 변경될 때 스크롤
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            delay(100)
            try {
                listState.animateScrollToItem(
                    index = messages.size - 1,
                    scrollOffset = 0
                )
            } catch (e: Exception) {
                Log.e("ChatScreen", "Scroll error: ${e.message}")
            }
        }
    }

// AI 응답이 도착했을 때 스크롤
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            activity.saveChatMessages(messages, sessionId)
            val lastMessage = messages.last()
            // AI 응답이고 waiting 상태가 아닐 때
            if (!lastMessage.isFromUser && !lastMessage.isWaiting) {
                delay(150)
                try {
                    listState.animateScrollToItem(
                        index = messages.size - 1,
                        scrollOffset = 0
                    )
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Auto scroll error: ${e.message}")
                }
            }
        }
    }

// TextField가 포커스를 받을 때 스크롤 (키보드 대신)
    var isTextFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isTextFieldFocused) {
        if (isTextFieldFocused && messages.isNotEmpty()) {
            delay(300) // 키보드 애니메이션을 위한 지연
            try {
                listState.animateScrollToItem(messages.size - 1)
            } catch (e: Exception) {
                Log.e("ChatScreen", "Focus scroll error: ${e.message}")
            }
        }
    }

    // Clean up callback when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            // 화면을 떠날 때 현재 상태 저장
            activity.chatService.saveNavigationState()

            // 콜백만 정리하고 결과는 유지
            activity.chatService.responseCallback = null
            activity.chatService.navigationCallback = null
            activity.chatService.exploreResponseCallback = null
            // clearResults()는 제거하여 상태 유지
        }
    }

    // Check for microphone permission
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechRecognizer?.startListening(speechRecognizerIntent)
            isListening = true
        } else {
            Toast.makeText(context, "음성 인식을 위해 마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    // UI 구성 부분
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar
            TopAppBar(
                title = { Text("오비서") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(
                            onClick = { navController.navigateUp() },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (!showBackButton) {
                        IconButton(onClick = {
                            navController.navigate("home?section=home") {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = true
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                modifier = Modifier.size(50.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFc6f584),
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black,
                    actionIconContentColor = Color.Black
                )
            )

            Divider()

            // Messages list
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        bottom = if (showNavigation) 80.dp else 16.dp
                    )
                ) {
                    itemsIndexed(messages) { index, message ->
                        MessageItem(
                            message = message,
                            navController = navController,
                            activity = activity
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
            // Navigation controls for search results (when multiple results exist)
            if (showNavigation) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent // 완전히 투명
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp), // 패딩 줄임
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous button - 첫 번째 결과가 아닐 때만 표시
                        if (hasPrevious) {
                            Button(
                                onClick = {
                                    // ChatService를 통해 이전 결과 표시
                                    activity.chatService.showPreviousResult()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFc6f584),
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Previous",
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        "이전",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

// Page indicator
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "$currentPage / $totalPages",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFc6f584)
                            )
                        }

// Next button
                        if (hasNext) {
                            Button(
                                onClick = {
                                    // ChatService를 통해 다음 결과 표시
                                    activity.chatService.showNextResult()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFc6f584),
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "다음",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Next",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    // 채팅 횟수 확인
                                    if (!RequestCounterHelper.canSendMessage()) {
                                        Toast.makeText(activity, "오늘 채팅 갯수 도달", Toast.LENGTH_LONG)
                                            .show()

                                        // 채팅 한도 도달 메시지를 채팅창에 추가
                                        val limitMessage = ChatMessage(
                                            text = "오늘 채팅 갯수 도달\n내일 다시 이용해 주세요.",
                                            isFromUser = false
                                        )
                                        messages = messages + limitMessage
                                        return@Button
                                    }

                                    // 탐색 시작 토스트 메시지 표시
                                    Toast.makeText(activity, "탐색 시작", Toast.LENGTH_SHORT).show()

                                    // 상세 디버깅 로그
                                    Log.d("ExploreDebug", "=== 탐색 버튼 클릭 ===")
                                    Log.d("ExploreDebug", "userCity: '$userCity'")
                                    Log.d("ExploreDebug", "userDistrict: '$userDistrict'")

                                    // 탐색 버튼 기능 - Flask 서버의 explore 엔드포인트로 연결
                                    Thread {
                                        try {
                                            // 요청 전에 카운트 증가
                                            RequestCounterHelper.incrementRequestCount()

                                            val url = URL("http://192.168.219.101:5000/explore")
//                                            val url = URL("https://coral-app-fjt8m.ondigitalocean.app/explore")
                                            val connection =
                                                url.openConnection() as HttpURLConnection
                                            connection.requestMethod = "POST"
                                            connection.setRequestProperty(
                                                "Content-Type",
                                                "application/json"
                                            )
                                            connection.doOutput = true

                                            // JSON 데이터 생성
                                            val jsonObject = JSONObject().apply {
                                                put("userCity", userCity)
                                                put("userDistrict", userDistrict)
                                            }

                                            Log.d(
                                                "ExploreDebug",
                                                "전송할 JSON: ${jsonObject.toString()}"
                                            )

                                            // 데이터 전송
                                            connection.outputStream.use { os ->
                                                val input = jsonObject.toString()
                                                    .toByteArray(Charsets.UTF_8)
                                                os.write(input, 0, input.size)
                                            }

                                            // 응답 받기
                                            val responseCode = connection.responseCode
                                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                                val response =
                                                    connection.inputStream.bufferedReader()
                                                        .use { it.readText() }
                                                Log.d("ExploreResponse", "서버 응답: $response")

                                                // JSON 응답 파싱
                                                try {
                                                    val responseJson = JSONObject(response)
                                                    val generatedQuery = responseJson.optString(
                                                        "generated_query",
                                                        null
                                                    )
                                                    val queryResponse =
                                                        responseJson.optJSONObject("query_response")

                                                    // UI 스레드에서 처리
                                                    activity.runOnUiThread {
                                                        if (generatedQuery != null && queryResponse != null) {
                                                            // 생성된 질문을 메시지로 추가
                                                            val exploreMessage = ChatMessage(
                                                                text = "🔍 탐색: $generatedQuery",
                                                                isFromUser = false
                                                            )
                                                            messages = messages + exploreMessage

                                                            // 응답 처리
                                                            val responseType =
                                                                queryResponse.optString("type")
                                                            when (responseType) {
                                                                "llm" -> {
                                                                    val content =
                                                                        queryResponse.optString(
                                                                            "content",
                                                                            "응답 없음"
                                                                        )
                                                                    val responseMessage =
                                                                        ChatMessage(
                                                                            text = content,
                                                                            isFromUser = false
                                                                        )
                                                                    messages =
                                                                        messages + responseMessage

                                                                    // LLM 응답은 단일 결과이므로 네비게이션 필요 없음
                                                                    showNavigation = false
                                                                }
                                                                "pinecone" -> {
                                                                    val results =
                                                                        queryResponse.optJSONArray("results")
                                                                    val category =
                                                                        queryResponse.optString(
                                                                            "category",
                                                                            ""
                                                                        )

                                                                    if (results != null && results.length() > 0) {
                                                                        // 검색 결과를 개별 메시지로 저장
                                                                        val searchResults =
                                                                            mutableListOf<ChatMessage>()

                                                                        for (i in 0 until results.length()) {
                                                                            val result =
                                                                                results.getJSONObject(
                                                                                    i
                                                                                )
                                                                            val title =
                                                                                result.optString(
                                                                                    "title",
                                                                                    "제목 없음"
                                                                                )
                                                                            val content =
                                                                                result.optString(
                                                                                    "content",
                                                                                    "내용 없음"
                                                                                )
                                                                            val resultCategory =
                                                                                result.optString(
                                                                                    "category",
                                                                                    ""
                                                                                )

                                                                            val resultText =
                                                                                StringBuilder()
                                                                            resultText.append("📋 ${category} 검색 결과 ${i + 1}/${results.length()}\n\n")
                                                                            resultText.append("🏢 $title\n")
                                                                            if (resultCategory.isNotEmpty()) {
                                                                                resultText.append("📍 $resultCategory\n")
                                                                            }
                                                                            resultText.append("\n$content")

                                                                            searchResults.add(
                                                                                ChatMessage(
                                                                                    text = resultText.toString(),
                                                                                    isFromUser = false
                                                                                )
                                                                            )
                                                                        }

                                                                        // ChatService를 통해 검색 결과 설정
                                                                        activity.chatService.setSearchResults(
                                                                            searchResults
                                                                        )

                                                                        // ChatService의 콜백을 통해 결과를 표시하도록 설정
                                                                        // 탐색 모드용 콜백 설정
                                                                        activity.chatService.exploreResponseCallback =
                                                                            { aiResponse ->
                                                                                Log.d(
                                                                                    "ExploreResponse",
                                                                                    "Received explore response: $aiResponse"
                                                                                )
                                                                                // 기존 메시지 리스트를 업데이트
                                                                                val lastIndex =
                                                                                    messages.indexOfLast {
                                                                                        it.text.startsWith(
                                                                                            "📋"
                                                                                        ) && !it.isFromUser
                                                                                    }

                                                                                if (lastIndex >= 0) {
                                                                                    // 기존 검색 결과를 업데이트
                                                                                    val updatedMessages =
                                                                                        messages.toMutableList()
                                                                                    updatedMessages[lastIndex] =
                                                                                        ChatMessage(
                                                                                            text = aiResponse,
                                                                                            isFromUser = false
                                                                                        )
                                                                                    messages =
                                                                                        updatedMessages
                                                                                } else {
                                                                                    // 새로운 검색 결과 추가
                                                                                    messages =
                                                                                        messages + ChatMessage(
                                                                                            text = aiResponse,
                                                                                            isFromUser = false
                                                                                        )
                                                                                }
                                                                            }

                                                                        // 첫 번째 결과를 수동으로 표시
                                                                        if (searchResults.isNotEmpty()) {
                                                                            messages =
                                                                                messages + searchResults[0]

                                                                            // 검색 결과가 여러 개인 경우 네비게이션 표시
                                                                            if (searchResults.size > 1) {
                                                                                showNavigation =
                                                                                    true
                                                                                hasPrevious = false
                                                                                hasNext = true
                                                                                currentPage = 1
                                                                                totalPages =
                                                                                    searchResults.size
                                                                            } else {
                                                                                showNavigation =
                                                                                    false
                                                                            }
                                                                        }
                                                                    } else {
                                                                        val responseMessage =
                                                                            ChatMessage(
                                                                                text = "검색 결과가 없습니다.",
                                                                                isFromUser = false
                                                                            )
                                                                        messages =
                                                                            messages + responseMessage
                                                                        showNavigation = false
                                                                    }
                                                                }
                                                            }

                                                            Toast.makeText(
                                                                activity,
                                                                "탐색이 완료되었습니다.",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        } else {
                                                            Toast.makeText(
                                                                activity,
                                                                "탐색 결과가 없습니다.",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(
                                                        "ExploreError",
                                                        "JSON 파싱 오류: ${e.message}"
                                                    )
                                                    activity.runOnUiThread {
                                                        Toast.makeText(
                                                            activity,
                                                            "응답 처리 중 오류가 발생했습니다.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            } else {
                                                Log.e(
                                                    "ExploreError",
                                                    "HTTP error code: $responseCode"
                                                )
                                                activity.runOnUiThread {
                                                    Toast.makeText(
                                                        activity,
                                                        "탐색 중 오류가 발생했습니다.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }

                                            connection.disconnect()
                                        } catch (e: Exception) {
                                            Log.e("ExploreError", "Error: ${e.message}", e)
                                            activity.runOnUiThread {
                                                Toast.makeText(
                                                    activity,
                                                    "서버 연결에 실패했습니다.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }.start()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFfba064),
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Explore",
                                        modifier = Modifier.size(25.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        "탐색",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Message input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Microphone button
                        IconButton(
                            onClick = {
                                if (speechRecognizer == null) {
                                    Toast.makeText(context, "음성 인식을 사용할 수 없습니다", Toast.LENGTH_SHORT)
                                        .show()
                                    return@IconButton
                                }

                                if (isListening) {
                                    speechRecognizer.stopListening()
                                    isListening = false
                                } else {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        speechRecognizer.startListening(speechRecognizerIntent)
                                        isListening = true
                                    } else {
                                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(
                                    color = if (isListening) Color(0xFFFF5722) else Color(0xFFF0F0F0),
                                    shape = CircleShape
                                )
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.KeyboardVoice,
                                contentDescription = if (isListening) "음성 입력 중지" else "음성 입력",
                                tint = if (isListening) Color.White else Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Text field
// OutlinedTextField 수정
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .onFocusChanged { focusState ->
                                    isTextFieldFocused = focusState.isFocused
                                },
                            placeholder = {
                                Text(
                                    if (isListening) "듣고 있습니다..." else "메시지를 입력하세요...",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isListening) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                                autoCorrect = false
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (messageText.isNotEmpty()) {
                                        focusManager.clearFocus()

                                        sendMessage(
                                            messageText,
                                            activity,
                                            sessionId,
                                            messages,
                                            { newMessages -> messages = newMessages },
                                            userCity,
                                            userDistrict,
                                            context,
                                            locationPermissionLauncher,
                                            listState,
                                            coroutineScope
                                        )
                                        messageText = ""
                                    }
                                }
                            ),
                            maxLines = 3,
                            singleLine = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isListening) Color(0xFFFF5722) else MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = if (isListening) Color(0xFFFF5722).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send button
                        IconButton(
                            onClick = {
                                if (messageText.isNotEmpty()) {
                                    if (isListening) {
                                        speechRecognizer?.stopListening()
                                        isListening = false
                                    }

                                    focusManager.clearFocus()

                                    sendMessage(
                                        messageText,
                                        activity,
                                        sessionId,
                                        messages,
                                        { newMessages -> messages = newMessages },
                                        userCity,
                                        userDistrict,
                                        context,
                                        locationPermissionLauncher,
                                        listState,  // 추가
                                        coroutineScope  // 추가
                                    )
                                    messageText = ""
                                }
                            },
                            modifier = Modifier
                                .background(
                                    color = if (messageText.isNotEmpty()) Color(0xFFc6f584) else Color(
                                        0xFFE0E0E0
                                    ),
                                    shape = CircleShape
                                )
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (messageText.isNotEmpty()) Color.Black else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    navController: NavController
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 뒤로 가기 처리
    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        TopAppBar(
            title = { Text("신청 페이지") },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로 가기"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFFc6f584),
                titleContentColor = Color.Black,
                navigationIconContentColor = Color.Black
            )
        )

        // WebView
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false

                    // JavaScript 팝업 지원 추가
                    settings.javaScriptCanOpenWindowsAutomatically = true

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            canGoBack = view?.canGoBack() ?: false
                        }

                        // 페이지 로딩 에러 처리
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            // 에러 시 빈 페이지 대신 에러 메시지 표시
                            view?.loadData(
                                "<html><body><center><h2>페이지를 불러올 수 없습니다</h2></center></body></html>",
                                "text/html; charset=UTF-8",
                                null
                            )
                        }
                    }

                    // WebChromeClient 설정으로 JavaScript 팝업 처리
                    webChromeClient = object : WebChromeClient() {
                        // JavaScript alert() 처리
                        override fun onJsAlert(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?
                        ): Boolean {
                            AlertDialog.Builder(context)
                                .setTitle("알림")
                                .setMessage(message ?: "")
                                .setPositiveButton("확인") { dialog, _ ->
                                    result?.confirm()
                                    dialog.dismiss()

                                    // "마감된 채용정보입니다" 메시지인 경우 뒤로 가기
                                    if (message?.contains("마감") == true ||
                                        message?.contains("종료") == true) {
                                        navController.navigateUp()
                                    }
                                }
                                .setCancelable(false)
                                .show()

                            return true // true를 반환하여 WebView의 기본 처리를 막음
                        }

                        // JavaScript confirm() 처리
                        override fun onJsConfirm(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?
                        ): Boolean {
                            AlertDialog.Builder(context)
                                .setTitle("확인")
                                .setMessage(message ?: "")
                                .setPositiveButton("확인") { dialog, _ ->
                                    result?.confirm()
                                    dialog.dismiss()
                                }
                                .setNegativeButton("취소") { dialog, _ ->
                                    result?.cancel()
                                    dialog.dismiss()
                                }
                                .setCancelable(false)
                                .show()

                            return true
                        }

                        // 페이지 제목 변경 감지
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            // 제목이 "마감"이나 특정 키워드를 포함하는 경우 처리 가능
                        }
                    }

                    webView = this
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
private fun sendMessage(
    messageText: String,
    activity: MainActivity,
    sessionId: String,
    currentMessages: List<ChatMessage>,
    updateMessages: (List<ChatMessage>) -> Unit,
    userCity: String = "",
    userDistrict: String = "",
    context: Context,
    locationPermissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    listState: LazyListState,  // 파라미터 추가
    coroutineScope: CoroutineScope  // 파라미터 추가
) {
    // 위치 권한 확인
    val fineLocationPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarseLocationPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val hasLocationPermission = fineLocationPermission || coarseLocationPermission

    // 위치 권한이 없거나 위치 정보가 없는 경우
    if (!hasLocationPermission || userCity == "위치 권한 없음" || userDistrict == "위치 권한 없음") {
        // 권한이 영구적으로 거부되었는지 확인
        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) || ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // 권한을 요청한 적이 있고, rationale을 보여줄 필요가 없다면 = "다시 묻지 않음" 상태
        val isPermissionPermanentlyDenied = !hasLocationPermission && !shouldShowRationale &&
                activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getBoolean("location_permission_requested", false)

        if (isPermissionPermanentlyDenied) {
            // "다시 묻지 않음" 상태인 경우 설정으로 안내하는 다이얼로그 표시
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("위치 권한 필요")
                .setMessage("오비서 앱은 사용자님의 지역에 맞는 정보를 제공하기 위해 위치 권한이 필요합니다.\n\n설정에서 위치 권한을 허용해주세요.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
                .setNegativeButton("취소", null)
                .show()
        } else {
            // 권한 요청을 한 적이 있다고 표시
            activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("location_permission_requested", true)
                .apply()

            // 권한 요청 다이얼로그 표시
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        // 메시지 전송 차단
        return
    }

    // 위치 정보가 아직 로드되지 않은 경우
    if (userCity.isEmpty() || userDistrict.isEmpty() || userCity == "위치 확인 중...") {
        Toast.makeText(
            context,
            "위치 정보를 확인 중입니다. 잠시 후 다시 시도해주세요.",
            Toast.LENGTH_SHORT
        ).show()
        // 메시지 전송 차단
        return
    }

    // Create user message
    val userMessage = ChatMessage(
        text = messageText,
        isFromUser = true
    )

    // Create waiting message for AI response
    val waitingMessage = ChatMessage(
        text = "",
        isFromUser = false,
        isWaiting = true
    )

    // Update the messages list first
    updateMessages(currentMessages + userMessage + waitingMessage)

    // 메시지 추가 후 스크롤
    coroutineScope.launch {
        delay(100)
        try {
            listState.animateScrollToItem(
                index = currentMessages.size + 1, // +2 messages (user + waiting)
                scrollOffset = 0
            )
        } catch (e: Exception) {
            Log.e("ChatScreen", "Send message scroll error: ${e.message}")
        }
    }

    // Then send the message to the backend
    activity.onMessageSent(messageText, sessionId)
}


@Composable
fun MessageItem(
    message: ChatMessage,
    navController: NavController,
    activity: MainActivity
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val supabaseHelper = remember { SupabaseDatabaseHelper(context) }

    // 현재 메시지에서 Title 추출을 위한 변수
    var messageTitle by remember { mutableStateOf<String?>(null) }

    // YouTube 썸네일 URL 추출을 위한 패턴
    val thumbnailPattern = """\[THUMBNAIL_URL\](.+?)\[/THUMBNAIL_URL\]""".toRegex()
    val youtubeUrlPattern = """\[YOUTUBE_URL\](.+?)\[/YOUTUBE_URL\]""".toRegex()

    val thumbnailMatch = thumbnailPattern.find(message.text)
    val youtubeUrlMatch = youtubeUrlPattern.find(message.text)

    // 체육시설 소득공제 URL 패턴 확인
    val sportsDeductionUrlPattern = """\[SPORTS_DEDUCTION_URL\](.+?)\[/SPORTS_DEDUCTION_URL\]""".toRegex()
    val sportsDeductionMatch = sportsDeductionUrlPattern.find(message.text)

    // 메시지에서 Title 추출
    LaunchedEffect(message.text) {
        var titlePattern = """📋\s*(.+?)(?:\n|$)""".toRegex()
        var titleMatch = titlePattern.find(message.text)

        if (titleMatch != null) {
            messageTitle = titleMatch.groupValues[1].trim()
        } else {
            titlePattern = """Title:\s*(.+?)(?:\n|$)""".toRegex(RegexOption.IGNORE_CASE)
            titleMatch = titlePattern.find(message.text)

            if (titleMatch != null) {
                messageTitle = titleMatch.groupValues[1].trim()
            } else {
                titlePattern = """🏢\s*(.+?)(?:\n|$)""".toRegex()
                titleMatch = titlePattern.find(message.text)

                if (titleMatch != null) {
                    messageTitle = titleMatch.groupValues[1].trim()
                } else {
                    val firstLine = message.text.split("\n").firstOrNull()?.trim()
                    if (!firstLine.isNullOrEmpty() && firstLine.length < 100) {
                        messageTitle = firstLine
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .wrapContentWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser) Color(0xFFc6f584) else Color(0xFFE0E0E0)
            ),
            shape = RoundedCornerShape(
                topStart = if (message.isFromUser) 12.dp else 2.dp,
                topEnd = if (message.isFromUser) 2.dp else 12.dp,
                bottomStart = 12.dp,
                bottomEnd = 12.dp
            )
        ) {
            if (message.isWaiting) {
                // Show loading indicator for waiting messages
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF4A7C25),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                Column(modifier = Modifier.padding(12.dp)) {
                    when {
                        // 1. 체육시설 소득공제 메시지 처리
                        sportsDeductionMatch != null && !message.isFromUser -> {
                            val sportsDeductionUrl = sportsDeductionMatch.groupValues[1]

                            // URL 마커를 제거한 텍스트 표시
                            val displayText = message.text
                                .replace(sportsDeductionUrlPattern, "")
                                .trim()

                            // 텍스트 표시
                            if (displayText.isNotEmpty()) {
                                Text(
                                    text = displayText,
                                    color = Color.Black,
                                    fontSize = 24.sp,
                                    lineHeight = 29.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // 클릭 가능한 버튼
                            Button(
                                onClick = {
                                    // 웹 브라우저로 URL 열기
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sportsDeductionUrl))
                                    try {
                                        context.startActivity(intent)

                                        // 클릭 기록 저장
                                        coroutineScope.launch {
                                            try {
                                                val identifier = activity.currentUserId
                                                val user = supabaseHelper.getUserByIdentifier(identifier)

                                                if (user != null && user.id != null) {
                                                    val applicationHistory = supabaseHelper.saveApplicationHistory(
                                                        userId = user.id,
                                                        applicationCategory = "체육시설 소득공제",
                                                        applicationContent = "소득공제 체육시설 조회",
                                                        searchHistoryId = activity.chatService.lastSearchHistoryId
                                                    )

                                                    if (applicationHistory != null) {
                                                        Log.d("ApplicationHistory", "체육시설 소득공제 조회 기록 저장 성공")
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("ApplicationHistory", "기록 저장 실패: ${e.message}", e)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "웹 브라우저를 열 수 없습니다.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),  // 녹색 버튼
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "웹사이트 열기",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "소득공제 체육시설 조회하기",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 2. YouTube 썸네일이 있는 경우
                        thumbnailMatch != null && youtubeUrlMatch != null && !message.isFromUser -> {
                            val thumbnailUrl = thumbnailMatch.groupValues[1]
                            val youtubeUrl = youtubeUrlMatch.groupValues[1]

                            // Video ID 추출
                            val videoIdMatch = Regex("(?:v=|/)([a-zA-Z0-9_-]{11})").find(youtubeUrl)
                            val videoId = videoIdMatch?.groupValues?.get(1) ?: ""

                            // 썸네일과 URL 패턴을 제거한 텍스트 표시
                            val displayText = message.text
                                .replace(thumbnailPattern, "")
                                .replace(youtubeUrlPattern, "")
                                .trim()

                            // 텍스트 표시 (있는 경우)
                            if (displayText.isNotEmpty()) {
                                Text(
                                    text = displayText,
                                    color = Color.Black,
                                    fontSize = 24.sp,
                                    lineHeight = 29.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // YouTube 썸네일 표시
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clickable {
                                        // application_history 저장
                                        val searchHistoryId = activity.chatService.lastSearchHistoryId

                                        coroutineScope.launch {
                                            try {
                                                val identifier = activity.currentUserId
                                                val user = supabaseHelper.getUserByIdentifier(identifier)

                                                if (user != null && user.id != null) {
                                                    // YouTube 동영상의 경우 messageTitle이나 displayText에서 제목 추출
                                                    val videoTitle = messageTitle ?: displayText.split("\n").firstOrNull()?.trim() ?: "YouTube 동영상"

                                                    val applicationHistory = supabaseHelper.saveApplicationHistory(
                                                        userId = user.id,
                                                        applicationCategory = "동영상",
                                                        applicationContent = videoTitle,
                                                        searchHistoryId = searchHistoryId
                                                    )

                                                    if (applicationHistory != null) {
                                                        Log.d("ApplicationHistory", "YouTube 동영상 시청 기록 저장 성공: ${applicationHistory.id}")
                                                        Log.d("ApplicationHistory", "동영상 제목: $videoTitle")
                                                    } else {
                                                        Log.e("ApplicationHistory", "YouTube 동영상 시청 기록 저장 실패")
                                                    }
                                                } else {
                                                    Log.e("ApplicationHistory", "사용자 정보를 찾을 수 없음: identifier = $identifier")
                                                }
                                            } catch (e: Exception) {
                                                Log.e("ApplicationHistory", "YouTube 동영상 시청 기록 저장 중 오류: ${e.message}", e)
                                            }
                                        }

                                        // YouTube URL로 이동
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "동영상을 열 수 없습니다.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box {
                                    // 썸네일 URL 상태를 추적
                                    var currentThumbnailUrl by remember { mutableStateOf(thumbnailUrl) }
                                    var thumbnailAttempt by remember { mutableStateOf(0) }

                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(currentThumbnailUrl)
                                            .crossfade(true)
                                            .listener(
                                                onError = { _, _ ->
                                                    // 현재 URL이 실패했을 때 다음 URL 시도
                                                    when (thumbnailAttempt) {
                                                        0 -> {
                                                            // maxresdefault 실패 시 hqdefault 시도
                                                            if (videoId.isNotEmpty()) {
                                                                currentThumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                                                                thumbnailAttempt = 1
                                                            }
                                                        }
                                                        1 -> {
                                                            // hqdefault 실패 시 0.jpg 시도
                                                            if (videoId.isNotEmpty()) {
                                                                currentThumbnailUrl = "https://img.youtube.com/vi/$videoId/0.jpg"
                                                                thumbnailAttempt = 2
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                            .build(),
                                        contentDescription = "YouTube 썸네일",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    // YouTube 재생 아이콘 오버레이
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "재생",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .background(
                                                    Color.Red,
                                                    shape = CircleShape
                                                )
                                                .padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. 기본 메시지 처리
                        else -> {
                            val detailUrlPattern = """\[DETAIL_URL\](.+?)\[/DETAIL_URL\]""".toRegex()
                            val detailUrlMatch = detailUrlPattern.find(message.text)

                            // Detail URL이 있는 경우 제거한 텍스트 표시
                            val displayText = if (detailUrlMatch != null) {
                                message.text.replace(detailUrlPattern, "").trim()
                            } else {
                                message.text
                            }

                            // 전화번호 처리 로직
                            val telPattern = """(?:📞\s*)?전화:\s*(.+?)(?=\n|$)""".toRegex()
                            val telMatches = telPattern.findAll(displayText)

                            if (!message.isFromUser && telMatches.count() > 0) {
                                var currentIndex = 0

                                telMatches.forEach { telMatch ->
                                    val telContent = telMatch.groupValues[1].trim()
                                    val beforePhone = displayText.substring(currentIndex, telMatch.range.first)

                                    if (beforePhone.isNotEmpty()) {
                                        Text(
                                            text = beforePhone,
                                            color = Color.Black,
                                            fontSize = 24.sp,
                                            lineHeight = 29.sp
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "📞 전화: ",
                                            color = Color.Black,
                                            fontSize = 24.sp,
                                            lineHeight = 29.sp
                                        )

                                        val phonePattern = """(\d{2,4}[).\s-]?\d{3,4}[-.\s]?\d{4})""".toRegex()
                                        val phoneMatches = phonePattern.findAll(telContent)

                                        if (phoneMatches.count() > 0) {
                                            var phoneIndex = 0
                                            phoneMatches.forEach { phoneMatch ->
                                                val phoneNumber = phoneMatch.value
                                                val beforePhoneText = telContent.substring(phoneIndex, phoneMatch.range.first)

                                                if (beforePhoneText.isNotEmpty()) {
                                                    Text(
                                                        text = beforePhoneText,
                                                        color = Color.Black,
                                                        fontSize = 24.sp,
                                                        lineHeight = 29.sp
                                                    )
                                                }

                                                Text(
                                                    text = phoneNumber,
                                                    color = Color.Blue,
                                                    fontSize = 24.sp,
                                                    lineHeight = 29.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.clickable {
                                                        val searchHistoryId = activity.chatService.lastSearchHistoryId

                                                        coroutineScope.launch {
                                                            try {
                                                                val identifier = activity.currentUserId
                                                                val user = supabaseHelper.getUserByIdentifier(identifier)

                                                                if (user != null && user.id != null) {
                                                                    val applicationHistory = supabaseHelper.saveApplicationHistory(
                                                                        userId = user.id,
                                                                        applicationCategory = "전화",
                                                                        applicationContent = messageTitle ?: phoneNumber,
                                                                        searchHistoryId = searchHistoryId
                                                                    )

                                                                    if (applicationHistory != null) {
                                                                        Log.d("ApplicationHistory", "전화 기록 저장 성공: ${applicationHistory.id}")
                                                                    }
                                                                }
                                                            } catch (e: Exception) {
                                                                Log.e("ApplicationHistory", "전화 기록 저장 실패: ${e.message}", e)
                                                            }
                                                        }

                                                        val cleanNumber = phoneNumber.replace("[^0-9]".toRegex(), "")
                                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                                            data = Uri.parse("tel:$cleanNumber")
                                                        }
                                                        try {
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(
                                                                context,
                                                                "전화 앱을 열 수 없습니다.",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                )

                                                phoneIndex = phoneMatch.range.last + 1
                                            }

                                            if (phoneIndex < telContent.length) {
                                                Text(
                                                    text = telContent.substring(phoneIndex),
                                                    color = Color.Black,
                                                    fontSize = 24.sp,
                                                    lineHeight = 29.sp
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = telContent,
                                                color = Color.Blue,
                                                fontSize = 24.sp,
                                                lineHeight = 29.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable {
                                                    // 전화 클릭 처리 코드...
                                                }
                                            )
                                        }
                                    }

                                    currentIndex = telMatch.range.last + 1
                                }

                                if (currentIndex < displayText.length) {
                                    Text(
                                        text = displayText.substring(currentIndex),
                                        color = Color.Black,
                                        fontSize = 24.sp,
                                        lineHeight = 29.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = displayText,
                                    color = Color.Black,
                                    fontSize = 24.sp,
                                    lineHeight = 29.sp
                                )
                            }

                            // Detail URL이 있는 경우 신청 버튼 추가
                            if (detailUrlMatch != null && !message.isFromUser) {
                                val detailUrl = detailUrlMatch.groupValues[1]

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        val searchHistoryId = activity.chatService.lastSearchHistoryId

                                        coroutineScope.launch {
                                            try {
                                                val identifier = activity.currentUserId
                                                val user = supabaseHelper.getUserByIdentifier(identifier)

                                                if (user != null && user.id != null) {
                                                    val applicationHistory = supabaseHelper.saveApplicationHistory(
                                                        userId = user.id,
                                                        applicationCategory = "신청",
                                                        applicationContent = messageTitle ?: "신청 페이지",
                                                        searchHistoryId = searchHistoryId
                                                    )

                                                    if (applicationHistory != null) {
                                                        Log.d("ApplicationHistory", "신청 기록 저장 성공: ${applicationHistory.id}")
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("ApplicationHistory", "신청 기록 저장 실패: ${e.message}", e)
                                            }
                                        }

                                        activity.chatService.saveNavigationState()
                                        val encodedUrl = Uri.encode(detailUrl)
                                        navController.navigate("webview/$encodedUrl")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFc6f584),
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "신청하기",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
// ChatMessage를 JSON으로 변환하기 위한 함수 추가
data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isWaiting: Boolean = false
) {
    // JSON으로 변환
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("text", text)
            put("isFromUser", isFromUser)
            put("timestamp", timestamp)
            put("isWaiting", isWaiting)
        }
    }

    companion object {
        // JSON에서 ChatMessage로 변환
        fun fromJson(json: JSONObject): ChatMessage {
            return ChatMessage(
                text = json.getString("text"),
                isFromUser = json.getBoolean("isFromUser"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                isWaiting = json.optBoolean("isWaiting", false)
            )
        }
    }
}