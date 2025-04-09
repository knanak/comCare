package com.example.comcare

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.Exception

import com.example.comcare.OPEN_API_KEY




class PlaceViewModel : ViewModel() {
    // Using MutableState for the places list
    private val _allPlaces = mutableStateOf<List<Place>>(emptyList())
    private val _filteredPlaces = mutableStateOf<List<Place>>(emptyList())
    val filteredPlaces: State<List<Place>> = _filteredPlaces

    // Location hierarchy
    private val _cities = mutableStateOf<List<String>>(listOf("전체"))
    val cities: State<List<String>> = _cities

    private val _districts = mutableStateOf<Map<String, List<String>>>(mapOf("전체" to listOf("전체")))
    val districts: State<Map<String, List<String>>> = _districts

    private fun processLocationCategories(places: List<Place>) {
        // Extract unique cities from addresses
        val citySet = mutableSetOf<String>()
        val districtMap = mutableMapOf<String, MutableSet<String>>()

        // Always include "전체" (All) option
        citySet.add("전체")
        districtMap["전체"] = mutableSetOf("전체")

        // Extract city and district information from places
        places.forEach { place ->
            // Process the address to extract city
            val addressParts = place.address.split(" ")
            if (addressParts.size >= 2) {
                val city = addressParts[0] // Usually the first part is the city (e.g., "서울특별시")
                val district = place.district // Use the district field directly

                if (city.isNotEmpty()) {
                    // Add city if not already added
                    citySet.add(city)

                    // Initialize district set for this city if needed
                    if (!districtMap.containsKey(city)) {
                        districtMap[city] = mutableSetOf("전체")
                    }

                    // Add district to the city's district set
                    if (district.isNotEmpty()) {
                        districtMap[city]?.add(district)
                    }
                }
            }
        }

        // Convert sets to sorted lists for UI
        _cities.value = citySet.toList().sorted()

        // Ensure "전체" is always first in cities list
        if (_cities.value.contains("전체")) {
            val citiesList = _cities.value.toMutableList()
            citiesList.remove("전체")
            citiesList.add(0, "전체")
            _cities.value = citiesList
        }

        // Convert district sets to sorted lists with "전체" always first
        val districtMapSorted = mutableMapOf<String, List<String>>()
        districtMap.forEach { (city, districts) ->
            // Create a sorted list of districts
            val sortedDistricts = districts.filter { it != "전체" }.sorted().toMutableList()

            // Add "전체" at the beginning
            sortedDistricts.add(0, "전체")

            districtMapSorted[city] = sortedDistricts
        }
        _districts.value = districtMapSorted

        // Log the extracted location data
        Log.d("LocationCategories", "Extracted cities: ${_cities.value}")
        _districts.value.forEach { (city, districts) ->
            Log.d("LocationCategories", "City: $city, Districts: $districts")
        }
    }

    // Service hierarchy
    private val _serviceCategories = mutableStateOf<List<String>>(emptyList())
    val serviceCategories: State<List<String>> = _serviceCategories

    private val _serviceSubcategories = mutableStateOf<Map<String, List<String>>>(emptyMap())
    val serviceSubcategories: State<Map<String, List<String>>> = _serviceSubcategories

    init {
        // Fetch data when ViewModel is initialized
        fetchPlacesFromApi()
    }

    private fun fetchPlacesFromApi() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting API data fetch")
                val apiData = withContext(Dispatchers.IO) {
                    fetchApiData()
                }

                // Process data to extract categories
                processLocationCategories(apiData)
                processServiceCategories(apiData)


                _allPlaces.value = apiData
                _filteredPlaces.value = apiData
                Log.d("PlaceViewModel", "API data fetched successfully: ${apiData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching API data", e)

                // Fallback to sample data if API fails
                Log.d("PlaceViewModel", "Falling back to sample data")
                val sampleData = getSampleData()
                _allPlaces.value = sampleData
                _filteredPlaces.value = sampleData

                // Process sample data as well
                processLocationCategories(sampleData)
                processServiceCategories(sampleData)

            }
        }
    }
    private fun processServiceCategories(places: List<Place>) {
        // Extract all distinct facility detail types from the data without adding "전체"
        val categories = places.map { it.facilityKindDetail }.distinct().filter { it.isNotEmpty() }
        _serviceCategories.value = categories

        // Create subcategories map without "전체"
        val subcategoriesMap = mutableMapOf<String, List<String>>()

        // Group facilities by their detail type to create subcategories
        places.groupBy { it.facilityKindDetail }
            .forEach { (category, placesInCategory) ->
                if (category.isNotEmpty()) {
                    // Find distinct service types for this category
                    val subCategories = placesInCategory
                        .flatMap { place ->
                            // Handle service2 as a List<String> and normalize each item
                            place.service2.map { serviceName ->
                                normalizeServiceName(serviceName)
                            }
                        }
                        .distinct()
                        .filter { it.isNotEmpty() }

                    subcategoriesMap[category] = subCategories
                }
            }

        _serviceSubcategories.value = subcategoriesMap
    }

    // Helper function to normalize service names
    private fun normalizeServiceName(serviceName: String): String {
        // If it contains "seniorcenter" with parentheses, normalize to just "seniorcenter"
        // Adjust these patterns to match your actual Korean text if needed
        val seniorCenterPattern = "(노인복지관)\\s*\\([^)]*\\)".toRegex()
        if (seniorCenterPattern.containsMatchIn(serviceName)) {
            return "노인복지관"
        }

        // For English text if needed
        if (serviceName.contains("seniorcenter") && serviceName.contains("(")) {
            return "seniorcenter"
        }

        return serviceName
    }

    private suspend fun fetchApiData(): List<Place> {
        return withContext(Dispatchers.IO) {
            try {
                // Define API parameters as variables
                val apiKey = OPEN_API_KEY
                val dataFormat = "xml"
                val serviceName = "fcltOpenInfo_OWI"

                // First, make a small request to get the total count
                val countUrlBuilder = StringBuilder("http://openapi.seoul.go.kr:8088")
                countUrlBuilder.append("/" + URLEncoder.encode(apiKey, "UTF-8"))
                countUrlBuilder.append("/" + URLEncoder.encode(dataFormat, "UTF-8"))
                countUrlBuilder.append("/" + URLEncoder.encode(serviceName, "UTF-8"))
                countUrlBuilder.append("/" + URLEncoder.encode("1", "UTF-8"))
                countUrlBuilder.append("/" + URLEncoder.encode("1", "UTF-8"))

                val countUrl = URL(countUrlBuilder.toString())
                val countConn = countUrl.openConnection() as HttpURLConnection
                countConn.requestMethod = "GET"

                val countResponse = BufferedReader(InputStreamReader(countConn.inputStream)).use { rd ->
                    val sb = StringBuilder()
                    var line: String?
                    while (rd.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    sb.toString()
                }

                // Parse list_total_count from the response
                val dbFactory = DocumentBuilderFactory.newInstance()
                val dBuilder = dbFactory.newDocumentBuilder()
                val doc = dBuilder.parse(countResponse.byteInputStream())
                doc.documentElement.normalize()

                val totalCountNodes = doc.getElementsByTagName("list_total_count")
                var totalCount = 1000 // Default fallback value

                if (totalCountNodes.length > 0) {
                    totalCount = totalCountNodes.item(0).textContent.toInt()
                    Log.d("PlaceViewModel", "list_total_count found: $totalCount")
                } else {
                    Log.d("PlaceViewModel", "list_total_count not found, using default value")
                }

                // Now build the URL for the actual data request
                val startIndex = "1"
                val endIndex = totalCount.toString()

                val urlBuilder = StringBuilder("http://openapi.seoul.go.kr:8088")
                urlBuilder.append("/" + URLEncoder.encode(apiKey, "UTF-8"))
                urlBuilder.append("/" + URLEncoder.encode(dataFormat, "UTF-8"))
                urlBuilder.append("/" + URLEncoder.encode(serviceName, "UTF-8"))
                urlBuilder.append("/" + URLEncoder.encode(startIndex, "UTF-8"))
                urlBuilder.append("/" + URLEncoder.encode(endIndex, "UTF-8"))

                val finalUrl = urlBuilder.toString()
                Log.d("PlaceViewModel", "API URL with dynamic endIndex=$endIndex: $finalUrl")

                // Make the actual request
                val url = URL(finalUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Content-type", "application/xml")

                val responseCode = conn.responseCode
                Log.d("PlaceViewModel", "API response code: $responseCode")

                val xmlResponse = if (responseCode in 200..300) {
                    BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { rd ->
                        val sb = StringBuilder()
                        var line: String?
                        while (rd.readLine().also { line = it } != null) {
                            sb.append(line)
                        }
                        sb.toString()
                    }
                } else {
                    Log.e("PlaceViewModel", "API response error: $responseCode")
                    throw Exception("API call failed with response code: $responseCode")
                }

                // Parse the response into a list of Place objects
                val places = parseApiResponse(xmlResponse)
                Log.d("PlaceViewModel", "Parsed ${places.size} places from API response")

                // Return the list of places
                places

            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Exception in fetchApiData", e)
                // Return an empty list rather than rethrowing
                emptyList<Place>()
            }
        }
    }

    private fun parseApiResponse(xmlResponse: String): List<Place> {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(xmlResponse.byteInputStream())
        doc.documentElement.normalize()

        val places = mutableListOf<Place>()
        val excludedPlaces = mutableListOf<String>()
        val rowList = doc.getElementsByTagName("row")

        for (i in 0 until rowList.length) {
            val rowNode = rowList.item(i) as Element

            val address = getElementValue(rowNode, "FCLT_ADDR")
            val name = getElementValue(rowNode, "FCLT_NM")

            // Skip if address doesn't contain "서울" (Seoul)
            if (!address.contains("서울")) {
                excludedPlaces.add("$name ($address)")
                continue
            }

            val fcltKindNm = getElementValue(rowNode, "FCLT_KIND_NM")
            val service2Values = determineServiceDetails(fcltKindNm)

            val fcltKindDetailNm = getElementValue(rowNode, "FCLT_KIND_DTL_NM")
            val service1Values = determineServices(fcltKindDetailNm)

            Log.d("ApiParser", "FCLT_KIND_NM: $fcltKindNm")
            Log.d("ApiParser", "Extracted service2: ${service2Values.joinToString()}")
            Log.d("ApiParser", "FCLT_KIND_DTL_NM (service1): $fcltKindDetailNm")

            val place = Place(
                id = (i + 1).toString(),
                name = name,
                facilityCode = getElementValue(rowNode, "FCLT_CD"),
                facilityKind = fcltKindNm,
                facilityKindDetail = fcltKindDetailNm,
                district = getElementValue(rowNode, "JRSD_SGG_NM"),
                address = address,
                tel = getElementValue(rowNode, "FCLT_TEL_NO").ifEmpty { " 없음" },
                zipCode = getElementValue(rowNode, "FCLT_ZIPCD"),
                // Derived values
                service2 = service2Values,
                service1 = service1Values,

                // Default values for fields not in API
                full = (80..200).random().toString(),
                now = (60..150).random().toString(),
                wating = (0..20).random().toString(),
                bus = determineTransportation(address)
            )

            places.add(place)
        }

        // Log summary of excluded places
        Log.d("ApiParser", "Excluded ${excludedPlaces.size} non-Seoul places:")
        excludedPlaces.forEach {
            Log.d("ApiParser", "Excluded: $it")
        }

        // Log summary of included places
        Log.d("ApiParser", "Included ${places.size} Seoul places")

        // Log a summary of all service2 values
        val allService2Values = places.flatMap { it.service2 }.distinct()
        Log.d("ApiParser", "All unique service2 values: ${allService2Values.joinToString()}")

        return places
    }

    private fun getElementValue(element: Element, tagName: String): String {
        val nodeList = element.getElementsByTagName(tagName)
        if (nodeList.length > 0) {
            return nodeList.item(0).textContent
        }
        return ""
    }

    private fun determineServices(facilityKind: String): List<String> {
        // Simply return the input as a single-item list
        // Duplicates are excluded because we're creating a new list each time
        return if (facilityKind.isNotEmpty()) {
            listOf(facilityKind)
        } else {
            emptyList()
        }
    }

    private fun determineServiceDetails(facilityKindNM: String): List<String> {
        // Pattern to match content after closing parenthesis and space
        val afterParenthesesPattern = "\\)\\s+(.+)$".toRegex()
        val match = afterParenthesesPattern.find(facilityKindNM)

        // If we found content after parentheses, use it directly
        if (match != null && match.groupValues.size > 1) {
            val content = match.groupValues[1].trim()
            if (content.isNotEmpty()) {
                // Log the extraction for debugging
                Log.d("ServiceExtraction", "Original: $facilityKindNM, Extracted: $content")
                // Return the extracted content directly
                return listOf(content)
            }
        }

        // Fallback for when there's no match
        Log.d("ServiceExtraction", "No match found for: $facilityKindNM")

        // Just return the original value without the "노인-" prefix
        // This will be displayed directly in the dropdown
        return listOf(facilityKindNM)
    }

//    private fun determineRating(): String {
//        // Randomly assign ratings for demo purposes
//        return listOf("A", "B", "C").random()
//    }

    private fun determineTransportation(address: String): String {
        // Create some demo transportation info based on address
        val districts = listOf("강남", "강북", "강서", "강동", "서초", "성북", "중랑")
        val stations = listOf("역", "정류장", "터미널")
        val lines = listOf("1호선", "2호선", "3호선", "4호선", "5호선", "6호선", "7호선", "8호선", "9호선")
        val exits = listOf("1번", "2번", "3번", "4번", "5번")
        val distances = listOf("도보 3분", "도보 5분", "도보 10분", "버스 5분", "버스 10분")

        // Try to match district from address
        var matchedDistrict = "인근"
        for (district in districts) {
            if (address.contains(district)) {
                matchedDistrict = district
                break
            }
        }

        val station = "${matchedDistrict}${stations.random()}"
        val details = if (stations.random() == "역") {
            "${lines.random()} ${station} ${exits.random()} 출구에서 ${distances.random()}"
        } else {
            "${station}에서 ${distances.random()}"
        }

        return details
    }

    // Sample data for fallback
    private fun getSampleData(): List<Place> {
        return listOf(
            Place(
                id = "1",
                name = "성북구립 상월곡실버복지센터",
                facilityCode = "A0495",
                facilityKind = "(노인복지시설) 노인복지관(소규모)",
                facilityKindDetail = "노인여가복지시설",
                district = "성북구",
                address = "서울특별시 성북구 화랑로18길 6 (상월곡동)",
                tel = "정보 없음",
                zipCode = "",
                service1 = listOf("노인-복지관"),
                service2 = listOf("노인-여가복지"),
//                rating = "A",
                rating_year = "2023",
                full = "100",
                now = "85",
                wating = "0",
                bus = "화랑대역 2번 출구에서 도보 10분"
            ),
            Place(
                id = "2",
                name = "서초구립양재노인종합복지관",
                facilityCode = "A0724",
                facilityKind = "(노인복지시설) 노인복지관",
                facilityKindDetail = "노인여가복지시설",
                district = "서초구",
                address = "서울특별시 서초구 강남대로30길",
                tel = "정보 없음",
                zipCode = "",
                service1 = listOf("노인-복지관"),
                service2 = listOf("노인-여가복지"),
//                rating = "A",
                rating_year = "2023",
                full = "150",
                now = "120",
                wating = "5",
                bus = "양재역 4번 출구에서 도보 15분"
            )
        )
    }

    // Add these properties to track last search criteria
    var lastSearchCity: String = "전체"
    var lastSearchDistrict: String = "전체"
    var lastSearchServiceCategory: String = "전체"
    var lastSearchServiceSubcategory: String = "전체"

    // Apply filters
    fun filterPlaces(
        selectedCity: String,
        selectedDistrict: String,
        selectedServiceCategory: String,
        selectedServiceSubcategory: String
    ) {
        // Save last search criteria
        lastSearchCity = selectedCity
        lastSearchDistrict = selectedDistrict
        lastSearchServiceCategory = selectedServiceCategory
        lastSearchServiceSubcategory = selectedServiceSubcategory

        _filteredPlaces.value = _allPlaces.value.filter { place ->
            // Location filtering
            val cityMatch = selectedCity == "전체" || place.address.contains(selectedCity)
            val districtMatch = selectedDistrict == "전체" || place.district == selectedDistrict

            // Service filtering - updated to use facilityKindDetail for category matching
            val serviceMatch = if (selectedServiceCategory == "전체") {
                true
            } else {
                // Primary category match based on facilityKindDetail
                val categoryMatch = place.facilityKindDetail == selectedServiceCategory

                // Subcategory matching
                val subcategoryMatch = if (selectedServiceSubcategory == "전체") {
                    true
                } else {
                    // Check if any service contains the subcategory
                    val service1Match = place.service1.any {
                        it.contains(selectedServiceSubcategory)
                    }

                    val service2Match = place.service2.any {
                        it.contains(selectedServiceSubcategory)
                    }

                    service1Match || service2Match
                }

                categoryMatch && subcategoryMatch
            }

            cityMatch && districtMatch && serviceMatch
        }
    }
}