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
import androidx.core.app.ActivityCompat

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
    class PlaceViewModelFactory(
        private val supabaseHelper: SupabaseDatabaseHelper,
        private val userCity: String,
        private val userDistrict: String
    ) : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlaceViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PlaceViewModel(supabaseHelper).apply {
                    // 위치 정보 초기화
                    setUserLocation(userCity, userDistrict)
                } as T
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
            var locationPermissionGranted by remember { mutableStateOf(false) }
            var showLocationPermissionDialog by remember { mutableStateOf(false) }
            var viewModelFactory by remember { mutableStateOf(PlaceViewModelFactory(supabaseHelper, "", "")) }

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
                        viewModelFactory = PlaceViewModelFactory(supabaseHelper, city, district)
                        Log.d(TAG, "위치 권한 승인 - 위치 정보 획득 완료")
                        Log.d(TAG, "사용자 위치: $userCity $userDistrict")
                        Log.d(TAG, "전체 주소: $user_add")
                    }
                } else {
                    // 권한이 거부된 경우
                    showLocationPermissionDialog = true
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
                            viewModelFactory = PlaceViewModelFactory(supabaseHelper, city, district)
                            Log.d(TAG, "기존 위치 권한 있음 - 위치 정보 획득 완료")
                            Log.d(TAG, "사용자 위치: $userCity $userDistrict")
                            Log.d(TAG, "전체 주소: $user_add")
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

                    NavHost(
                        navController = navController,
                        startDestination = "chat"  // Changed to "chat" so the app starts on the chat screen
                    ) {
                        composable("home") {
                            PlaceComparisonApp(
                                navController = navController,
                                viewModel = viewModel,
//                                userCity = userCity,
//                                userDistrict = userDistrict
                            )
                        }
                        composable("searchResults") {
                            SearchResultsScreen(
                                viewModel = viewModel,
                                navController = navController
                            )
                        }
                        composable("chat") {
                            ChatScreen(
                                activity = this@MainActivity,
                                navController = navController,
                                showBackButton = false  // Set to false since this is now the start screen
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
    viewModel: PlaceViewModel
) {
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
                // Define the new color
                val highlightColor = Color(0xFFf3f04d) // The #f3f04d color

                // Replace Column with a scrollable container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                                    // Section Title with updated color
                                    Text(
                                        "오늘의 시설",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = highlightColor, // Updated color
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Get random place if available
                                    if (viewModel.filteredPlaces.value.isNotEmpty()) {
                                        // Get a random place from the filtered places list
                                        val randomPlace = remember(viewModel.filteredPlaces.value) {
                                            viewModel.filteredPlaces.value.random()
                                        }

                                        // Display the random place
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

                                        // New "More" button with right alignment and updated color
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    currentSection = "welfareFacilities"
                                                    showFilters = true
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = highlightColor, // Updated color
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
                                            "시설 정보를 불러오는 중...",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }

                        // Today's Policy Section
//                        item {
//                            Card(
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    .padding(vertical = 8.dp),
//                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
//                            ) {
//                                Column(modifier = Modifier.padding(16.dp)) {
//                                    // Section Title with updated color
//                                    Text(
//                                        "오늘의 정책",
//                                        style = MaterialTheme.typography.titleLarge,
//                                        fontWeight = FontWeight.Bold,
//                                        color = highlightColor, // Updated color
//                                    )
//
//                                    Spacer(modifier = Modifier.height(12.dp))
//
//                                    // Since we don't have actual policy data, display a sample policy
//                                    Text(
//                                        "노인 일자리 사업",
//                                        style = MaterialTheme.typography.titleMedium,
//                                        fontWeight = FontWeight.Bold
//                                    )
//
//                                    Spacer(modifier = Modifier.height(4.dp))
//
//                                    Text(
//                                        "만 65세 이상 노인에게 일자리를 제공하는 정책입니다.",
//                                        style = MaterialTheme.typography.bodyLarge
//                                    )
//
//                                    Spacer(modifier = Modifier.height(4.dp))
//
//                                    Text(
//                                        "지원금: 월 30만원",
//                                        style = MaterialTheme.typography.bodyMedium
//                                    )
//
//                                    Spacer(modifier = Modifier.height(12.dp))
//
//                                    // New "More" button with right alignment and updated color
//                                    Row(
//                                        modifier = Modifier.fillMaxWidth(),
//                                        horizontalArrangement = Arrangement.End
//                                    ) {
//                                        Button(
//                                            onClick = {
//                                                currentSection = "seniorPolicies"
//                                            },
//                                            colors = ButtonDefaults.buttonColors(
//                                                containerColor = highlightColor, // Updated color
//                                                contentColor = Color.Black
//                                            ),
//                                            shape = RoundedCornerShape(8.dp)
//                                        ) {
//                                            Text(
//                                                "더보기",
//                                                fontWeight = FontWeight.Bold
//                                            )
//                                        }
//                                    }
//                                }
//                            }
//                        }

                        // Today's Jobs Section
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Section Title with updated color
                                    Text(
                                        "오늘의 일자리",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = highlightColor, // Updated color
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Get random job if available
                                    if (viewModel.jobs.value.isNotEmpty()) {
                                        // Get a random job from the full list
                                        val randomJob = remember(viewModel.jobs.value) {
                                            viewModel.jobs.value.random()
                                        }

                                        // Display the random job
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

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // New "More" button with right alignment and updated color
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    currentSection = "jobs"
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = highlightColor, // Updated color
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
                                            "일자리 정보를 불러오는 중...",
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
                                    // Section Title with updated color
                                    Text(
                                        "오늘의 문화",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = highlightColor, // Updated color
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Get random lecture if available
                                    if (viewModel.lectures.value.isNotEmpty()) {
                                        // Get a random lecture from the full list
                                        val randomLecture = remember(viewModel.lectures.value) {
                                            viewModel.lectures.value.random()
                                        }

                                        // Display the random lecture
                                        Text(
                                            randomLecture.Title ?: "강좌명 없음",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Extract the clean institution name
                                        val institutionText = randomLecture.Institution?.let {
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
                                                randomLecture.Fees ?: "무료",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
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
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = highlightColor, // Updated color
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
                                            "문화 강좌 정보를 불러오는 중...",
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
                            text = "총 ${viewModel.filteredJobs.value.size}개",
                            style = MaterialTheme.typography.bodyLarge,
//                            color = Color(0xFF4A7C25)
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

                            // City and District selection (reusing existing state from viewModel)
                            var expandedCityMenu by remember { mutableStateOf(false) }
                            var expandedDistrictMenu by remember { mutableStateOf(false) }
                            var selectedCity by remember { mutableStateOf("전체") }
                            var selectedDistrict by remember { mutableStateOf("전체") }

                            // Get available districts for the selected city
                            val availableDistricts = remember(selectedCity) {
                                viewModel.districts.value[selectedCity] ?: listOf("전체")
                            }

                            // Reset district when city changes
                            LaunchedEffect(selectedCity) {
                                selectedDistrict = "전체"
                                viewModel.filterJobs(selectedCity, selectedDistrict)
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
                                                "$selectedCity",
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
                                        viewModel.cities.value.forEach { city ->
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
                                                "$selectedDistrict",
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
                                                    viewModel.filterJobs(selectedCity, selectedDistrict)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

//                            Spacer(modifier = Modifier.height(16.dp))
//
//                            // Apply filter button
//                            Button(
//                                onClick = { viewModel.filterJobs(selectedCity, selectedDistrict) },
//                                modifier = Modifier.fillMaxWidth(),
//                                colors = ButtonDefaults.buttonColors(
//                                    containerColor = Color(0xFFc6f584),
//                                    contentColor = Color.Black
//                                )
//                            ) {
//                                Text(
//                                    "검색하기",
//                                    style = MaterialTheme.typography.titleMedium,
//                                    fontWeight = FontWeight.Bold
//                                )
//                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Jobs list with pagination
                    val jobs = viewModel.filteredJobs.value

                    if (jobs.isNotEmpty()) {
                        // Pagination state
                        var currentPage by remember { mutableStateOf(0) }
                        val itemsPerPage = 5
                        val totalPages = ceil(jobs.size.toFloat() / itemsPerPage).toInt()

                        // Calculate current page items
                        val startIndex = currentPage * itemsPerPage
                        val endIndex = minOf(startIndex + itemsPerPage, jobs.size)
                        val currentPageItems = jobs.subList(startIndex, endIndex)

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
                                        JobCard(job = job)
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
                    } else if (viewModel.isLoading) {
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
//                    else {
//                        // Show message when no data
//                        Box(
//                            modifier = Modifier.fillMaxSize(),
//                            contentAlignment = Alignment.Center
//                        ) {
//                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                                Text("일자리 정보가 없습니다")
//                                Spacer(modifier = Modifier.height(16.dp))
//                                Button(
//                                    onClick = {
//                                        viewModel.fetchJobsData()
//                                        // Reset filters
//                                        viewModel.filterJobs("전체", "전체")
//                                    },
//                                    colors = ButtonDefaults.buttonColors(
//                                        containerColor = Color(0xFFc6f584),
//                                        contentColor = Color.Black
//                                    )
//                                ) {
//                                    Text("새로고침")
//                                }
//                            }
//                        }
//                    }
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
                            text = "총 ${viewModel.filteredLectures.value.size}개",
                            style = MaterialTheme.typography.bodyLarge,
//                            color = Color(0xFF4A7C25)
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

                            // City and District selection (reusing existing state from viewModel)
                            var expandedCityMenu by remember { mutableStateOf(false) }
                            var expandedDistrictMenu by remember { mutableStateOf(false) }
                            var selectedCity by remember { mutableStateOf("전체") }
                            var selectedDistrict by remember { mutableStateOf("전체") }

                            // Get available districts for the selected city
                            val availableDistricts = remember(selectedCity) {
                                viewModel.districts.value[selectedCity] ?: listOf("전체")
                            }

                            // Reset district when city changes
                            LaunchedEffect(selectedCity) {
                                selectedDistrict = "전체"
                                viewModel.filterLectures(selectedCity, selectedDistrict)
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
                                                "$selectedCity",
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
                                        viewModel.cities.value.forEach { city ->
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
                                                "$selectedDistrict",
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
                                                    viewModel.filterLectures(selectedCity, selectedDistrict)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

//                            Spacer(modifier = Modifier.height(16.dp))

                            // Apply filter button
//                            Button(
//                                onClick = { viewModel.filterLectures(selectedCity, selectedDistrict) },
//                                modifier = Modifier.fillMaxWidth(),
//                                colors = ButtonDefaults.buttonColors(
//                                    containerColor = Color(0xFFc6f584),
//                                    contentColor = Color.Black
//                                )
//                            ) {
//                                Text(
//                                    "검색하기",
//                                    style = MaterialTheme.typography.titleMedium,
//                                    fontWeight = FontWeight.Bold
//                                )
//                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Lectures list with pagination
                    val lectures = viewModel.filteredLectures.value.sortedByDescending { it.Id }

                    if (lectures.isNotEmpty()) {
                        // Pagination state
                        var currentPage by remember { mutableStateOf(0) }
                        val itemsPerPage = 5
                        val totalPages = ceil(lectures.size.toFloat() / itemsPerPage).toInt()

                        // Calculate current page items
                        val startIndex = currentPage * itemsPerPage
                        val endIndex = minOf(startIndex + itemsPerPage, lectures.size)
                        val currentPageItems = lectures.subList(startIndex, endIndex)

                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Main content area - shows lectures for current page
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
                                    items(currentPageItems) { lecture ->
                                        LectureCard(lecture = lecture)
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
                    } else if (viewModel.isLoadingLectures) {
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
                                    onClick = { viewModel.fetchLectureData() },
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
                    "수강료: ${lecture.Fees ?: "무료"}",
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    activity: MainActivity,
    navController: NavController,
    showBackButton: Boolean = true
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
                title = { Text("실버랜드 오비서") },
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
                                contentDescription = "Home"
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
                            // 마지막 결과일 때는 '탐색' 버튼
                            Button(
                                onClick = {
                                    // 탐색 버튼 기능 - 처음으로 돌아가거나 추가 검색 등
                                    activity.chatService.showResultAtIndex(0) // 첫 번째 결과로 이동
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFfba064), // 연두색으로 변경
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
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Explore",
                                        modifier = Modifier.size(25.dp) // 아이콘 크기 증가
                                    )
                                    Spacer(modifier = Modifier.width(2.dp)) // 간격 줄임
                                    Text(
                                        "탐색",
                                        style = MaterialTheme.typography.headlineSmall, // 텍스트 크기 대폭 증가
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