package com.example.comcare

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.semantics.SemanticsProperties.ImeAction
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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
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
import android.os.Build
import android.provider.ContactsContract.CommonDataKinds.Website.URL
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    val chatService = ChatService()
    private var currentUserId: String = "guest" // Default value

    // 위치 관련 변수 추가
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userCity: String = ""
    private var userDistrict: String = ""
    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0
    private var user_add: String = "" // 전체 주소를 저장하는 변수

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "Location"
    }

    fun onMessageSent(message: String, sessionId: String) {
        // Save message to local database, display in UI, etc.

        // Then trigger the n8n workflow
        chatService.sendChatMessageToWorkflow(
            currentUserId,
            message,
            sessionId
        )
    }

    // Add this nested class inside MainActivity
// MainActivity의 PlaceViewModelFactory 클래스 수정
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

        // FusedLocationProviderClient 초기화
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Supabase helper
        val supabaseHelper = SupabaseDatabaseHelper(this)

        setContent {
            // 위치 정보를 State로 관리 - 초기값 설정
            var userCityState by remember { mutableStateOf("위치 확인 중...") }
            var userDistrictState by remember { mutableStateOf("위치 확인 중...") }

            var locationPermissionGranted by remember { mutableStateOf(false) }
            var showLocationPermissionDialog by remember { mutableStateOf(false) }

            // PlaceViewModelFactory는 한 번만 생성
            val viewModelFactory = remember { PlaceViewModelFactory(supabaseHelper) }

            // 위치 권한 런처
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

                if (fineLocationGranted || coarseLocationGranted) {
                    locationPermissionGranted = true
                    // 권한이 승인되면 위치 정보 가져오기
                    getLastKnownLocation { city, district ->
                        userCity = city
                        userDistrict = district
                        // State 업데이트 - 여기가 중요!
                        userCityState = city
                        userDistrictState = district
                        Log.d(TAG, "위치 권한 승인 - 위치 정보 획득 완료")
                        Log.d(TAG, "사용자 위치 State 업데이트: $city $district")
                    }
                } else {
                    // 권한이 거부된 경우
                    showLocationPermissionDialog = true
                    userCityState = "위치 권한 없음"
                    userDistrictState = "위치 권한 없음"
                }
            }

            // 앱 시작 시 위치 권한 확인 및 요청
            LaunchedEffect(Unit) {
                when {
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        // 이미 권한이 있는 경우
                        locationPermissionGranted = true
                        getLastKnownLocation { city, district ->
                            userCity = city
                            userDistrict = district
                            // State 업데이트 - 여기가 중요!
                            userCityState = city
                            userDistrictState = district
                            Log.d(TAG, "기존 위치 권한 있음 - 위치 정보 획득 완료")
                            Log.d(TAG, "사용자 위치 State 업데이트: $city $district")
                        }
                    }
                    else -> {
                        // 권한 요청
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            }

            PlaceComparisonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 위치 권한 안내 다이얼로그
                    if (showLocationPermissionDialog) {
                        AlertDialog(
                            onDismissRequest = { showLocationPermissionDialog = false },
                            title = { Text("위치 권한 필요") },
                            text = {
                                Text("오비서 앱은 사용자님의 지역에 맞는 정보를 제공하기 위해 위치 권한이 필요합니다. 설정에서 위치 권한을 허용해주세요.")
                            },
                            confirmButton = {
                                TextButton(onClick = { showLocationPermissionDialog = false }) {
                                    Text("확인")
                                }
                            }
                        )
                    }

                    val navController = rememberNavController()

                    // Use factory to create ViewModel with Supabase dependency
                    val viewModel: PlaceViewModel = viewModel(factory = viewModelFactory)

                    // 위치 정보가 업데이트될 때마다 ViewModel에 설정
                    LaunchedEffect(userCityState, userDistrictState) {
                        if (userCityState != "위치 확인 중..." &&
                            userCityState != "위치 권한 없음" &&
                            userCityState.isNotEmpty() &&
                            userDistrictState.isNotEmpty()) {
                            viewModel.setUserLocation(userCityState, userDistrictState)
                            Log.d(TAG, "ViewModel에 위치 정보 설정: $userCityState $userDistrictState")
                        }
                    }

                    // 현재 위치 정보 로그
                    Log.d(TAG, "NavHost 렌더링 시점의 위치: City=$userCityState, District=$userDistrictState")

                    NavHost(
                        navController = navController,
                        startDestination = "chat"
                    ) {
                        composable("home") {
                            PlaceComparisonApp(
                                navController = navController,
                                viewModel = viewModel,
                                userCity = userCityState,  // State 값 사용
                                userDistrict = userDistrictState  // State 값 사용
                            )
                        }
                        composable("searchResults") {
                            SearchResultsScreen(
                                viewModel = viewModel,
                                navController = navController
                            )
                        }
                        composable("chat") {
                            // 현재 State 값 로그
                            Log.d(TAG, "ChatScreen으로 전달되는 위치: City=$userCityState, District=$userDistrictState")

                            ChatScreen(
                                activity = this@MainActivity,
                                navController = navController,
                                showBackButton = false,
                                userCity = userCityState,  // State 값 사용
                                userDistrict = userDistrictState  // State 값 사용
                            )
                        }
                    }
                }
            }
        }
    }
    // 위치 정보를 가져오는 함수
    private fun getLastKnownLocation(callback: (String, String) -> Unit) {
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

                // Geocoder를 사용하여 좌표를 주소로 변환
                getAddressFromLocation(location.latitude, location.longitude) { city, district ->
                    callback(city, district)
                }
            } else {
                Log.d(TAG, "마지막 위치 정보가 없음 - 새로운 위치 요청")
                // 마지막 위치가 없는 경우 현재 위치 요청
                requestNewLocationData(callback)
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "위치 정보 획득 실패: ${exception.message}")
            callback("", "")
        }
    }

    // 새로운 위치 데이터 요청
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
            10000L // 10초
        ).apply {
            setMinUpdateIntervalMillis(5000L) // 최소 5초
            setMaxUpdates(1) // 한 번만 업데이트
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
                    }
                } else {
                    Log.w(TAG, "새로운 위치 정보를 가져올 수 없습니다.")
                    callback("", "")
                }
                // 위치 업데이트 중지
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    // 좌표를 주소로 변환하는 함수
    private fun getAddressFromLocation(latitude: Double, longitude: Double, callback: (String, String) -> Unit) {
        try {
            val geocoder = Geocoder(this, Locale.KOREAN)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val city = address.adminArea ?: ""  // 시/도
                        val district = address.subLocality ?: address.locality ?: ""  // 구/군

                        // 전체 주소 생성 및 저장
                        val fullAddress = address.getAddressLine(0) ?: ""
                        user_add = fullAddress

                        // 상세 주소 정보 로그
                        Log.d(TAG, "========== 위치 정보 ==========")
                        Log.d(TAG, "전체 주소 (user_add): $user_add")
//                        Log.d(TAG, "위도: $latitude")
//                        Log.d(TAG, "경도: $longitude")
                        Log.d(TAG, "시/도: $city")
                        Log.d(TAG, "구/군: $district")
//                        Log.d(TAG, "상세 주소 정보:")
//                        Log.d(TAG, "  - 국가: ${address.countryName}")
//                        Log.d(TAG, "  - 시/도 (adminArea): ${address.adminArea}")
//                        Log.d(TAG, "  - 시/군/구 (locality): ${address.locality}")
//                        Log.d(TAG, "  - 동/읍/면 (subLocality): ${address.subLocality}")
//                        Log.d(TAG, "  - 도로명: ${address.thoroughfare}")
//                        Log.d(TAG, "  - 상세주소: ${address.featureName}")
//                        Log.d(TAG, "==============================")

                        callback(city, district)
                    } else {
                        Log.w(TAG, "주소를 찾을 수 없습니다.")
                        user_add = ""
                        callback("", "")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val city = address.adminArea ?: ""  // 시/도
                    val district = address.subLocality ?: address.locality ?: ""  // 구/군

                    // 전체 주소 생성 및 저장
                    val fullAddress = address.getAddressLine(0) ?: ""
                    user_add = fullAddress

                    // 상세 주소 정보 로그
                    Log.d(TAG, "========== 위치 정보 ==========")
//                    Log.d(TAG, "전체 주소 (user_add): $user_add")
//                    Log.d(TAG, "위도: $latitude")
//                    Log.d(TAG, "경도: $longitude")
                    Log.d(TAG, "시/도: $city")
                    Log.d(TAG, "구/군: $district")
//                    Log.d(TAG, "상세 주소 정보:")
//                    Log.d(TAG, "  - 국가: ${address.countryName}")
//                    Log.d(TAG, "  - 시/도 (adminArea): ${address.adminArea}")
//                    Log.d(TAG, "  - 시/군/구 (locality): ${address.locality}")
//                    Log.d(TAG, "  - 동/읍/면 (subLocality): ${address.subLocality}")
//                    Log.d(TAG, "  - 도로명: ${address.thoroughfare}")
//                    Log.d(TAG, "  - 상세주소: ${address.featureName}")
//                    Log.d(TAG, "==============================")

                    callback(city, district)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceComparisonApp(
    navController: NavController,
    viewModel: PlaceViewModel,
    userCity: String = "",  // 파라미터 추가
    userDistrict: String = ""  // 파라미터 추가
) {
    Log.d("PlaceComparisonApp", "받은 위치 정보: City=$userCity, District=$userDistrict")
    var currentSection by remember { mutableStateOf("home") }
    var selectedCity by remember { mutableStateOf("전체") }
    var selectedDistrict by remember { mutableStateOf("전체") }
    var selectedServiceCategory by remember { mutableStateOf("전체") }
    var selectedServiceSubcategory by remember { mutableStateOf("전체") }

    var expandedCityMenu by remember { mutableStateOf(false) }
    var expandedDistrictMenu by remember { mutableStateOf(false) }


    var expandedServiceMenu by remember { mutableStateOf(false) }
    var expandedServiceSubcategoryMenu by remember { mutableStateOf(false) }

    var showFilters by remember { mutableStateOf(false) }

    // Get available districts for the selected city
    val availableDistricts = remember(selectedCity) {
        viewModel.districts.value[selectedCity] ?: listOf("전체")
    }

    // Get available service subcategories for the selected category
    val availableServiceSubcategories = remember(selectedServiceCategory) {
        viewModel.serviceSubcategories.value[selectedServiceCategory] ?: listOf("전체")
    }

    LaunchedEffect(userCity, userDistrict) {
        if (userCity.isNotEmpty() && userDistrict.isNotEmpty() &&
            userCity != "위치 확인 중..." && userCity != "위치 권한 없음") {
            viewModel.setUserLocation(userCity, userDistrict)
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
                    viewModel.fetchLectureData()
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

// Add Chat button - now directly below the navigation buttons
        Button(
            onClick = { navController.navigate("chat") },
            modifier = Modifier
                .fillMaxWidth()  // Make button full width
                .padding(horizontal = 0.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFc6f584),
                contentColor = Color.Black
            ),
            shape = RectangleShape  // Make rectangular
        ) {
            Text(
                "오비서에게 물어보기",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
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
                            // Apply filters
                            viewModel.filterPlaces(
                                selectedCity,
                                selectedDistrict,
                                selectedServiceCategory,
                                selectedServiceSubcategory
                            )
                            // Navigate to results screen
                            navController.navigate("searchResults")
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
                val highlightColor = Color(0xFFf3f04d)

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
                                        color = highlightColor,
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
                                            if (userCity.isNotEmpty() && userDistrict.isNotEmpty()) {
                                                "$userCity $userDistrict 지역의 시설 정보가 없습니다."
                                            } else {
                                                "시설 정보를 불러오는 중..."
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
                                        color = highlightColor,
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
                                                    randomJob.JobTitle ?: "제목 없음",
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
                                        color = highlightColor,
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


// MainActivity.kt의 jobs 섹션 수정 부분

            "jobs" -> {
                // Jobs content with pagination
                Column(modifier = Modifier.fillMaxSize()) {
                    // Section header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "노인 일자리 정보",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "총 ${viewModel.getTotalFilteredJobsCount()}개",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    // Add location filter section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Location Filter Title
                            Text(
                                "위치 검색:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // City and District selection
                            var expandedCityMenu by remember { mutableStateOf(false) }
                            var expandedDistrictMenu by remember { mutableStateOf(false) }
                            var selectedCity by remember { mutableStateOf("전체") }
                            var selectedDistrict by remember { mutableStateOf("전체") }

                            // 통합된 job cities 사용
                            val availableDistricts = remember(selectedCity) {
                                viewModel.jobDistricts.value[selectedCity] ?: listOf("전체")
                            }

                            // Reset district when city changes
                            LaunchedEffect(selectedCity) {
                                selectedDistrict = "전체"
                                viewModel.filterAllJobs(selectedCity, selectedDistrict)
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
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text("▼")
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
                                                        style = MaterialTheme.typography.bodyLarge
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
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text("▼")
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
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                },
                                                onClick = {
                                                    selectedDistrict = district
                                                    expandedDistrictMenu = false
                                                    viewModel.filterAllJobs(selectedCity, selectedDistrict)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Jobs list with pagination
                    val regularJobs = viewModel.filteredJobs.value
                    val kkJobs = viewModel.filteredKKJobs.value
                    val ichJobs = viewModel.filteredICHJobs.value  // ICH jobs 추가

                    // 통합된 일자리 리스트 생성 (ICH jobs 포함)
                    val allJobs = regularJobs + kkJobs + ichJobs

                    if (allJobs.isNotEmpty()) {
                        // Pagination state
                        var currentPage by remember { mutableStateOf(0) }
                        val itemsPerPage = 5
                        val totalPages = ceil(allJobs.size.toFloat() / itemsPerPage).toInt()

                        // Calculate current page items
                        val startIndex = currentPage * itemsPerPage
                        val endIndex = minOf(startIndex + itemsPerPage, allJobs.size)
                        val currentPageItems = allJobs.subList(startIndex, endIndex)

                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Main content area - shows jobs for current page
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
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
                                            is SupabaseDatabaseHelper.ICHJob -> ICHJobCard(ichJob = job)  // ICH job card 추가
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }

                            // Pagination controls
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
                                                // Go to last page of previous group
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

                                // "다음" (next) button if there are more pages
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
                    } else if (viewModel.isLoading || viewModel.isLoadingKKJobs || viewModel.isLoadingICHJobs) {  // ICH jobs 로딩 상태 추가
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
                    }
                }
            }


            "culture" -> {
                // Lectures content with pagination
                Column(modifier = Modifier.fillMaxSize()) {
                    // Section header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "시니어 문화강좌 정보",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "총 ${viewModel.getTotalFilteredCulturesCount()}개",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    // Add location filter section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Location Filter Title
                            Text(
                                "위치 검색:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // City and District selection
                            var expandedCityMenu by remember { mutableStateOf(false) }
                            var expandedDistrictMenu by remember { mutableStateOf(false) }
                            var selectedCity by remember { mutableStateOf("전체") }
                            var selectedDistrict by remember { mutableStateOf("전체") }

                            // 통합된 culture cities 사용
                            val availableDistricts = remember(selectedCity) {
                                viewModel.cultureDistricts.value[selectedCity] ?: listOf("전체")
                            }

                            // Reset district when city changes
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
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text("▼")
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
                                                        style = MaterialTheme.typography.bodyLarge
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
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text("▼")
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
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                },
                                                onClick = {
                                                    selectedDistrict = district
                                                    expandedDistrictMenu = false
                                                    viewModel.filterAllCultures(selectedCity, selectedDistrict)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 통합된 문화 강좌 리스트 (lectures + kk_cultures)
                    val regularLectures = viewModel.filteredLectures.value
                    val kkCultures = viewModel.filteredKKCultures.value

                    // 통합된 문화 강좌 리스트 생성
                    val allCultures = regularLectures + kkCultures

                    if (allCultures.isNotEmpty()) {
                        // Pagination state
                        var currentPage by remember { mutableStateOf(0) }
                        val itemsPerPage = 5
                        val totalPages = ceil(allCultures.size.toFloat() / itemsPerPage).toInt()

                        // Calculate current page items
                        val startIndex = currentPage * itemsPerPage
                        val endIndex = minOf(startIndex + itemsPerPage, allCultures.size)
                        val currentPageItems = allCultures.subList(startIndex, endIndex)

                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Main content area - shows cultures for current page
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
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
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }

                            // Pagination controls
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

                                // "다음" (next) button if there are more pages
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
                    } else if (viewModel.isLoadingLectures || viewModel.isLoadingKKCultures) {
                        // Show loading indicator
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF4A7C25))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("강좌 정보를 불러오는 중...")
                            }
                        }
                    } else {
                        // Show message when no data
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("강좌 정보가 없습니다")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        viewModel.fetchLectureData()
                                        viewModel.fetchKKCulturesData()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFc6f584),
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("새로고침")
                                }
                            }
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar with back button
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

        // Results list
        val itemsPerPage = 5
        var currentPage by remember { mutableStateOf(0) }

        val totalPages = ceil(places.size.toFloat() / itemsPerPage).toInt()

        if (places.isNotEmpty()) {
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

            // 상세정보 버튼
//            Button(
//                onClick = { /* Open comparison view */ },
//                modifier = Modifier.align(Alignment.End),
//                colors = ButtonDefaults.buttonColors(
//                    containerColor = Color(0xFFc6f584),
//                    contentColor = Color.Black
//                ),
//                shape = RectangleShape
//            ) {
//                Text(
//                    "상세정보",
//                    style = MaterialTheme.typography.titleMedium,
//                    fontWeight = FontWeight.Bold
//                )
//            }
        }
    }
}

@Composable
fun JobCard(job: SupabaseDatabaseHelper.Job) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Job Title
            Text(
                text = job.JobTitle ?: "제목 없음",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Job Category
//            Row {
//                Text(
//                    "유형: ",
//                    style = MaterialTheme.typography.bodyLarge,
//                    fontWeight = FontWeight.Bold
//                )
//                Text(
//                    job.JobCategory ?: "정보 없음",
//                    style = MaterialTheme.typography.bodyLarge
//                )
//            }

            Spacer(modifier = Modifier.height(4.dp))

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

            Spacer(modifier = Modifier.height(8.dp))

            // Salary Row
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

            Spacer(modifier = Modifier.height(8.dp))

            // Deadline
            job.Deadline?.let {
                Text(
                    "마감일: $it",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
//                    color = Color(0xFF4A7C25),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun KKJobCard(kkJob: SupabaseDatabaseHelper.KKJob) {
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

            // Category
            Row {
                Text(
                    "카테고리: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    kkJob.Category ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Deadline
            kkJob.Deadline?.let {
                Text(
                    "마감일: $it",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// MainActivity.kt에 추가할 ICHJobCard 컴포저블

@Composable
fun ICHJobCard(ichJob: SupabaseDatabaseHelper.ICHJob) {
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

            // Category
            Row {
                Text(
                    "카테고리: ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    ichJob.Category ?: "정보 없음",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Deadline
            ichJob.Deadline?.let {
                Text(
                    "마감일: $it",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// Update the LectureCard composable to display a clean Institution name
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

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom row with Fee and Quota
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Fee
                Text(
                    "수강료: ${lecture.Fee ?: "무료"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Quota
                Text(
                    "정원: ${lecture.Quota ?: "제한없음"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun KKCultureCard(kkCulture: SupabaseDatabaseHelper.KKCulture) {
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

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom row with Fee and Quota
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Fee (Fees -> Fee로 변경)
                Text(
                    "수강료: ${kkCulture.Fee ?: "무료"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Quota
                Text(
                    "정원: ${kkCulture.Quota ?: "제한없음"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // State information if available
            kkCulture.State?.let { state ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "상태: $state",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when(state) {
                        "모집중" -> Color(0xFF4CAF50)
                        "마감" -> Color(0xFFF44336)
                        else -> Color.Gray
                    }
                )
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
    userDistrict: String = ""
) {
    // Use rememberSaveable to persist state across recompositions
    var messageText by rememberSaveable { mutableStateOf("") }
    var messages by rememberSaveable { mutableStateOf(listOf<ChatMessage>()) }
    val sessionId = rememberSaveable { UUID.randomUUID().toString().replace("-", "") }

    // Speech recognition state
    var isListening by remember { mutableStateOf(false) }

    // Focus manager for keyboard control
    val focusManager = LocalFocusManager.current

    // Navigation state for search results
    val showNavigationState = remember { mutableStateOf(false) }
    val hasPreviousState = remember { mutableStateOf(false) }
    val hasNextState = remember { mutableStateOf(false) }
    val currentPageState = remember { mutableStateOf(0) }
    val totalPagesState = remember { mutableStateOf(0) }

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

    val speechRecognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")  // Korean language
            putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀해주세요...")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Enable partial results for real-time transcription
        }
    }

    // Add a system welcome message on first composition
    LaunchedEffect(Unit) {
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

            // Handle partial results for real-time transcription
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
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
    }

    // Clean up the speech recognizer when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    // Use a key for LazyListState to force recreation when needed
    val listState = rememberLazyListState()

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.size > 1) {
            try {
                delay(100) // Small delay to ensure rendering
                listState.scrollToItem(index = messages.size - 1)
            } catch (e: Exception) {
                Log.e("ChatScreen", "Scroll error: ${e.message}")
            }
        }
    }

    // Set up the callback to receive responses from n8n
    LaunchedEffect(Unit) {
        activity.chatService.responseCallback = { aiResponse ->
            Log.d("ChatScreen", "Received response: $aiResponse")
            // Replace any waiting messages with the actual response
            val updatedMessages = messages.toMutableList()
            val waitingIndex = updatedMessages.indexOfLast { it.isWaiting }

            if (waitingIndex >= 0) {
                updatedMessages[waitingIndex] = ChatMessage(
                    text = aiResponse,
                    isFromUser = false
                )
            } else {
                // Find the last AI message and update it, or add new one
                val lastAiMessageIndex = updatedMessages.indexOfLast { !it.isFromUser && !it.isWaiting }
                if (lastAiMessageIndex >= 0 && showNavigation) {
                    // Update existing AI message when navigating through results
                    updatedMessages[lastAiMessageIndex] = ChatMessage(
                        text = aiResponse,
                        isFromUser = false
                    )
                } else {
                    // Add new AI message for new queries
                    updatedMessages.add(ChatMessage(
                        text = aiResponse,
                        isFromUser = false
                    ))
                }
            }

            messages = updatedMessages
        }

        // Set up navigation callback
        activity.chatService.navigationCallback = { hasPrev, hasNextResult, current, total ->
            hasPrevious = hasPrev
            hasNext = hasNextResult
            currentPage = current
            totalPages = total
            showNavigation = total > 1
        }
    }

    // Clean up callback when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            activity.chatService.responseCallback = null
            activity.chatService.navigationCallback = null
        }
    }

    // Check for microphone permission
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Start listening when permission is granted
            speechRecognizer?.startListening(speechRecognizerIntent)
            isListening = true
        } else {
            // Show a toast if permission is denied
            Toast.makeText(context, "음성 인식을 위해 마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    // Fixed layout structure with appropriate behavior
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
                            modifier = Modifier.size(56.dp) // 버튼 크기 증가
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(32.dp) // 아이콘 크기 증가
                            )
                        }
                    }
                },
                actions = {
                    // Add a button to navigate to the main home screen
                    if (!showBackButton) {
                        IconButton(onClick = {
                            // Navigate to home with section=home parameter
                            navController.navigate("home?section=home") {
                                // Pop up to the start destination to avoid building up a large stack
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = true
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                modifier = Modifier.size(50.dp) // 아이콘 크기 증가
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

            // Messages list - this stays fixed in position
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
                    contentPadding = PaddingValues(top = 16.dp, bottom = if (showNavigation) 80.dp else 16.dp)
                ) {
                    itemsIndexed(messages) { index, message ->
                        MessageItem(message = message)

                        // Add spacing between messages
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Add extra space at the end
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
                                    activity.chatService.showPreviousResult()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFc6f584), // 연두색으로 변경
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp) // 패딩 더 줄임
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Previous",
                                        modifier = Modifier.size(24.dp) // 아이콘 크기 증가
                                    )
                                    Spacer(modifier = Modifier.width(2.dp)) // 간격 줄임
                                    Text(
                                        "이전",
                                        style = MaterialTheme.typography.headlineSmall, // 텍스트 크기 대폭 증가
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            // 첫 번째 결과일 때는 빈 공간
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
                                style = MaterialTheme.typography.headlineMedium, // 텍스트 크기 대폭 증가
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFc6f584) // 연두색으로 변경
                            )
                        }

                        // Next button - 마지막 결과가 아닐 때는 '다음', 마지막일 때는 '탐색'
                        if (hasNext) {
                            Button(
                                onClick = {
                                    activity.chatService.showNextResult()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFc6f584), // 연두색으로 변경
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp) // 패딩 더 줄임
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "다음",
                                        style = MaterialTheme.typography.headlineSmall, // 텍스트 크기 대폭 증가
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(2.dp)) // 간격 줄임
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Next",
                                        modifier = Modifier.size(24.dp) // 아이콘 크기 증가
                                    )
                                }
                            }
                        } else {
                            // ChatScreen의 탐색 버튼 onClick 부분 수정
                            // ChatScreen의 탐색 버튼 onClick 부분 수정
                            Button(
                                onClick = {
                                    // 탐색 시작 토스트 메시지 표시
                                    Toast.makeText(activity, "탐색 시작", Toast.LENGTH_SHORT).show()

                                    // 상세 디버깅 로그
                                    Log.d("ExploreDebug", "=== 탐색 버튼 클릭 ===")
                                    Log.d("ExploreDebug", "userCity: '$userCity'")
                                    Log.d("ExploreDebug", "userDistrict: '$userDistrict'")

                                    // 탐색 버튼 기능 - Flask 서버의 explore 엔드포인트로 연결
                                    Thread {
                                        try {
                                            val url = URL("http://192.168.219.102:5000/explore")
                                            val connection = url.openConnection() as HttpURLConnection
                                            connection.requestMethod = "POST"
                                            connection.setRequestProperty("Content-Type", "application/json")
                                            connection.doOutput = true

                                            // JSON 데이터 생성
                                            val jsonObject = JSONObject().apply {
                                                put("userCity", userCity)
                                                put("userDistrict", userDistrict)
                                            }

                                            Log.d("ExploreDebug", "전송할 JSON: ${jsonObject.toString()}")

                                            // 데이터 전송
                                            connection.outputStream.use { os ->
                                                val input = jsonObject.toString().toByteArray(Charsets.UTF_8)
                                                os.write(input, 0, input.size)
                                            }

                                            // 응답 받기
                                            val responseCode = connection.responseCode
                                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                                val response = connection.inputStream.bufferedReader().use { it.readText() }
                                                Log.d("ExploreResponse", "서버 응답: $response")

                                                // JSON 응답 파싱
                                                try {
                                                    val responseJson = JSONObject(response)
                                                    val generatedQuery = responseJson.optString("generated_query", null)
                                                    val queryResponse = responseJson.optJSONObject("query_response")

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
                                                            val responseType = queryResponse.optString("type")
                                                            when (responseType) {
                                                                "llm" -> {
                                                                    val content = queryResponse.optString("content", "응답 없음")
                                                                    val responseMessage = ChatMessage(
                                                                        text = content,
                                                                        isFromUser = false
                                                                    )
                                                                    messages = messages + responseMessage

                                                                    // LLM 응답은 단일 결과이므로 네비게이션 필요 없음
                                                                    showNavigation = false
                                                                }
                                                                "pinecone" -> {
                                                                    val results = queryResponse.optJSONArray("results")
                                                                    val category = queryResponse.optString("category", "")

                                                                    if (results != null && results.length() > 0) {
                                                                        // 검색 결과를 개별 메시지로 저장
                                                                        val searchResults = mutableListOf<ChatMessage>()

                                                                        for (i in 0 until results.length()) {
                                                                            val result = results.getJSONObject(i)
                                                                            val title = result.optString("title", "제목 없음")
                                                                            val content = result.optString("content", "내용 없음")
                                                                            val resultCategory = result.optString("category", "")

                                                                            val resultText = StringBuilder()
                                                                            resultText.append("📋 ${category} 검색 결과 ${i + 1}/${results.length()}\n\n")
                                                                            resultText.append("🏢 $title\n")
                                                                            if (resultCategory.isNotEmpty()) {
                                                                                resultText.append("📍 $resultCategory\n")
                                                                            }
                                                                            resultText.append("\n$content")

                                                                            searchResults.add(ChatMessage(
                                                                                text = resultText.toString(),
                                                                                isFromUser = false
                                                                            ))
                                                                        }

                                                                        // ChatService를 통해 검색 결과 설정
                                                                        activity.chatService.setSearchResults(searchResults)

                                                                        // 첫 번째 결과 표시
                                                                        if (searchResults.isNotEmpty()) {
                                                                            messages = messages + searchResults[0]

                                                                            // 검색 결과가 여러 개인 경우 네비게이션 표시
                                                                            if (searchResults.size > 1) {
                                                                                showNavigation = true
                                                                                hasPrevious = false
                                                                                hasNext = true
                                                                                currentPage = 1
                                                                                totalPages = searchResults.size
                                                                            } else {
                                                                                showNavigation = false
                                                                            }
                                                                        }
                                                                    } else {
                                                                        val responseMessage = ChatMessage(
                                                                            text = "검색 결과가 없습니다.",
                                                                            isFromUser = false
                                                                        )
                                                                        messages = messages + responseMessage
                                                                        showNavigation = false
                                                                    }
                                                                }
                                                            }

                                                            Toast.makeText(activity, "탐색이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(activity, "탐색 결과가 없습니다.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("ExploreError", "JSON 파싱 오류: ${e.message}")
                                                    activity.runOnUiThread {
                                                        Toast.makeText(activity, "응답 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                Log.e("ExploreError", "HTTP error code: $responseCode")
                                                activity.runOnUiThread {
                                                    Toast.makeText(activity, "탐색 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                                                }
                                            }

                                            connection.disconnect()
                                        } catch (e: Exception) {
                                            Log.e("ExploreError", "Error: ${e.message}", e)
                                            activity.runOnUiThread {
                                                Toast.makeText(activity, "서버 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
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

            // Message input area with fixed position
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                // Set a minimum height for the input area
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
                        // Microphone button with visual feedback for active state
                        IconButton(
                            onClick = {
                                if (speechRecognizer == null) {
                                    Toast.makeText(context, "음성 인식을 사용할 수 없습니다", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }

                                if (isListening) {
                                    // Stop listening if already active
                                    speechRecognizer.stopListening()
                                    isListening = false
                                } else {
                                    // Start listening if not active
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        // Permission already granted, start listening
                                        speechRecognizer.startListening(speechRecognizerIntent)
                                        isListening = true
                                    } else {
                                        // Request permission
                                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(
                                    color = if (isListening) Color(0xFFFF5722) else Color(0xFFF0F0F0),
                                    shape = CircleShape
                                )
                                .size(48.dp) // Slightly larger for better visibility
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.KeyboardVoice,
                                contentDescription = if (isListening) "음성 입력 중지" else "음성 입력",
                                tint = if (isListening) Color.White else Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Text field with improved placeholder and real-time transcription
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
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
                                        // Hide keyboard when sending message
                                        focusManager.clearFocus()

                                        sendMessage(
                                            messageText,
                                            activity,
                                            sessionId,
                                            messages
                                        ) { newMessages ->
                                            messages = newMessages
                                            // Reset navigation when sending new message
                                            showNavigation = false
                                        }
                                        messageText = ""
                                    }
                                }
                            ),
                            maxLines = 3,
                            singleLine = false,
                            // Change border color when listening
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isListening) Color(0xFFFF5722) else MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = if (isListening) Color(0xFFFF5722).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send button - enabled only when there's text to send
                        IconButton(
                            onClick = {
                                if (messageText.isNotEmpty()) {
                                    // Stop listening if active before sending
                                    if (isListening) {
                                        speechRecognizer?.stopListening()
                                        isListening = false
                                    }

                                    // Hide keyboard when sending message
                                    focusManager.clearFocus()

                                    sendMessage(
                                        messageText,
                                        activity,
                                        sessionId,
                                        messages
                                    ) { newMessages ->
                                        messages = newMessages
                                        // Reset navigation when sending new message
                                        showNavigation = false
                                    }
                                    messageText = ""
                                }
                            },
                            modifier = Modifier
                                .background(
                                    color = if (messageText.isNotEmpty()) Color(0xFFc6f584) else Color(0xFFE0E0E0),
                                    shape = CircleShape
                                )
                                .size(48.dp) // Match size with mic button
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

// Helper function to send a message
private fun sendMessage(
    messageText: String,
    activity: MainActivity,
    sessionId: String,
    currentMessages: List<ChatMessage>,
    updateMessages: (List<ChatMessage>) -> Unit
) {
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

    // Then send the message to the backend
    activity.onMessageSent(messageText, sessionId)
}
@Composable
fun MessageItem(message: ChatMessage) {
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
                // Show message text
                Text(
                    text = message.text,
                    modifier = Modifier.padding(12.dp),
                    color = Color.Black,
                    fontSize = 24.sp, // 직접 폰트 크기 지정
                    lineHeight = 29.sp // 줄 간격 추가
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    isListening: Boolean,
    onMicClicked: () -> Unit,
    onSendClicked: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Microphone button for speech-to-text
            IconButton(
                onClick = onMicClicked,
                modifier = Modifier
                    .background(
                        color = if (isListening) Color(0xFFFF5722) else Color(0xFFF0F0F0),
                        shape = CircleShape
                    )
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = if (isListening) Color.White else Color.Black
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                placeholder = { Text("메시지를 입력하세요...") },
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { onSendClicked() }
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSendClicked,
                modifier = Modifier
                    .background(
                        color = Color(0xFFc6f584),
                        shape = CircleShape
                    )
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.Black
                )
            }
        }
    }
}
// Update ChatMessage class to include an isWaiting flag
data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isWaiting: Boolean = false
)