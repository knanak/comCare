package com.example.comcare

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.util.*
import kotlin.math.ceil

class MainActivity : ComponentActivity() {

    val chatService = ChatService()
    private var currentUserId: String = "guest" // Default value

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
    class PlaceViewModelFactory(private val supabaseHelper: SupabaseDatabaseHelper) : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlaceViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PlaceViewModel(supabaseHelper) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Supabase helper
        val supabaseHelper = SupabaseDatabaseHelper(this)

        // Create ViewModel factory
        val viewModelFactory = PlaceViewModelFactory(supabaseHelper)

        setContent {
            PlaceComparisonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // Use factory to create ViewModel with Supabase dependency
                    val viewModel: PlaceViewModel = viewModel(factory = viewModelFactory)

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            PlaceComparisonApp(
                                navController = navController,
                                viewModel = viewModel
                            )
                        }
                        composable("searchResults") {
                            SearchResultsScreen(
                                viewModel = viewModel,
                                navController = navController
                            )
                        }
                        // Add chat screen to navigation
                        composable("chat") {
                            ChatScreen(activity = this@MainActivity, navController = navController)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceComparisonApp(
    navController: NavController,
    viewModel: PlaceViewModel
) {
    var selectedCity by remember { mutableStateOf("전체") }
    var selectedDistrict by remember { mutableStateOf("전체") }
    var selectedServiceCategory by remember { mutableStateOf("전체") }
    var selectedServiceSubcategory by remember { mutableStateOf("전체") }

    var expandedCityMenu by remember { mutableStateOf(false) }
    var expandedDistrictMenu by remember { mutableStateOf(false) }
    var expandedServiceMenu by remember { mutableStateOf(false) }
    var expandedServiceSubcategoryMenu by remember { mutableStateOf(false) }

    // State for showing/hiding the filters
    var showFilters by remember { mutableStateOf(false) }

    // State for the current active section
    var currentSection by remember { mutableStateOf("longTermCare") }

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
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(1.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFc6f584)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "실버 놀이터",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Black
                    )
                }
            }
        }

        // Navigation buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Button 1 - Long-term Care Facilities
            Button(
                onClick = {
                    currentSection = "welfareFacilities"
                    showFilters = !showFilters  // Toggle filters visibility
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if(currentSection == "welfareFacilities") Color(0xFF9BC95B) else Color(0xFFc6f584),
                    contentColor = Color.Black
                )
            ) {
                Text("시설")
            }

            // Button 2 - Senior Policies
            Button(
                onClick = {
                    currentSection = "seniorPolicies"
                    showFilters = false
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if(currentSection == "seniorPolicies") Color(0xFF9BC95B) else Color(0xFFc6f584),
                    contentColor = Color.Black
                )
            ) {
                Text("정책")
            }

            // Button 3 - Jobs
            Button(
                onClick = {
                    currentSection = "jobs"
                    showFilters = false
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if(currentSection == "jobs") Color(0xFF9BC95B) else Color(0xFFc6f584),
                    contentColor = Color.Black
                )
            ) {
                Text("일자리")
            }

            // Button 4 - Welfare Facilities
            Button(
                onClick = {
                    currentSection = "welfareFacilities"
                    showFilters = !showFilters  // Toggle filters visibility
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if(currentSection == "welfareFacilities") Color(0xFF9BC95B) else Color(0xFFc6f584),
                    contentColor = Color.Black
                )
            ) {
                Text("문화")
            }
        }

        // Add Chat button
        Button(
            onClick = { navController.navigate("chat") },
            modifier = Modifier
                .align(Alignment.End)
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFc6f584),
                contentColor = Color.Black
            )
        ) {
            Text("채팅 문의")
        }

        // Show filters only when button 4 is pressed and showFilters is true
        if (showFilters && currentSection == "welfareFacilities") {
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
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(1.dp))

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
                                    Text("$selectedCity", color = Color(0xFF4A7C25))
                                    Text("▼", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4A7C25))
                                }
                            }

                            DropdownMenu(
                                expanded = expandedCityMenu,
                                onDismissRequest = { expandedCityMenu = false }
                            ) {
                                viewModel.cities.value.forEach { city ->
                                    DropdownMenuItem(
                                        text = { Text(city) },
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
                                    Text("$selectedDistrict", color = Color(0xFF4A7C25))
                                    Text("▼", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4A7C25))
                                }
                            }

                            DropdownMenu(
                                expanded = expandedDistrictMenu,
                                onDismissRequest = { expandedDistrictMenu = false }
                            ) {
                                availableDistricts.forEach { district ->
                                    DropdownMenuItem(
                                        text = { Text(district) },
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

                    Spacer(modifier = Modifier.height(3.dp))

                    // Service Filters (Category and Subcategory side by side)
                    Text(
                        "서비스:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(1.dp))

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
                                    Text("$selectedServiceCategory", color = Color(0xFF4A7C25))
                                    Text("▼", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4A7C25))
                                }
                            }

                            DropdownMenu(
                                expanded = expandedServiceMenu,
                                onDismissRequest = { expandedServiceMenu = false }
                            ) {
                                viewModel.serviceCategories.value.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category) },
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
                                    Text("$selectedServiceSubcategory", color = Color(0xFF4A7C25))
                                    Text("▼", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4A7C25))
                                }
                            }

                            DropdownMenu(
                                expanded = expandedServiceSubcategoryMenu,
                                onDismissRequest = { expandedServiceSubcategoryMenu = false }
                            ) {
                                availableServiceSubcategories.forEach { subcategory ->
                                    DropdownMenuItem(
                                        text = { Text(subcategory) },
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

                    // Add search button
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
                        Text("검색하기")
                    }
                }
            }
        }

        // Content based on the current section
        when (currentSection) {
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
                // Jobs content
                Text(
                    "노인 일자리 정보",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("노인 일자리 정보 섹션")
                }
            }
            "welfareFacilities" -> {
                // Welfare facilities content
                Text(
                    "복지시설 정보",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                // If filters are shown, display facilities after applying filters
                if (!showFilters) {
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
                            Text("선택한 조건에 맞는 시설이 없습니다.")
                        }
                    }
                } else {
                    // Show prompt to use filters
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("복지시설을 검색하려면 필터를 사용하세요.")
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showFilters = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFc6f584),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("필터 보기")
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
            // Top row with name and rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    place.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (place.rating.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "평가등급: ",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            place.rating,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when(place.rating) {
                                "A" -> Color(0xFF4CAF50)
                                "B" -> Color(0xFF2196F3)
                                "C" -> Color(0xFFFFC107)
                                "D" -> Color(0xFFFF9800)
                                "E" -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (place.rating_year.isNotEmpty()) {
                            Text(
                                " (${place.rating_year})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Address
            Text(
                "주소: ${place.address}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Services
            if (place.service1.isNotEmpty()) {
                Text(
                    "시설 종류: ${place.service1.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (place.service2.isNotEmpty()) {
                Text(
                    "시설 유형: ${place.facilityKind}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Capacity - only show if values are not all zeros
            if (place.full != "0" || place.now != "0" || place.wating != "0") {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "정원: ${place.full}명",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "현재: ${place.now}명",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "대기: ${place.wating}명",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Button
            Button(
                onClick = { /* Open comparison view */ },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFc6f584),
                    contentColor = Color.Black
                )
            ) {
                Text("상세정보")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(activity: MainActivity, navController: NavController) {
    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    val sessionId = remember { UUID.randomUUID().toString().replace("-", "") }

    // Set up the callback to receive responses from n8n
    LaunchedEffect(Unit) {
        activity.chatService.responseCallback = { aiResponse ->
            Log.d("ChatScreen", "Received AI response: $aiResponse")
            // Add the AI response to the messages list
            messages = messages + ChatMessage(
                text = aiResponse,
                isFromUser = false
            )
        }
    }

    // Clean up callback when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            activity.chatService.responseCallback = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                text = "채팅 문의",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Divider()

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            items(messages) { message ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .align(if (message.isFromUser) Alignment.End else Alignment.Start),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.isFromUser) Color(0xFFc6f584) else Color(0xFFE0E0E0)
                    )
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(12.dp),
                        color = Color.Black
                    )
                }
            }
        }

        // Input field and send button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("메시지를 입력하세요...") }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (messageText.isNotEmpty()) {
                        // Add user message to the list
                        val newMessage = ChatMessage(
                            text = messageText,
                            isFromUser = true
                        )
                        messages = messages + newMessage

                        // Send message to n8n webhook
                        activity.onMessageSent(messageText, sessionId)

                        // Clear input field
                        messageText = ""

                        // Add a "waiting" message that will be replaced by the real response
                        messages = messages + ChatMessage(
                            text = "...",
                            isFromUser = false,
                            isWaiting = true
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFc6f584),
                    contentColor = Color.Black
                )
            ) {
                Text("전송")
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
