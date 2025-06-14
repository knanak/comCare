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

class PlaceViewModel(private val supabaseHelper: SupabaseDatabaseHelper) : ViewModel() {
    // 미리 정의된 도시와 구/군 데이터
    private val predefinedCities = listOf("전체", "서울특별시", "경기도", "인천광역시", "부산광역시", "경상북도")

    private val predefinedDistricts = mapOf(
        "전체" to listOf("전체"),
        "서울특별시" to listOf(
            "전체", "강남구", "강동구", "강북구", "강서구", "관악구",
            "광진구", "구로구", "금천구", "노원구", "도봉구",
            "동대문구", "동작구", "마포구", "서대문구", "서초구",
            "성동구", "성북구", "송파구", "양천구", "영등포구",
            "용산구", "은평구", "종로구", "중구", "중랑구"
        ),
        "경기도" to listOf(
            "전체", "가평군", "고양시", "과천시", "광명시", "광주시",
            "구리시", "군포시", "김포시", "남양주시", "동두천시",
            "부천시", "성남시", "수원시", "시흥시", "안산시",
            "안성시", "안양시", "양주시", "양평군", "여주시",
            "연천군", "오산시", "용인시", "의왕시", "의정부시",
            "이천시", "파주시", "평택시", "포천시", "하남시", "화성시"
        ),
        "인천광역시" to listOf(
            "전체", "강화군", "계양구", "남동구", "동구", "미추홀구",
            "부평구", "서구", "연수구", "옹진군", "중구"
        ),
        "부산광역시" to listOf(
            "전체", "강서구", "금정구", "기장군", "남구", "동구",
            "동래구", "부산진구", "북구", "사상구", "사하구",
            "서구", "수영구", "연제구", "영도구", "중구", "해운대구"
        ),
        "경상북도" to listOf(
            "전체", "경산시", "경주시", "고령군", "구미시", "군위군",
            "김천시", "문경시", "봉화군", "상주시", "성주군",
            "안동시", "영덕군", "영양군", "영주시", "영천시",
            "예천군", "울릉군", "울진군", "의성군", "청도군",
            "청송군", "칠곡군", "포항시"
        )
    )

    // 사용자 위치 정보 추가
    private var userCity: String = ""
    private var userDistrict: String = ""

    // 1. facilities data
    // Using MutableState for the places list
    private val _allPlaces = mutableStateOf<List<Place>>(emptyList())
    private val _filteredPlaces = mutableStateOf<List<Place>>(emptyList())
    val filteredPlaces: State<List<Place>> = _filteredPlaces

    // Location hierarchy - 미리 정의된 데이터 사용
    private val _cities = mutableStateOf<List<String>>(predefinedCities)
    val cities: State<List<String>> = _cities

    private val _districts = mutableStateOf<Map<String, List<String>>>(predefinedDistricts)
    val districts: State<Map<String, List<String>>> = _districts

    // Service hierarchy
    private val _serviceCategories = mutableStateOf<List<String>>(emptyList())
    val serviceCategories: State<List<String>> = _serviceCategories

    private val _serviceSubcategories = mutableStateOf<Map<String, List<String>>>(emptyMap())
    val serviceSubcategories: State<Map<String, List<String>>> = _serviceSubcategories

    // job
    private val _jobs = mutableStateOf<List<SupabaseDatabaseHelper.Job>>(emptyList<SupabaseDatabaseHelper.Job>())
    val jobs: State<List<SupabaseDatabaseHelper.Job>> = _jobs

    private val _filteredJobs = mutableStateOf<List<SupabaseDatabaseHelper.Job>>(emptyList())
    val filteredJobs: State<List<SupabaseDatabaseHelper.Job>> = _filteredJobs

    private val _isLoading = mutableStateOf<Boolean>(false)
    val isLoading: Boolean
        get() = _isLoading.value

    // lecture
    private val _lectures = mutableStateOf<List<SupabaseDatabaseHelper.Lecture>>(emptyList())
    val lectures: State<List<SupabaseDatabaseHelper.Lecture>> = _lectures

    private val _filteredLectures = mutableStateOf<List<SupabaseDatabaseHelper.Lecture>>(emptyList())
    val filteredLectures: State<List<SupabaseDatabaseHelper.Lecture>> = _filteredLectures

    private val _isLoadingLectures = mutableStateOf<Boolean>(false)
    val isLoadingLectures: Boolean
        get() = _isLoadingLectures.value

    // kk_job
    private val _kkJobs = mutableStateOf<List<SupabaseDatabaseHelper.KKJob>>(emptyList())
    val kkJobs: State<List<SupabaseDatabaseHelper.KKJob>> = _kkJobs

    private val _filteredKKJobs = mutableStateOf<List<SupabaseDatabaseHelper.KKJob>>(emptyList())
    val filteredKKJobs: State<List<SupabaseDatabaseHelper.KKJob>> = _filteredKKJobs

    private val _isLoadingKKJobs = mutableStateOf<Boolean>(false)
    val isLoadingKKJobs: Boolean
        get() = _isLoadingKKJobs.value

    // 통합된 job 위치 정보 - 미리 정의된 데이터 사용
    private val _jobCities = mutableStateOf<List<String>>(predefinedCities)
    val jobCities: State<List<String>> = _jobCities

    private val _jobDistricts = mutableStateOf<Map<String, List<String>>>(predefinedDistricts)
    val jobDistricts: State<Map<String, List<String>>> = _jobDistricts

    // kk_culture 관련 추가
    private val _kkCultures = mutableStateOf<List<SupabaseDatabaseHelper.KKCulture>>(emptyList())
    val kkCultures: State<List<SupabaseDatabaseHelper.KKCulture>> = _kkCultures

    private val _filteredKKCultures = mutableStateOf<List<SupabaseDatabaseHelper.KKCulture>>(emptyList())
    val filteredKKCultures: State<List<SupabaseDatabaseHelper.KKCulture>> = _filteredKKCultures

    private val _isLoadingKKCultures = mutableStateOf<Boolean>(false)
    val isLoadingKKCultures: Boolean
        get() = _isLoadingKKCultures.value

    // 통합된 culture 위치 정보 - 미리 정의된 데이터 사용
    private val _cultureCities = mutableStateOf<List<String>>(predefinedCities)
    val cultureCities: State<List<String>> = _cultureCities

    private val _cultureDistricts = mutableStateOf<Map<String, List<String>>>(predefinedDistricts)
    val cultureDistricts: State<Map<String, List<String>>> = _cultureDistricts

    // kk_facility 관련 추가
    private val _kkFacilities = mutableStateOf<List<SupabaseDatabaseHelper.KKFacility>>(emptyList())
    val kkFacilities: State<List<SupabaseDatabaseHelper.KKFacility>> = _kkFacilities

    private val _isLoadingKKFacilities = mutableStateOf<Boolean>(false)
    val isLoadingKKFacilities: Boolean
        get() = _isLoadingKKFacilities.value

    // kk_facility2 관련 추가
    private val _kkFacility2s = mutableStateOf<List<SupabaseDatabaseHelper.KKFacility2>>(emptyList())
    val kkFacility2s: State<List<SupabaseDatabaseHelper.KKFacility2>> = _kkFacility2s

    private val _isLoadingKKFacility2s = mutableStateOf<Boolean>(false)
    val isLoadingKKFacility2s: Boolean
        get() = _isLoadingKKFacility2s.value

    // ich_facility 관련 추가
    private val _ichFacilities = mutableStateOf<List<SupabaseDatabaseHelper.ICHFacility>>(emptyList())
    val ichFacilities: State<List<SupabaseDatabaseHelper.ICHFacility>> = _ichFacilities

    private val _isLoadingICHFacilities = mutableStateOf<Boolean>(false)
    val isLoadingICHFacilities: Boolean
        get() = _isLoadingICHFacilities.value

    // ich_job 관련 추가
    private val _ichJobs = mutableStateOf<List<SupabaseDatabaseHelper.ICHJob>>(emptyList())
    val ichJobs: State<List<SupabaseDatabaseHelper.ICHJob>> = _ichJobs

    private val _filteredICHJobs = mutableStateOf<List<SupabaseDatabaseHelper.ICHJob>>(emptyList())
    val filteredICHJobs: State<List<SupabaseDatabaseHelper.ICHJob>> = _filteredICHJobs

    private val _isLoadingICHJobs = mutableStateOf<Boolean>(false)
    val isLoadingICHJobs: Boolean
        get() = _isLoadingICHJobs.value

    // ich_culture 관련 추가
    private val _ichCultures = mutableStateOf<List<SupabaseDatabaseHelper.ICHCulture>>(emptyList())
    val ichCultures: State<List<SupabaseDatabaseHelper.ICHCulture>> = _ichCultures

    private val _filteredICHCultures = mutableStateOf<List<SupabaseDatabaseHelper.ICHCulture>>(emptyList())
    val filteredICHCultures: State<List<SupabaseDatabaseHelper.ICHCulture>> = _filteredICHCultures

    private val _isLoadingICHCultures = mutableStateOf<Boolean>(false)
    val isLoadingICHCultures: Boolean
        get() = _isLoadingICHCultures.value

    // ich_facility2 관련 추가
    private val _ichFacility2s = mutableStateOf<List<SupabaseDatabaseHelper.ICHFacility2>>(emptyList())
    val ichFacility2s: State<List<SupabaseDatabaseHelper.ICHFacility2>> = _ichFacility2s

    private val _isLoadingICHFacility2s = mutableStateOf<Boolean>(false)
    val isLoadingICHFacility2s: Boolean
        get() = _isLoadingICHFacility2s.value


    private val _bsJobs = mutableStateOf<List<SupabaseDatabaseHelper.BSJob>>(emptyList())
    val bsJobs: State<List<SupabaseDatabaseHelper.BSJob>> = _bsJobs

    private val _filteredBSJobs = mutableStateOf<List<SupabaseDatabaseHelper.BSJob>>(emptyList())
    val filteredBSJobs: State<List<SupabaseDatabaseHelper.BSJob>> = _filteredBSJobs

    private val _isLoadingBSJobs = mutableStateOf<Boolean>(false)
    val isLoadingBSJobs: Boolean
        get() = _isLoadingBSJobs.value

    // 3. KB_Job 관련 상태 추가
    private val _kbJobs = mutableStateOf<List<SupabaseDatabaseHelper.KBJob>>(emptyList())
    val kbJobs: State<List<SupabaseDatabaseHelper.KBJob>> = _kbJobs

    private val _filteredKBJobs = mutableStateOf<List<SupabaseDatabaseHelper.KBJob>>(emptyList())
    val filteredKBJobs: State<List<SupabaseDatabaseHelper.KBJob>> = _filteredKBJobs

    private val _isLoadingKBJobs = mutableStateOf<Boolean>(false)
    val isLoadingKBJobs: Boolean
        get() = _isLoadingKBJobs.value

    // 1. BS_Culture 관련 상태 추가
    private val _bsCultures = mutableStateOf<List<SupabaseDatabaseHelper.BSCulture>>(emptyList())
    val bsCultures: State<List<SupabaseDatabaseHelper.BSCulture>> = _bsCultures

    private val _filteredBSCultures = mutableStateOf<List<SupabaseDatabaseHelper.BSCulture>>(emptyList())
    val filteredBSCultures: State<List<SupabaseDatabaseHelper.BSCulture>> = _filteredBSCultures

    private val _isLoadingBSCultures = mutableStateOf<Boolean>(false)
    val isLoadingBSCultures: Boolean
        get() = _isLoadingBSCultures.value

    // 2. KB_Culture 관련 상태 추가
    private val _kbCultures = mutableStateOf<List<SupabaseDatabaseHelper.KBCulture>>(emptyList())
    val kbCultures: State<List<SupabaseDatabaseHelper.KBCulture>> = _kbCultures

    private val _filteredKBCultures = mutableStateOf<List<SupabaseDatabaseHelper.KBCulture>>(emptyList())
    val filteredKBCultures: State<List<SupabaseDatabaseHelper.KBCulture>> = _filteredKBCultures

    private val _isLoadingKBCultures = mutableStateOf<Boolean>(false)
    val isLoadingKBCultures: Boolean
        get() = _isLoadingKBCultures.value

    // 1. BS_Facility 관련 상태 추가
    private val _bsFacilities = mutableStateOf<List<SupabaseDatabaseHelper.BSFacility>>(emptyList())
    val bsFacilities: State<List<SupabaseDatabaseHelper.BSFacility>> = _bsFacilities

    private val _isLoadingBSFacilities = mutableStateOf<Boolean>(false)
    val isLoadingBSFacilities: Boolean
        get() = _isLoadingBSFacilities.value

    // 2. KB_Facility 관련 상태 추가
    private val _kbFacilities = mutableStateOf<List<SupabaseDatabaseHelper.KBFacility>>(emptyList())
    val kbFacilities: State<List<SupabaseDatabaseHelper.KBFacility>> = _kbFacilities

    private val _isLoadingKBFacilities = mutableStateOf<Boolean>(false)
    val isLoadingKBFacilities: Boolean
        get() = _isLoadingKBFacilities.value

    private val _bsFacility2s = mutableStateOf<List<SupabaseDatabaseHelper.BSFacility2>>(emptyList())
    val bsFacility2s: State<List<SupabaseDatabaseHelper.BSFacility2>> = _bsFacility2s

    private val _isLoadingBSFacility2s = mutableStateOf<Boolean>(false)
    val isLoadingBSFacility2s: Boolean
        get() = _isLoadingBSFacility2s.value

    // 2. KB_Facility2 관련 상태 추가
    private val _kbFacility2s = mutableStateOf<List<SupabaseDatabaseHelper.KBFacility2>>(emptyList())
    val kbFacility2s: State<List<SupabaseDatabaseHelper.KBFacility2>> = _kbFacility2s

    private val _isLoadingKBFacility2s = mutableStateOf<Boolean>(false)
    val isLoadingKBFacility2s: Boolean
        get() = _isLoadingKBFacility2s.value

    init {
        // Fetch data when ViewModel is initialized
        fetchPlacesData()
//        fetchJobsData()
//        fetchLectureData()
//        fetchKKJobsData()
//        fetchKKCulturesData()
        fetchKKFacilitiesData()
        fetchKKFacility2sData()
        fetchICHFacilitiesData()
        fetchICHFacility2sData()
//        fetchICHJobsData()
//        fetchICHCulturesData()
//        fetchBSJobsData()
//        fetchKBJobsData()
//        fetchBSCulturesData()
//        fetchKBCulturesData()
        fetchBSFacilitiesData()
        fetchKBFacilitiesData()
        fetchBSFacility2sData()
        fetchKBFacility2sData()
    }

    // 사용자 위치 설정 함수 추가
    fun setUserLocation(city: String, district: String) {
        userCity = city
        userDistrict = district

        Log.d("PlaceViewModel", "========== 사용자 위치 설정 ==========")
        Log.d("PlaceViewModel", "도시: $userCity")
        Log.d("PlaceViewModel", "구/군: $userDistrict")
        Log.d("PlaceViewModel", "=====================================")

        // 위치가 설정되면 해당 지역으로 자동 필터링
        if (city.isNotEmpty()) {
            // ... 기존 코드 ...

            // 문화 필터링 (통합) - ICH 포함
            filterAllCultures(city, district)
            Log.d("PlaceViewModel", "문화 필터링 완료 - 일반: ${_filteredLectures.value.size}개, " +
                    "KK: ${_filteredKKCultures.value.size}개, ICH: ${_filteredICHCultures.value.size}개")
        }
    }

    // 사용자 위치 정보 getter 함수 추가
    fun getUserCity(): String = userCity
    fun getUserDistrict(): String = userDistrict


    // PlaceViewModel.kt에 추가할 내용

    // 고용 데이터 검색 및 필터링 함수
    fun searchAndFilterJobs(selectedCity: String, selectedDistrict: String) {
        viewModelScope.launch {
            try {
                // 로딩 상태 설정
                _isLoading.value = true
                _isLoadingKKJobs.value = true
                _isLoadingICHJobs.value = true
                _isLoadingBSJobs.value = true
                _isLoadingKBJobs.value = true

                // 선택된 도시에 따라 해당하는 데이터만 가져오기
                when (selectedCity) {
                    "서울특별시" -> {
                        val jobs = withContext(Dispatchers.IO) {
                            supabaseHelper.getFilteredJobs(selectedCity, selectedDistrict)
                        }
                        _jobs.value = jobs
                        _filteredJobs.value = jobs

                        // 다른 지역 데이터는 비우기
                        _filteredKKJobs.value = emptyList()
                        _filteredICHJobs.value = emptyList()
                        _filteredBSJobs.value = emptyList()
                        _filteredKBJobs.value = emptyList()
                    }

                    "경기도" -> {
                        val kkJobs = withContext(Dispatchers.IO) {
                            supabaseHelper.getFilteredKKJobs(selectedDistrict)
                        }
                        _kkJobs.value = kkJobs
                        _filteredKKJobs.value = kkJobs

                        // 다른 지역 데이터는 비우기
                        _filteredJobs.value = emptyList()
                        _filteredICHJobs.value = emptyList()
                        _filteredBSJobs.value = emptyList()
                        _filteredKBJobs.value = emptyList()
                    }

                    "인천광역시" -> {
                        val ichJobs = withContext(Dispatchers.IO) {
                            supabaseHelper.getFilteredICHJobs(selectedDistrict)
                        }
                        _ichJobs.value = ichJobs
                        _filteredICHJobs.value = ichJobs

                        // 다른 지역 데이터는 비우기
                        _filteredJobs.value = emptyList()
                        _filteredKKJobs.value = emptyList()
                        _filteredBSJobs.value = emptyList()
                        _filteredKBJobs.value = emptyList()
                    }

                    "부산광역시" -> {
                        val bsJobs = withContext(Dispatchers.IO) {
                            supabaseHelper.getFilteredBSJobs(selectedDistrict)
                        }
                        _bsJobs.value = bsJobs
                        _filteredBSJobs.value = bsJobs

                        // 다른 지역 데이터는 비우기
                        _filteredJobs.value = emptyList()
                        _filteredKKJobs.value = emptyList()
                        _filteredICHJobs.value = emptyList()
                        _filteredKBJobs.value = emptyList()
                    }

                    "경상북도" -> {
                        val kbJobs = withContext(Dispatchers.IO) {
                            supabaseHelper.getFilteredKBJobs(selectedDistrict)
                        }
                        _kbJobs.value = kbJobs
                        _filteredKBJobs.value = kbJobs

                        // 다른 지역 데이터는 비우기
                        _filteredJobs.value = emptyList()
                        _filteredKKJobs.value = emptyList()
                        _filteredICHJobs.value = emptyList()
                        _filteredBSJobs.value = emptyList()
                    }

                    "전체" -> {
                        // 전체 선택 시 모든 데이터 가져오기 (또는 비우기)
                        // 데이터가 너무 많다면 비우거나 일부만 가져오기
                        _filteredJobs.value = emptyList()
                        _filteredKKJobs.value = emptyList()
                        _filteredICHJobs.value = emptyList()
                        _filteredBSJobs.value = emptyList()
                        _filteredKBJobs.value = emptyList()
                    }
                }

            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error searching jobs: ${e.message}")
            } finally {
                _isLoading.value = false
                _isLoadingKKJobs.value = false
                _isLoadingICHJobs.value = false
                _isLoadingBSJobs.value = false
                _isLoadingKBJobs.value = false
            }
        }
    }

    fun searchAndFilterCultures(selectedCity: String, selectedDistrict: String) {
        viewModelScope.launch {
            try {
                // 로딩 상태 설정
                _isLoadingLectures.value = true
                _isLoadingKKCultures.value = true
                _isLoadingICHCultures.value = true
                _isLoadingBSCultures.value = true
                _isLoadingKBCultures.value = true

                // 선택된 도시에 따라 해당하는 데이터만 가져오기
                when (selectedCity) {
                    "서울특별시" -> {
                        val lectures = withContext(Dispatchers.IO) {
                            supabaseHelper.getFilteredLectures(selectedCity, selectedDistrict)
                        }
                        _lectures.value = lectures
                        _filteredLectures.value = lectures

                        // 다른 지역 데이터는 비우기
                        _filteredKKCultures.value = emptyList()
                        _filteredICHCultures.value = emptyList()
                        _filteredBSCultures.value = emptyList()
                        _filteredKBCultures.value = emptyList()
                    }

                    "경기도" -> {
                        val kkCultures = withContext(Dispatchers.IO) {
                            supabaseHelper.getFilteredKKCultures(selectedDistrict)
                        }
                        _kkCultures.value = kkCultures
                        _filteredKKCultures.value = kkCultures

                        // 다른 지역 데이터는 비우기
                        _filteredLectures.value = emptyList()
                        _filteredICHCultures.value = emptyList()
                        _filteredBSCultures.value = emptyList()
                        _filteredKBCultures.value = emptyList()
                    }

                    "인천광역시" -> {
                        val ichCultures = withContext(Dispatchers.IO) {
                            supabaseHelper.getFilteredICHCultures(selectedDistrict)
                        }
                        _ichCultures.value = ichCultures
                        _filteredICHCultures.value = ichCultures

                        // 다른 지역 데이터는 비우기
                        _filteredLectures.value = emptyList()
                        _filteredKKCultures.value = emptyList()
                        _filteredBSCultures.value = emptyList()
                        _filteredKBCultures.value = emptyList()
                    }

                    "부산광역시" -> {
                        val bsCultures = withContext(Dispatchers.IO) {
                            supabaseHelper.getFilteredBSCultures(selectedDistrict)
                        }
                        _bsCultures.value = bsCultures
                        _filteredBSCultures.value = bsCultures

                        // 다른 지역 데이터는 비우기
                        _filteredLectures.value = emptyList()
                        _filteredKKCultures.value = emptyList()
                        _filteredICHCultures.value = emptyList()
                        _filteredKBCultures.value = emptyList()
                    }

                    "경상북도" -> {
                        val kbCultures = withContext(Dispatchers.IO) {
                            supabaseHelper.getFilteredKBCultures(selectedDistrict)
                        }
                        _kbCultures.value = kbCultures
                        _filteredKBCultures.value = kbCultures

                        // 다른 지역 데이터는 비우기
                        _filteredLectures.value = emptyList()
                        _filteredKKCultures.value = emptyList()
                        _filteredICHCultures.value = emptyList()
                        _filteredBSCultures.value = emptyList()
                    }

                    "전체" -> {
                        // 전체 선택 시 모든 데이터 가져오기 (또는 비우기)
                        // 데이터가 너무 많다면 비우거나 일부만 가져오기
                        _filteredLectures.value = emptyList()
                        _filteredKKCultures.value = emptyList()
                        _filteredICHCultures.value = emptyList()
                        _filteredBSCultures.value = emptyList()
                        _filteredKBCultures.value = emptyList()
                    }
                }

            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error searching cultures: ${e.message}")
            } finally {
                _isLoadingLectures.value = false
                _isLoadingKKCultures.value = false
                _isLoadingICHCultures.value = false
                _isLoadingBSCultures.value = false
                _isLoadingKBCultures.value = false
            }
        }
    }

    private fun fetchPlacesData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting data fetch")

                // First try to get API data
                val apiData = withContext(Dispatchers.IO) {
                    fetchApiData()
                }

                // Then get Supabase data
                val supabaseData = withContext(Dispatchers.IO) {
                    fetchSupabaseData()
                }

                // Get KK Facility data
                val kkFacilityData = withContext(Dispatchers.IO) {
                    fetchKKFacilityDataAsPlaces()
                }

                // Get KK Facility2 data
                val kkFacility2Data = withContext(Dispatchers.IO) {
                    fetchKKFacility2DataAsPlaces()
                }

                // Get ICH Facility data
                val ichFacilityData = withContext(Dispatchers.IO) {
                    fetchICHFacilityDataAsPlaces()
                }

                // Get ICH Facility2 data
                val ichFacility2Data = withContext(Dispatchers.IO) {
                    fetchICHFacility2DataAsPlaces()
                }

                // Get BS Facility data
                val bsFacilityData = withContext(Dispatchers.IO) {
                    fetchBSFacilityDataAsPlaces()
                }

                // Get KB Facility data
                val kbFacilityData = withContext(Dispatchers.IO) {
                    fetchKBFacilityDataAsPlaces()
                }

                // Get BS Facility2 data
                val bsFacility2Data = withContext(Dispatchers.IO) {
                    fetchBSFacility2DataAsPlaces()
                }

                // Get KB Facility2 data
                val kbFacility2Data = withContext(Dispatchers.IO) {
                    fetchKBFacility2DataAsPlaces()
                }

                // Combine all datasets (bs_facility2, kb_facility2 포함)
                val combinedData = apiData + supabaseData + kkFacilityData + kkFacility2Data +
                        ichFacilityData + ichFacility2Data + bsFacilityData + kbFacilityData +
                        bsFacility2Data + kbFacility2Data

                // Process the combined data
                processServiceCategories(combinedData)

                _allPlaces.value = combinedData
                _filteredPlaces.value = combinedData

                Log.d("PlaceViewModel", "Data fetch complete: ${combinedData.size} total items " +
                        "(${apiData.size} API, ${supabaseData.size} Supabase, " +
                        "${kkFacilityData.size} KK Facility, ${kkFacility2Data.size} KK Facility2, " +
                        "${ichFacilityData.size} ICH Facility, ${ichFacility2Data.size} ICH Facility2, " +
                        "${bsFacilityData.size} BS Facility, ${kbFacilityData.size} KB Facility, " +
                        "${bsFacility2Data.size} BS Facility2, ${kbFacility2Data.size} KB Facility2)")

            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching data", e)

                // Fallback to sample data if everything fails
                Log.d("PlaceViewModel", "Falling back to sample data")
                val sampleData = getSampleData()
                _allPlaces.value = sampleData
                _filteredPlaces.value = sampleData

                // Process sample data
                processServiceCategories(sampleData)
            }
        }
    }


    fun fetchBSFacility2sData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting bs_facility2s data fetch")
                _isLoadingBSFacility2s.value = true

                val bsFacility2sData = withContext(Dispatchers.IO) {
                    try {
                        val bsFacility2s = supabaseHelper.getBSFacility2s()
                        Log.d("PlaceViewModel", "Supabase getBSFacility2s returned ${bsFacility2s.size} items")

                        if (bsFacility2s.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase bs_facility2s returned empty list")
                        } else {
                            // Log first bs_facility2 for debugging
                            val firstBSFacility2 = bsFacility2s.firstOrNull()
                            if (firstBSFacility2 != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample bs_facility2 data: Id=${firstBSFacility2.Id}, " +
                                            "title=${firstBSFacility2.Title}, " +
                                            "category=${firstBSFacility2.Category}, " +
                                            "address=${firstBSFacility2.Address}"
                                )
                            }
                        }
                        bsFacility2s
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getBSFacility2s Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the bs_facility2s value with the fetched data
                _bsFacility2s.value = bsFacility2sData

                // 기존 장소 데이터 다시 가져오기
                fetchPlacesData()

                Log.d("PlaceViewModel", "BS_Facility2s data fetch complete: ${bsFacility2sData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching bs_facility2s data: ${e.message}", e)
                _bsFacility2s.value = emptyList()
            } finally {
                _isLoadingBSFacility2s.value = false
            }
        }
    }

    // 5. KB_Facility2s 데이터 가져오기 함수
    fun fetchKBFacility2sData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting kb_facility2s data fetch")
                _isLoadingKBFacility2s.value = true

                val kbFacility2sData = withContext(Dispatchers.IO) {
                    try {
                        val kbFacility2s = supabaseHelper.getKBFacility2s()
                        Log.d("PlaceViewModel", "Supabase getKBFacility2s returned ${kbFacility2s.size} items")

                        if (kbFacility2s.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase kb_facility2s returned empty list")
                        } else {
                            // Log first kb_facility2 for debugging
                            val firstKBFacility2 = kbFacility2s.firstOrNull()
                            if (firstKBFacility2 != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample kb_facility2 data: Id=${firstKBFacility2.Id}, " +
                                            "title=${firstKBFacility2.Title}, " +
                                            "category=${firstKBFacility2.Category}, " +
                                            "address=${firstKBFacility2.Address}"
                                )
                            }
                        }
                        kbFacility2s
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getKBFacility2s Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the kb_facility2s value with the fetched data
                _kbFacility2s.value = kbFacility2sData

                // 기존 장소 데이터 다시 가져오기
                fetchPlacesData()

                Log.d("PlaceViewModel", "KB_Facility2s data fetch complete: ${kbFacility2sData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching kb_facility2s data: ${e.message}", e)
                _kbFacility2s.value = emptyList()
            } finally {
                _isLoadingKBFacility2s.value = false
            }
        }
    }

    // 6. BS Facility2 데이터를 Place 객체로 변환
    private suspend fun fetchBSFacility2DataAsPlaces(): List<Place> {
        return try {
            Log.d("PlaceViewModel", "Starting BS Facility2 data fetch as Places")

            val bsFacility2s = supabaseHelper.getBSFacility2s()
            Log.d("PlaceViewModel", "BS Facility2 getBSFacility2s returned ${bsFacility2s.size} items")

            if (bsFacility2s.isEmpty()) {
                Log.d("PlaceViewModel", "BS Facility2 returned empty list")
                return emptyList()
            }

            // BS Facility2 데이터를 Place 객체로 매핑
            val places = bsFacility2s.map { facility ->
                try {
                    // 주소에서 도시와 구/군 정보 추출
                    val addressParts = facility.Address?.trim()?.split(" ") ?: emptyList()
                    val city = if (addressParts.isNotEmpty()) addressParts[0] else ""
                    val district = if (addressParts.size > 1) addressParts[1] else ""

                    Place(
                        id = "bs2_${facility.Id}",
                        name = facility.Title ?: "이름 없음",
                        facilityCode = "",
                        facilityKind = facility.Service2 ?: "",
                        facilityKindDetail = facility.Service1 ?: "복지시설",
                        district = district,
                        address = facility.Address ?: "",
                        tel = facility.Tel ?: "",
                        zipCode = "",
                        service1 = listOf(facility.Service1 ?: "복지시설"),
                        service2 = listOf(facility.Service2 ?: ""),
                        rating = "",
                        rating_year = "",
                        full = facility.Quota?.toString() ?: "0",
                        now = facility.Users?.toString() ?: "0",
                        wating = "0",
                        bus = ""
                    )
                } catch (e: Exception) {
                    Log.e("PlaceViewModel", "Error converting bs_facility2: ${facility.Id}", e)
                    null
                }
            }.filterNotNull()

            Log.d("PlaceViewModel", "BS Facility2 data fetch complete: ${places.size} items")
            places

        } catch (e: Exception) {
            Log.e("PlaceViewModel", "Error fetching BS Facility2 data", e)
            emptyList()
        }
    }

    // 7. KB Facility2 데이터를 Place 객체로 변환
    private suspend fun fetchKBFacility2DataAsPlaces(): List<Place> {
        return try {
            Log.d("PlaceViewModel", "Starting KB Facility2 data fetch as Places")

            val kbFacility2s = supabaseHelper.getKBFacility2s()
            Log.d("PlaceViewModel", "KB Facility2 getKBFacility2s returned ${kbFacility2s.size} items")

            if (kbFacility2s.isEmpty()) {
                Log.d("PlaceViewModel", "KB Facility2 returned empty list")
                return emptyList()
            }

            // KB Facility2 데이터를 Place 객체로 매핑
            val places = kbFacility2s.map { facility ->
                try {
                    // 주소에서 도시와 구/군 정보 추출
                    val addressParts = facility.Address?.trim()?.split(" ") ?: emptyList()
                    val city = if (addressParts.isNotEmpty()) addressParts[0] else ""
                    val district = if (addressParts.size > 1) addressParts[1] else ""

                    Place(
                        id = "kb2_${facility.Id}",
                        name = facility.Title ?: "이름 없음",
                        facilityCode = "",
                        facilityKind = facility.Service2 ?: "",
                        facilityKindDetail = facility.Service1 ?: "복지시설",
                        district = district,
                        address = facility.Address ?: "",
                        tel = facility.Tel ?: "",
                        zipCode = "",
                        service1 = listOf(facility.Service1 ?: "복지시설"),
                        service2 = listOf(facility.Service2 ?: ""),
                        rating = "",
                        rating_year = "",
                        full = facility.Quota?.toString() ?: "0",
                        now = facility.Users?.toString() ?: "0",
                        wating = "0",
                        bus = ""
                    )
                } catch (e: Exception) {
                    Log.e("PlaceViewModel", "Error converting kb_facility2: ${facility.Id}", e)
                    null
                }
            }.filterNotNull()

            Log.d("PlaceViewModel", "KB Facility2 data fetch complete: ${places.size} items")
            places

        } catch (e: Exception) {
            Log.e("PlaceViewModel", "Error fetching KB Facility2 data", e)
            emptyList()
        }
    }

    fun fetchBSFacilitiesData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting bs_facilities data fetch")
                _isLoadingBSFacilities.value = true

                val bsFacilitiesData = withContext(Dispatchers.IO) {
                    try {
                        val bsFacilities = supabaseHelper.getBSFacilities()
                        Log.d("PlaceViewModel", "Supabase getBSFacilities returned ${bsFacilities.size} items")

                        if (bsFacilities.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase bs_facilities returned empty list")
                        } else {
                            // Log first bs_facility for debugging
                            val firstBSFacility = bsFacilities.firstOrNull()
                            if (firstBSFacility != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample bs_facility data: Id=${firstBSFacility.Id}, " +
                                            "title=${firstBSFacility.Title}, " +
                                            "category=${firstBSFacility.Category}, " +
                                            "address=${firstBSFacility.Address}"
                                )
                            }
                        }
                        bsFacilities
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getBSFacilities Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the bs_facilities value with the fetched data
                _bsFacilities.value = bsFacilitiesData

                // 기존 장소 데이터 다시 가져오기
                fetchPlacesData()

                Log.d("PlaceViewModel", "BS_Facilities data fetch complete: ${bsFacilitiesData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching bs_facilities data: ${e.message}", e)
                _bsFacilities.value = emptyList()
            } finally {
                _isLoadingBSFacilities.value = false
            }
        }
    }

    // 5. KB_Facilities 데이터 가져오기 함수
    fun fetchKBFacilitiesData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting kb_facilities data fetch")
                _isLoadingKBFacilities.value = true

                val kbFacilitiesData = withContext(Dispatchers.IO) {
                    try {
                        val kbFacilities = supabaseHelper.getKBFacilities()
                        Log.d("PlaceViewModel", "Supabase getKBFacilities returned ${kbFacilities.size} items")

                        if (kbFacilities.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase kb_facilities returned empty list")
                        } else {
                            // Log first kb_facility for debugging
                            val firstKBFacility = kbFacilities.firstOrNull()
                            if (firstKBFacility != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample kb_facility data: Id=${firstKBFacility.Id}, " +
                                            "title=${firstKBFacility.Title}, " +
                                            "category=${firstKBFacility.Category}, " +
                                            "address=${firstKBFacility.Address}"
                                )
                            }
                        }
                        kbFacilities
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getKBFacilities Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the kb_facilities value with the fetched data
                _kbFacilities.value = kbFacilitiesData

                // 기존 장소 데이터 다시 가져오기
                fetchPlacesData()

                Log.d("PlaceViewModel", "KB_Facilities data fetch complete: ${kbFacilitiesData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching kb_facilities data: ${e.message}", e)
                _kbFacilities.value = emptyList()
            } finally {
                _isLoadingKBFacilities.value = false
            }
        }
    }

    // 6. BS Facility 데이터를 Place 객체로 변환
    private suspend fun fetchBSFacilityDataAsPlaces(): List<Place> {
        return try {
            Log.d("PlaceViewModel", "Starting BS Facility data fetch as Places")

            val bsFacilities = supabaseHelper.getBSFacilities()
            Log.d("PlaceViewModel", "BS Facility getBSFacilities returned ${bsFacilities.size} items")

            if (bsFacilities.isEmpty()) {
                Log.d("PlaceViewModel", "BS Facility returned empty list")
                return emptyList()
            }

            // BS Facility 데이터를 Place 객체로 매핑
            val places = bsFacilities.map { facility ->
                try {
                    // 주소에서 도시와 구/군 정보 추출
                    val addressParts = facility.Address?.trim()?.split(" ") ?: emptyList()
                    val city = if (addressParts.isNotEmpty()) addressParts[0] else ""
                    val district = if (addressParts.size > 1) addressParts[1] else ""

                    // Rating에서 개행문자 제거
                    val cleanRating = facility.Rating?.replace("\n", " ") ?: ""

                    Place(
                        id = "bs_${facility.Id}",
                        name = facility.Title ?: "이름 없음",
                        facilityCode = "",
                        facilityKind = facility.Service2 ?: "",
                        facilityKindDetail = facility.Service1 ?: "복지시설",
                        district = district,
                        address = facility.Address ?: "",
                        tel = facility.Tel ?: "",
                        zipCode = "",
                        service1 = listOf(facility.Service1 ?: "복지시설"),
                        service2 = listOf(facility.Service2 ?: ""),
                        rating = cleanRating,
                        rating_year = "",
                        full = facility.Full ?: "0",
                        now = facility.Now ?: "0",
                        wating = facility.Wating ?: "0",
                        bus = facility.Bus ?: ""
                    )
                } catch (e: Exception) {
                    Log.e("PlaceViewModel", "Error converting bs_facility: ${facility.Id}", e)
                    null
                }
            }.filterNotNull()

            Log.d("PlaceViewModel", "BS Facility data fetch complete: ${places.size} items")
            places

        } catch (e: Exception) {
            Log.e("PlaceViewModel", "Error fetching BS Facility data", e)
            emptyList()
        }
    }

    // 7. KB Facility 데이터를 Place 객체로 변환
    private suspend fun fetchKBFacilityDataAsPlaces(): List<Place> {
        return try {
            Log.d("PlaceViewModel", "Starting KB Facility data fetch as Places")

            val kbFacilities = supabaseHelper.getKBFacilities()
            Log.d("PlaceViewModel", "KB Facility getKBFacilities returned ${kbFacilities.size} items")

            if (kbFacilities.isEmpty()) {
                Log.d("PlaceViewModel", "KB Facility returned empty list")
                return emptyList()
            }

            // KB Facility 데이터를 Place 객체로 매핑
            val places = kbFacilities.map { facility ->
                try {
                    // 주소에서 도시와 구/군 정보 추출
                    val addressParts = facility.Address?.trim()?.split(" ") ?: emptyList()
                    val city = if (addressParts.isNotEmpty()) addressParts[0] else ""
                    val district = if (addressParts.size > 1) addressParts[1] else ""

                    // Rating에서 개행문자 제거
                    val cleanRating = facility.Rating?.replace("\n", " ") ?: ""

                    Place(
                        id = "kb_${facility.Id}",
                        name = facility.Title ?: "이름 없음",
                        facilityCode = "",
                        facilityKind = facility.Service2 ?: "",
                        facilityKindDetail = facility.Service1 ?: "복지시설",
                        district = district,
                        address = facility.Address ?: "",
                        tel = facility.Tel ?: "",
                        zipCode = "",
                        service1 = listOf(facility.Service1 ?: "복지시설"),
                        service2 = listOf(facility.Service2 ?: ""),
                        rating = cleanRating,
                        rating_year = "",
                        full = facility.Full ?: "0",
                        now = facility.Now ?: "0",
                        wating = facility.Wating ?: "0",
                        bus = facility.Bus ?: ""
                    )
                } catch (e: Exception) {
                    Log.e("PlaceViewModel", "Error converting kb_facility: ${facility.Id}", e)
                    null
                }
            }.filterNotNull()

            Log.d("PlaceViewModel", "KB Facility data fetch complete: ${places.size} items")
            places

        } catch (e: Exception) {
            Log.e("PlaceViewModel", "Error fetching KB Facility data", e)
            emptyList()
        }
    }

    private suspend fun fetchICHFacility2DataAsPlaces(): List<Place> {
        return try {
            Log.d("PlaceViewModel", "Starting ICH Facility2 data fetch as Places")

            val ichFacility2s = supabaseHelper.getICHFacility2s()
            Log.d("PlaceViewModel", "ICH Facility2 getICHFacility2s returned ${ichFacility2s.size} items")

            if (ichFacility2s.isEmpty()) {
                Log.d("PlaceViewModel", "ICH Facility2 returned empty list")
                return emptyList()
            }

            // ICH Facility2 데이터를 Place 객체로 매핑
            val places = ichFacility2s.map { facility ->
                try {
                    // 주소에서 도시와 구/군 정보 추출
                    val addressParts = facility.Address?.trim()?.split(" ") ?: emptyList()
                    val city = if (addressParts.isNotEmpty()) addressParts[0] else ""
                    val district = if (addressParts.size > 1) addressParts[1] else ""

                    Place(
                        id = "ich2_${facility.Id}",
                        name = facility.Title ?: "이름 없음",
                        facilityCode = "",
                        facilityKind = facility.Service2 ?: "",
                        facilityKindDetail = facility.Service1 ?: "복지시설",
                        district = district,
                        address = facility.Address ?: "",
                        tel = facility.Tel ?: "",
                        zipCode = "",
                        service1 = listOf(facility.Service1 ?: "복지시설"),
                        service2 = listOf(facility.Service2 ?: ""),
                        rating = "",
                        rating_year = "",
                        full = "0",
                        now = "0",
                        wating = "0",
                        bus = ""
                    )
                } catch (e: Exception) {
                    Log.e("PlaceViewModel", "Error converting ich_facility2: ${facility.Id}", e)
                    null
                }
            }.filterNotNull()

            Log.d("PlaceViewModel", "ICH Facility2 data fetch complete: ${places.size} items")
            places

        } catch (e: Exception) {
            Log.e("PlaceViewModel", "Error fetching ICH Facility2 data", e)
            emptyList()
        }
    }

    fun fetchICHFacility2sData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting ich_facility2s data fetch")
                _isLoadingICHFacility2s.value = true

                val ichFacility2sData = withContext(Dispatchers.IO) {
                    try {
                        val ichFacility2s = supabaseHelper.getICHFacility2s()
                        Log.d("PlaceViewModel", "Supabase getICHFacility2s returned ${ichFacility2s.size} items")

                        if (ichFacility2s.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase ich_facility2s returned empty list")
                        } else {
                            // Log first ich_facility2 for debugging
                            val firstICHFacility2 = ichFacility2s.firstOrNull()
                            if (firstICHFacility2 != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample ich_facility2 data: Id=${firstICHFacility2.Id}, " +
                                            "title=${firstICHFacility2.Title}, " +
                                            "category=${firstICHFacility2.Category}, " +
                                            "address=${firstICHFacility2.Address}"
                                )
                            }
                        }
                        ichFacility2s
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getICHFacility2s Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the ich_facility2s value with the fetched data
                _ichFacility2s.value = ichFacility2sData

                // 기존 장소 데이터 다시 가져오기
                fetchPlacesData()

                Log.d("PlaceViewModel", "ICH_Facility2s data fetch complete: ${ichFacility2sData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching ich_facility2s data: ${e.message}", e)
                _ichFacility2s.value = emptyList()
            } finally {
                _isLoadingICHFacility2s.value = false
            }
        }
    }

    // ICH Facility 데이터를 Place 객체로 변환
    private suspend fun fetchICHFacilityDataAsPlaces(): List<Place> {
        return try {
            Log.d("PlaceViewModel", "Starting ICH Facility data fetch as Places")

            val ichFacilities = supabaseHelper.getICHFacilities()
            Log.d("PlaceViewModel", "ICH Facility getICHFacilities returned ${ichFacilities.size} items")

            if (ichFacilities.isEmpty()) {
                Log.d("PlaceViewModel", "ICH Facility returned empty list")
                return emptyList()
            }

            // ICH Facility 데이터를 Place 객체로 매핑
            val places = ichFacilities.map { facility ->
                try {
                    // 주소에서 도시와 구/군 정보 추출
                    val addressParts = facility.Address?.trim()?.split(" ") ?: emptyList()
                    val city = if (addressParts.isNotEmpty()) addressParts[0] else ""
                    val district = if (addressParts.size > 1) addressParts[1] else ""

                    // Rating에서 개행문자 제거
                    val cleanRating = facility.Rating?.replace("\n", " ") ?: ""

                    Place(
                        id = "ich_${facility.Id}",
                        name = facility.Title ?: "이름 없음",
                        facilityCode = "",
                        facilityKind = facility.Service2 ?: "",
                        facilityKindDetail = facility.Service1 ?: "복지시설",
                        district = district,
                        address = facility.Address ?: "",
                        tel = facility.Tel ?: "",
                        zipCode = "",
                        service1 = listOf(facility.Service1 ?: "복지시설"),
                        service2 = listOf(facility.Service2 ?: ""),
                        rating = cleanRating,
                        rating_year = "",
                        full = facility.Full ?: "0",
                        now = facility.Now ?: "0",
                        wating = facility.Wating ?: "0",
                        bus = facility.Bus ?: ""
                    )
                } catch (e: Exception) {
                    Log.e("PlaceViewModel", "Error converting ich_facility: ${facility.Id}", e)
                    null
                }
            }.filterNotNull()

            Log.d("PlaceViewModel", "ICH Facility data fetch complete: ${places.size} items")
            places

        } catch (e: Exception) {
            Log.e("PlaceViewModel", "Error fetching ICH Facility data", e)
            emptyList()
        }
    }

    fun fetchICHFacilitiesData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting ich_facilities data fetch")
                _isLoadingICHFacilities.value = true

                val ichFacilitiesData = withContext(Dispatchers.IO) {
                    try {
                        val ichFacilities = supabaseHelper.getICHFacilities()
                        Log.d("PlaceViewModel", "Supabase getICHFacilities returned ${ichFacilities.size} items")

                        if (ichFacilities.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase ich_facilities returned empty list")
                        } else {
                            // Log first ich_facility for debugging
                            val firstICHFacility = ichFacilities.firstOrNull()
                            if (firstICHFacility != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample ich_facility data: Id=${firstICHFacility.Id}, " +
                                            "title=${firstICHFacility.Title}, " +
                                            "category=${firstICHFacility.Category}, " +
                                            "address=${firstICHFacility.Address}"
                                )
                            }
                        }
                        ichFacilities
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getICHFacilities Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the ich_facilities value with the fetched data
                _ichFacilities.value = ichFacilitiesData

                // 기존 장소 데이터 다시 가져오기
                fetchPlacesData()

                Log.d("PlaceViewModel", "ICH_Facilities data fetch complete: ${ichFacilitiesData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching ich_facilities data: ${e.message}", e)
                _ichFacilities.value = emptyList()
            } finally {
                _isLoadingICHFacilities.value = false
            }
        }
    }

    // KK Facility 데이터를 Place 객체로 변환
    private suspend fun fetchKKFacilityDataAsPlaces(): List<Place> {
        return try {
            Log.d("PlaceViewModel", "Starting KK Facility data fetch as Places")

            val kkFacilities = supabaseHelper.getKKFacilities()
            Log.d("PlaceViewModel", "KK Facility getKKFacilities returned ${kkFacilities.size} items")

            if (kkFacilities.isEmpty()) {
                Log.d("PlaceViewModel", "KK Facility returned empty list")
                return emptyList()
            }

            // KK Facility 데이터를 Place 객체로 매핑
            val places = kkFacilities.map { facility ->
                try {
                    // 주소에서 도시와 구/군 정보 추출
                    val addressParts = facility.Address?.trim()?.split(" ") ?: emptyList()
                    val city = if (addressParts.isNotEmpty()) addressParts[0] else ""
                    val district = if (addressParts.size > 1) addressParts[1] else ""

                    // Rating에서 개행문자 제거
                    val cleanRating = facility.Rating?.replace("\n", " ") ?: ""

                    Place(
                        id = "kk_${facility.Id}",
                        name = facility.Title ?: "이름 없음",
                        facilityCode = "",
                        facilityKind = facility.Service2 ?: "",
                        facilityKindDetail = facility.Service1 ?: "복지시설",
                        district = district,
                        address = facility.Address ?: "",
                        tel = facility.Tel ?: "",
                        zipCode = "",
                        service1 = listOf(facility.Service1 ?: "복지시설"),
                        service2 = listOf(facility.Service2 ?: ""),
                        rating = cleanRating,
                        rating_year = "",
                        full = facility.Full ?: "0",
                        now = facility.Now ?: "0",
                        wating = facility.Wating ?: "0",
                        bus = facility.Bus ?: ""
                    )
                } catch (e: Exception) {
                    Log.e("PlaceViewModel", "Error converting kk_facility: ${facility.Id}", e)
                    null
                }
            }.filterNotNull()

            Log.d("PlaceViewModel", "KK Facility data fetch complete: ${places.size} items")
            places

        } catch (e: Exception) {
            Log.e("PlaceViewModel", "Error fetching KK Facility data", e)
            emptyList()
        }
    }

    fun fetchKKFacilitiesData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting kk_facilities data fetch")
                _isLoadingKKFacilities.value = true

                val kkFacilitiesData = withContext(Dispatchers.IO) {
                    try {
                        val kkFacilities = supabaseHelper.getKKFacilities()
                        Log.d("PlaceViewModel", "Supabase getKKFacilities returned ${kkFacilities.size} items")

                        if (kkFacilities.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase kk_facilities returned empty list")
                        } else {
                            // Log first kk_facility for debugging
                            val firstKKFacility = kkFacilities.firstOrNull()
                            if (firstKKFacility != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample kk_facility data: Id=${firstKKFacility.Id}, " +
                                            "title=${firstKKFacility.Title}, " +
                                            "category=${firstKKFacility.Category}, " +
                                            "address=${firstKKFacility.Address}"
                                )
                            }
                        }
                        kkFacilities
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getKKFacilities Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the kk_facilities value with the fetched data
                _kkFacilities.value = kkFacilitiesData

                // 기존 장소 데이터 다시 가져오기
                fetchPlacesData()

                Log.d("PlaceViewModel", "KK_Facilities data fetch complete: ${kkFacilitiesData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching kk_facilities data: ${e.message}", e)
                _kkFacilities.value = emptyList()
            } finally {
                _isLoadingKKFacilities.value = false
            }
        }
    }

    // KK Facility2 데이터를 Place 객체로 변환
    private suspend fun fetchKKFacility2DataAsPlaces(): List<Place> {
        return try {
            Log.d("PlaceViewModel", "Starting KK Facility2 data fetch as Places")

            val kkFacility2s = supabaseHelper.getKKFacility2s()
            Log.d("PlaceViewModel", "KK Facility2 getKKFacility2s returned ${kkFacility2s.size} items")

            if (kkFacility2s.isEmpty()) {
                Log.d("PlaceViewModel", "KK Facility2 returned empty list")
                return emptyList()
            }

            // KK Facility2 데이터를 Place 객체로 매핑
            val places = kkFacility2s.map { facility ->
                try {
                    // 주소에서 도시와 구/군 정보 추출
                    val addressParts = facility.Address?.trim()?.split(" ") ?: emptyList()
                    val city = if (addressParts.isNotEmpty()) addressParts[0] else ""
                    val district = if (addressParts.size > 1) addressParts[1] else ""

                    Place(
                        id = "kk2_${facility.Id}",
                        name = facility.Title ?: "이름 없음",
                        facilityCode = "",
                        facilityKind = facility.Service2 ?: "",
                        facilityKindDetail = facility.Service1 ?: "복지시설",
                        district = district,
                        address = facility.Address ?: "",
                        tel = facility.Tel ?: "",
                        zipCode = "",
                        service1 = listOf(facility.Service1 ?: "복지시설"),
                        service2 = listOf(facility.Service2 ?: ""),
                        rating = "",
                        rating_year = "",
                        full = facility.Quota?.toString() ?: "0",
                        now = facility.Users?.toString() ?: "0",
                        wating = "0",
                        bus = ""
                    )
                } catch (e: Exception) {
                    Log.e("PlaceViewModel", "Error converting kk_facility2: ${facility.Id}", e)
                    null
                }
            }.filterNotNull()

            Log.d("PlaceViewModel", "KK Facility2 data fetch complete: ${places.size} items")
            places

        } catch (e: Exception) {
            Log.e("PlaceViewModel", "Error fetching KK Facility2 data", e)
            emptyList()
        }
    }

    fun fetchKKFacility2sData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting kk_facility2s data fetch")
                _isLoadingKKFacility2s.value = true

                val kkFacility2sData = withContext(Dispatchers.IO) {
                    try {
                        val kkFacility2s = supabaseHelper.getKKFacility2s()
                        Log.d("PlaceViewModel", "Supabase getKKFacility2s returned ${kkFacility2s.size} items")

                        if (kkFacility2s.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase kk_facility2s returned empty list")
                        } else {
                            // Log first kk_facility2 for debugging
                            val firstKKFacility2 = kkFacility2s.firstOrNull()
                            if (firstKKFacility2 != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample kk_facility2 data: Id=${firstKKFacility2.Id}, " +
                                            "title=${firstKKFacility2.Title}, " +
                                            "category=${firstKKFacility2.Category}, " +
                                            "address=${firstKKFacility2.Address}"
                                )
                            }
                        }
                        kkFacility2s
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getKKFacility2s Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the kk_facility2s value with the fetched data
                _kkFacility2s.value = kkFacility2sData

                // 기존 장소 데이터 다시 가져오기
                fetchPlacesData()

                Log.d("PlaceViewModel", "KK_Facility2s data fetch complete: ${kkFacility2sData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching kk_facility2s data: ${e.message}", e)
                _kkFacility2s.value = emptyList()
            } finally {
                _isLoadingKKFacility2s.value = false
            }
        }
    }

    // 주소에서 도시와 구/군 정보를 추출하는 함수들
    private fun extractCity(address: String): String {
        // 주소에서 첫 번째 단어를 도시로 추출 (예: "Seoul Yeongdeungpo-gu"에서 "Seoul")
        val parts = address.trim().split(" ")
        return if (parts.isNotEmpty()) parts[0] else ""
    }

    private fun extractDistrict(address: String): String {
        // 주소에서 두 번째 단어를 구/군으로 추출 (예: "Seoul Yeongdeungpo-gu"에서 "Yeongdeungpo-gu")
        val parts = address.trim().split(" ")
        return if (parts.size > 1) parts[1] else ""
    }

    private suspend fun fetchSupabaseData(): List<Place> {
        return try {
            Log.d("PlaceViewModel", "Starting Supabase data fetch")

            val supabaseFacilities = supabaseHelper.getFacilities()
            Log.d("PlaceViewModel", "Supabase getFacilities returned ${supabaseFacilities.size} items")

            if (supabaseFacilities.isEmpty()) {
                Log.d("PlaceViewModel", "Supabase returned empty list")
                return emptyList()
            }

            // 시설 데이터 매핑
            val places = supabaseFacilities.map { facility ->
                try {
                    // 도시와 구/군 정보 추출
                    val city = extractCity(facility.address)
                    val district = extractDistrict(facility.address)

                    // 서비스 정보 처리
                    val originalService2 = facility.service2
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
                        Pair(originalService2, facility.service2 ?: "")
                    }

                    // facility.rating에서 개행문자 제거
                    val cleanRating = facility.rating.replace("\n", " ")

                    Place(
                        id = facility.id.toString(),
                        name = facility.name,
                        facilityCode = "", // No direct equivalent in Supabase model
                        facilityKind = originalService2,
                        facilityKindDetail = "장기요양기관",
                        district = district, // 추출한 구/군 정보 저장
                        address = facility.address,
                        tel = facility.tel,
                        zipCode = "",
                        service1 = listOf("장기요양기관"),
                        service2 = listOf(newService1),
                        rating = cleanRating,
                        rating_year = facility.rating_year,
                        full = facility.full,
                        now = facility.now,
                        wating = facility.wating,
                        bus = facility.bus
                    )
                } catch (e: Exception) {
                    Log.e("PlaceViewModel", "Error converting facility: ${facility.id}", e)
                    null
                }
            }.filterNotNull()

            Log.d("PlaceViewModel", "Supabase data fetch complete: ${places.size} items")
            places

        } catch (e: Exception) {
            Log.e("PlaceViewModel", "Error fetching Supabase data", e)
            emptyList()
        }
    }

    private fun processServiceCategories(places: List<Place>) {
        // Extract all distinct facility detail types from the data
        val allCategories = places.map { it.facilityKindDetail }.distinct().filter { it.isNotEmpty() }

        // Add "전체" (All) at the beginning
        val categoriesList = mutableListOf("전체")
        categoriesList.addAll(allCategories)
        _serviceCategories.value = categoriesList

        // Create subcategories map
        val subcategoriesMap = mutableMapOf<String, List<String>>()

        // Add "전체" entry for all categories
        subcategoriesMap["전체"] = listOf("전체")

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
                        .sorted()

                    // Add "전체" at the beginning
                    val fullSubcategories = mutableListOf("전체")
                    fullSubcategories.addAll(subCategories)

                    subcategoriesMap[category] = fullSubcategories
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
        // Keep your existing fetchApiData method unchanged
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
        // Keep your existing parseApiResponse method unchanged
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
                facilityKind = service2Values.firstOrNull() ?: "",
                facilityKindDetail = fcltKindDetailNm,
                district = getElementValue(rowNode, "JRSD_SGG_NM"),
                address = address,
                tel = getElementValue(rowNode, "FCLT_TEL_NO").ifEmpty { " 없음" },
                zipCode = getElementValue(rowNode, "FCLT_ZIPCD"),
                // Derived values
                service2 = service2Values,
                service1 = service1Values,
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
                rating = "A",
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
                rating = "A",
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

            // District filtering - district 필드와 주소 모두 확인
            val districtMatch = if (selectedDistrict == "전체") {
                true
            } else {
                // district 필드 확인
                val fieldMatch = place.district == selectedDistrict

                // 주소에서 district 확인
                val addressParts = place.address.split(" ")
                val addressMatch = if (addressParts.size >= 2) {
                    addressParts[1] == selectedDistrict
                } else {
                    false
                }

                // 둘 중 하나라도 일치하면 true
                fieldMatch || addressMatch
            }

            // 디버깅을 위한 로그 (ich_facility만)
            if (place.id.startsWith("ich_") && selectedCity == "인천광역시") {
                val addressParts = place.address.split(" ")
                val addressDistrict = if (addressParts.size >= 2) addressParts[1] else "N/A"
                Log.d("FilterDebug", "ICH facility: ${place.name}, " +
                        "district field: '${place.district}', " +
                        "address district: '$addressDistrict', " +
                        "selected: '$selectedDistrict', " +
                        "match: $districtMatch")
            }

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

        // 결과 로그
        if (selectedCity == "인천광역시") {
            Log.d("FilterDebug", "Filtered results for 인천광역시 $selectedDistrict: ${_filteredPlaces.value.size} items")
        }
    }

    private fun fetchJobsData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting jobs data fetch")
                _isLoading.value = true

                val jobsData = withContext(Dispatchers.IO) {
                    try {
                        val jobs = supabaseHelper.getJobs()
                        Log.d("PlaceViewModel", "Supabase getJobs returned ${jobs.size} items")

                        if (jobs.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase jobs returned empty list")
                        } else {
                            // Log first job for debugging
                            val firstJob = jobs.firstOrNull()
                            if (firstJob != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample job data: id=${firstJob.Id}, " +
                                            "title=${firstJob.Title}, " +
                                            "category=${firstJob.JobCategory}"
                                )
                            }
                        }

                        jobs
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getJobs Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the jobs value with the fetched data
                _jobs.value = jobsData

                Log.d("PlaceViewModel", "Jobs data fetch complete: ${jobsData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching jobs data: ${e.message}", e)
                // Set to empty list if there's an error
                if (_jobs != null) {  // Null check before accessing
                    _jobs.value = emptyList()
                }
            } finally {
                if (_isLoading != null) {  // Null check before accessing
                    _isLoading.value = false
                }
            }
        }
    }

    fun filterJobs(selectedCity: String, selectedDistrict: String) {
        if (_jobs.value.isEmpty()) {
            _filteredJobs.value = emptyList()
            return
        }

        Log.d("PlaceViewModel", "Filtering jobs with city='$selectedCity', district='$selectedDistrict'")

        _filteredJobs.value = _jobs.value.filter { job ->
            // Extract location information from Location field
            val location = job.Address ?: ""

            // City filtering
            val cityMatch = selectedCity == "전체" ||
                    location.contains(selectedCity) ||
                    (selectedCity == "서울특별시" && (location.contains("서울")))

            // District filtering
            val districtMatch = selectedDistrict == "전체" || location.contains(selectedDistrict)

            // Both conditions must match
            cityMatch && districtMatch
        }

        Log.d("PlaceViewModel", "Filtered jobs: ${_filteredJobs.value.size} of ${_jobs.value.size} with city='$selectedCity', district='$selectedDistrict'")
    }

    fun fetchLectureData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting lectures data fetch")
                _isLoadingLectures.value = true

                val lectureData = withContext(Dispatchers.IO) {
                    try {
                        val lectures = supabaseHelper.getLectures()
                        Log.d("PlaceViewModel", "Supabase getLectures returned ${lectures.size} items")

                        if (lectures.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase lectures returned empty list")
                        } else {
                            // Log first lecture for debugging
                            val firstLecture = lectures.firstOrNull()
                            if (firstLecture != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample lecture data: id=${firstLecture.Id}, " +
                                            "title=${firstLecture.Title}, " +
                                            "institution=${firstLecture.Institution}"
                                )
                            }
                        }
                        lectures
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getLectures Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the lectures value with the fetched data
                _lectures.value = lectureData
                // Initialize filtered lectures with all lectures
                _filteredLectures.value = lectureData

                Log.d("PlaceViewModel", "Lectures data fetch complete: ${lectureData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching lectures data: ${e.message}", e)
                // Set to empty list if there's an error
                _lectures.value = emptyList()
                _filteredLectures.value = emptyList()
            } finally {
                _isLoadingLectures.value = false
            }
        }
    }

    fun filterLectures(selectedCity: String, selectedDistrict: String) {
        if (_lectures.value.isEmpty()) {
            _filteredLectures.value = emptyList()
            return
        }

        Log.d("PlaceViewModel", "Filtering lectures with city='$selectedCity', district='$selectedDistrict'")

        _filteredLectures.value = _lectures.value.filter { lecture ->
            // Extract location information from Institution field
            val institution = lecture.Institution ?: ""

            // Look for the region marker we added in the Institution field
            val regionMarker = "[REGION:"
            val regionStart = institution.indexOf(regionMarker)

            if (regionStart >= 0) {
                // Extract the region value between "[REGION:" and "]"
                val regionEnd = institution.indexOf("]", regionStart)
                if (regionEnd > regionStart) {
                    val fullRegion = institution.substring(regionStart + regionMarker.length, regionEnd)

                    // The fullRegion now contains "서울특별시 구로구" format
                    // Split by space to separate city and district
                    val parts = fullRegion.split(" ")
                    val city = if (parts.isNotEmpty()) parts[0] else ""
                    val district = if (parts.size > 1) parts.subList(1, parts.size).joinToString(" ") else ""

                    // City filtering - check against the city part
                    val cityMatch = selectedCity == "전체" ||
                            city.contains(selectedCity) ||
                            (selectedCity == "Seoul" && (city == "서울특별시" || city.contains("서울")))

                    // District filtering - check against the district part
                    val districtMatch = selectedDistrict == "전체" ||
                            district == selectedDistrict ||
                            district.contains(selectedDistrict) ||
                            selectedDistrict.contains(district)

                    // Both conditions must match
                    cityMatch && districtMatch
                } else {
                    // If region format is corrupted, don't filter out this item
                    selectedCity == "전체" && selectedDistrict == "전체"
                }
            } else {
                // If no region marker found, fall back to checking the entire institution field
                val cityMatch = selectedCity == "전체" ||
                        institution.contains(selectedCity) ||
                        (selectedCity == "Seoul" && institution.contains("서울"))

                val districtMatch = selectedDistrict == "전체" || institution.contains(selectedDistrict)

                cityMatch && districtMatch
            }
        }

        Log.d("PlaceViewModel", "Filtered lectures: ${_filteredLectures.value.size} of ${_lectures.value.size} with city='$selectedCity', district='$selectedDistrict'")
    }

    fun fetchKKJobsData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting kk_jobs data fetch")
                _isLoadingKKJobs.value = true

                val kkJobsData = withContext(Dispatchers.IO) {
                    try {
                        val kkJobs = supabaseHelper.getKKJobs()
                        Log.d("PlaceViewModel", "Supabase getKKJobs returned ${kkJobs.size} items")

                        if (kkJobs.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase kk_jobs returned empty list")
                        } else {
                            // Log first kk_job for debugging
                            val firstKKJob = kkJobs.firstOrNull()
                            if (firstKKJob != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample kk_job data: Id=${firstKKJob.Id}, " +
                                            "title=${firstKKJob.Title}, " +
                                            "category=${firstKKJob.Category}, " +
                                            "location=${firstKKJob.Address}"
                                )
                            }
                        }
                        kkJobs
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getKKJobs Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the kk_jobs value with the fetched data
                _kkJobs.value = kkJobsData
                _filteredKKJobs.value = kkJobsData

                Log.d("PlaceViewModel", "KK_Jobs data fetch complete: ${kkJobsData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching kk_jobs data: ${e.message}", e)
                _kkJobs.value = emptyList()
                _filteredKKJobs.value = emptyList()
            } finally {
                _isLoadingKKJobs.value = false
            }
        }
    }

    fun filterKKJobs(selectedCity: String, selectedDistrict: String) {
        if (_kkJobs.value.isEmpty()) {
            _filteredKKJobs.value = emptyList()
            return
        }

        Log.d("PlaceViewModel", "Filtering kk_jobs with city='$selectedCity', district='$selectedDistrict'")

        _filteredKKJobs.value = _kkJobs.value.filter { kkJob ->
            val location = kkJob.Address ?: ""
            val addressParts = location.trim().split(" ")

            // Extract city and district from the location
            val jobCity = if (addressParts.isNotEmpty()) addressParts[0] else ""
            val jobDistrict = if (addressParts.size > 1) addressParts[1] else ""

            // City filtering
            val cityMatch = selectedCity == "전체" || jobCity == selectedCity

            // District filtering
            val districtMatch = selectedDistrict == "전체" || jobDistrict == selectedDistrict

            // Both conditions must match
            cityMatch && districtMatch
        }

        Log.d("PlaceViewModel", "Filtered kk_jobs: ${_filteredKKJobs.value.size} of ${_kkJobs.value.size}")
    }



    // ICH Jobs 필터링 함수
    fun fetchICHJobsData() {
        Log.d("PlaceViewModel", "========== fetchICHJobsData START ==========")

        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "1. Entering viewModelScope.launch")
                _isLoadingICHJobs.value = true
                Log.d("PlaceViewModel", "2. Loading state set to true")

                val ichJobsData = withContext(Dispatchers.IO) {
                    try {
                        Log.d("PlaceViewModel", "3. Inside Dispatchers.IO context")
                        Log.d("PlaceViewModel", "4. About to call supabaseHelper.getICHJobs()")

                        // supabaseHelper가 null이 아닌지 확인
                        if (supabaseHelper == null) {
                            Log.e("PlaceViewModel", "ERROR: supabaseHelper is null!")
                            return@withContext emptyList<SupabaseDatabaseHelper.ICHJob>()
                        }

                        val startTime = System.currentTimeMillis()
                        val ichJobs = supabaseHelper.getICHJobs()
                        val endTime = System.currentTimeMillis()

                        Log.d("PlaceViewModel", "5. getICHJobs() completed in ${endTime - startTime}ms")
                        Log.d("PlaceViewModel", "6. Returned ${ichJobs.size} items")

                        if (ichJobs.isEmpty()) {
                            Log.w("PlaceViewModel", "WARNING: getICHJobs() returned empty list")
                        } else {
                            // 첫 번째 아이템 상세 로그
                            val firstICHJob = ichJobs.firstOrNull()
                            if (firstICHJob != null) {
                                Log.d("PlaceViewModel", "First ICH Job details:")
                                Log.d("PlaceViewModel", "  - Id: ${firstICHJob.Id}")
                                Log.d("PlaceViewModel", "  - Title: ${firstICHJob.Title}")
                                Log.d("PlaceViewModel", "  - Category: ${firstICHJob.Category}")
                                Log.d("PlaceViewModel", "  - Address: ${firstICHJob.Address}")
                                Log.d("PlaceViewModel", "  - WorkingHours: ${firstICHJob.WorkingHours}")
                                Log.d("PlaceViewModel", "  - Deadline: ${firstICHJob.Deadline}")
                            }

                            // 처음 3개 아이템의 제목 로그
                            ichJobs.take(3).forEachIndexed { index, job ->
                                Log.d("PlaceViewModel", "ICH Job[$index]: ${job.Title}")
                            }
                        }

                        ichJobs
                    } catch (e: Exception) {
                        Log.e("PlaceViewModel", "ERROR in Dispatchers.IO block: ${e.message}")
                        Log.e("PlaceViewModel", "Exception type: ${e.javaClass.simpleName}")
                        Log.e("PlaceViewModel", "Stack trace:", e)
                        e.printStackTrace()
                        emptyList()
                    }
                }

                Log.d("PlaceViewModel", "7. Back from Dispatchers.IO, got ${ichJobsData.size} items")

                // Update the ich_jobs value with the fetched data
                _ichJobs.value = ichJobsData
                _filteredICHJobs.value = ichJobsData

                Log.d("PlaceViewModel", "8. State updated:")
                Log.d("PlaceViewModel", "   - _ichJobs.value.size: ${_ichJobs.value.size}")
                Log.d("PlaceViewModel", "   - _filteredICHJobs.value.size: ${_filteredICHJobs.value.size}")

            } catch (e: Exception) {
                Log.e("PlaceViewModel", "FATAL ERROR in fetchICHJobsData: ${e.message}")
                Log.e("PlaceViewModel", "Exception type: ${e.javaClass.simpleName}")
                e.printStackTrace()
                _ichJobs.value = emptyList()
                _filteredICHJobs.value = emptyList()
            } finally {
                _isLoadingICHJobs.value = false
                Log.d("PlaceViewModel", "9. Loading state set to false")
                Log.d("PlaceViewModel", "========== fetchICHJobsData END ==========")
            }
        }
    }

    fun filterICHJobs(selectedCity: String, selectedDistrict: String) {
        if (_ichJobs.value.isEmpty()) {
            _filteredICHJobs.value = emptyList()
            return
        }

        Log.d("PlaceViewModel", "Filtering ich_jobs with city='$selectedCity', district='$selectedDistrict'")

        _filteredICHJobs.value = _ichJobs.value.filter { ichJob ->
            val location = ichJob.Address ?: ""
            val addressParts = location.trim().split(" ")

            // Extract city and district from the location
            val jobCity = if (addressParts.isNotEmpty()) addressParts[0] else ""
            val jobDistrict = if (addressParts.size > 1) addressParts[1] else ""

            // City filtering
            val cityMatch = selectedCity == "전체" || jobCity == selectedCity

            // District filtering
            val districtMatch = selectedDistrict == "전체" || jobDistrict == selectedDistrict

            // Both conditions must match
            cityMatch && districtMatch
        }

        Log.d("PlaceViewModel", "Filtered ich_jobs: ${_filteredICHJobs.value.size} of ${_ichJobs.value.size}")
    }

    fun fetchBSJobsData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting bs_jobs data fetch")
                _isLoadingBSJobs.value = true

                val bsJobsData = withContext(Dispatchers.IO) {
                    try {
                        val bsJobs = supabaseHelper.getBSJobs()
                        Log.d("PlaceViewModel", "Supabase getBSJobs returned ${bsJobs.size} items")

                        if (bsJobs.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase bs_jobs returned empty list")
                        } else {
                            // Log first bs_job for debugging
                            val firstBSJob = bsJobs.firstOrNull()
                            if (firstBSJob != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample bs_job data: Id=${firstBSJob.Id}, " +
                                            "title=${firstBSJob.Title}, " +
                                            "category=${firstBSJob.Category}, " +
                                            "location=${firstBSJob.Address}"
                                )
                            }
                        }
                        bsJobs
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getBSJobs Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the bs_jobs value with the fetched data
                _bsJobs.value = bsJobsData
                _filteredBSJobs.value = bsJobsData

                Log.d("PlaceViewModel", "BS_Jobs data fetch complete: ${bsJobsData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching bs_jobs data: ${e.message}", e)
                _bsJobs.value = emptyList()
                _filteredBSJobs.value = emptyList()
            } finally {
                _isLoadingBSJobs.value = false
            }
        }
    }

    // 6. KB_Jobs 데이터 가져오기 함수
    fun fetchKBJobsData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting kb_jobs data fetch")
                _isLoadingKBJobs.value = true

                val kbJobsData = withContext(Dispatchers.IO) {
                    try {
                        val kbJobs = supabaseHelper.getKBJobs()
                        Log.d("PlaceViewModel", "Supabase getKBJobs returned ${kbJobs.size} items")

                        if (kbJobs.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase kb_jobs returned empty list")
                        } else {
                            // Log first kb_job for debugging
                            val firstKBJob = kbJobs.firstOrNull()
                            if (firstKBJob != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample kb_job data: Id=${firstKBJob.Id}, " +
                                            "title=${firstKBJob.Title}, " +
                                            "category=${firstKBJob.Category}, " +
                                            "location=${firstKBJob.Address}"
                                )
                            }
                        }
                        kbJobs
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getKBJobs Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the kb_jobs value with the fetched data
                _kbJobs.value = kbJobsData
                _filteredKBJobs.value = kbJobsData

                Log.d("PlaceViewModel", "KB_Jobs data fetch complete: ${kbJobsData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching kb_jobs data: ${e.message}", e)
                _kbJobs.value = emptyList()
                _filteredKBJobs.value = emptyList()
            } finally {
                _isLoadingKBJobs.value = false
            }
        }
    }

    // 7. BS_Jobs 필터링 함수
    fun filterBSJobs(selectedCity: String, selectedDistrict: String) {
        if (_bsJobs.value.isEmpty()) {
            _filteredBSJobs.value = emptyList()
            return
        }

        Log.d("PlaceViewModel", "Filtering bs_jobs with city='$selectedCity', district='$selectedDistrict'")

        _filteredBSJobs.value = _bsJobs.value.filter { bsJob ->
            val location = bsJob.Address ?: ""
            val addressParts = location.trim().split(" ")

            // Extract city and district from the location
            val jobCity = if (addressParts.isNotEmpty()) addressParts[0] else ""
            val jobDistrict = if (addressParts.size > 1) addressParts[1] else ""

            // City filtering
            val cityMatch = selectedCity == "전체" || jobCity == selectedCity

            // District filtering
            val districtMatch = selectedDistrict == "전체" || jobDistrict == selectedDistrict

            // Both conditions must match
            cityMatch && districtMatch
        }

        Log.d("PlaceViewModel", "Filtered bs_jobs: ${_filteredBSJobs.value.size} of ${_bsJobs.value.size}")
    }

    // 8. KB_Jobs 필터링 함수
    fun filterKBJobs(selectedCity: String, selectedDistrict: String) {
        if (_kbJobs.value.isEmpty()) {
            _filteredKBJobs.value = emptyList()
            return
        }

        Log.d("PlaceViewModel", "Filtering kb_jobs with city='$selectedCity', district='$selectedDistrict'")

        _filteredKBJobs.value = _kbJobs.value.filter { kbJob ->
            val location = kbJob.Address ?: ""
            val addressParts = location.trim().split(" ")

            // Extract city and district from the location
            val jobCity = if (addressParts.isNotEmpty()) addressParts[0] else ""
            val jobDistrict = if (addressParts.size > 1) addressParts[1] else ""

            // City filtering
            val cityMatch = selectedCity == "전체" || jobCity == selectedCity

            // District filtering
            val districtMatch = selectedDistrict == "전체" || jobDistrict == selectedDistrict

            // Both conditions must match
            cityMatch && districtMatch
        }

        Log.d("PlaceViewModel", "Filtered kb_jobs: ${_filteredKBJobs.value.size} of ${_kbJobs.value.size}")
    }

    // 통합 필터링 함수
    fun filterAllJobs(selectedCity: String, selectedDistrict: String) {
        // Regular jobs 필터링
        filterJobs(selectedCity, selectedDistrict)

        // KK_Jobs 필터링
        filterKKJobs(selectedCity, selectedDistrict)

        // ICH_Jobs 필터링
        filterICHJobs(selectedCity, selectedDistrict)

        // BS_Jobs 필터링
        filterBSJobs(selectedCity, selectedDistrict)

        // KB_Jobs 필터링
        filterKBJobs(selectedCity, selectedDistrict)

        Log.d("PlaceViewModel", "Filtered all jobs - Regular: ${_filteredJobs.value.size}, " +
                "KK: ${_filteredKKJobs.value.size}, ICH: ${_filteredICHJobs.value.size}, " +
                "BS: ${_filteredBSJobs.value.size}, KB: ${_filteredKBJobs.value.size}")
    }

    // 10. getTotalFilteredJobsCount 함수 수정 - BS_Jobs와 KB_Jobs 포함
    fun getTotalFilteredJobsCount(): Int {
        return _filteredJobs.value.size + _filteredKKJobs.value.size + _filteredICHJobs.value.size +
                _filteredBSJobs.value.size + _filteredKBJobs.value.size
    }

    fun fetchKKCulturesData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting kk_cultures data fetch")
                _isLoadingKKCultures.value = true

                val kkCulturesData = withContext(Dispatchers.IO) {
                    try {
                        val kkCultures = supabaseHelper.getKKCultures()
                        Log.d("PlaceViewModel", "Supabase getKKCultures returned ${kkCultures.size} items")

                        if (kkCultures.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase kk_cultures returned empty list")
                        } else {
                            // Log first kk_culture for debugging
                            val firstKKCulture = kkCultures.firstOrNull()
                            if (firstKKCulture != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample kk_culture data: Id=${firstKKCulture.Id}, " +
                                            "title=${firstKKCulture.Title}, " +
                                            "institution=${firstKKCulture.Institution}, " +
                                            "address=${firstKKCulture.Address}"
                                )
                            }
                        }
                        kkCultures
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getKKCultures Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the kk_cultures value with the fetched data
                _kkCultures.value = kkCulturesData
                _filteredKKCultures.value = kkCulturesData

                Log.d("PlaceViewModel", "KK_Cultures data fetch complete: ${kkCulturesData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching kk_cultures data: ${e.message}", e)
                _kkCultures.value = emptyList()
                _filteredKKCultures.value = emptyList()
            } finally {
                _isLoadingKKCultures.value = false
            }
        }
    }

    fun filterKKCultures(selectedCity: String, selectedDistrict: String) {
        if (_kkCultures.value.isEmpty()) {
            _filteredKKCultures.value = emptyList()
            return
        }

        Log.d("PlaceViewModel", "Filtering kk_cultures with city='$selectedCity', district='$selectedDistrict'")

        _filteredKKCultures.value = _kkCultures.value.filter { kkCulture ->
            // kk_culture는 항상 경기도 데이터
            val cultureCity = "경기도"
            val cultureDistrict = kkCulture.Category ?: "" // Category를 district로 사용

            // City filtering
            val cityMatch = selectedCity == "전체" || cultureCity == selectedCity

            // District filtering
            val districtMatch = selectedDistrict == "전체" || cultureDistrict == selectedDistrict

            // Both conditions must match
            cityMatch && districtMatch
        }

        Log.d("PlaceViewModel", "Filtered kk_cultures: ${_filteredKKCultures.value.size} of ${_kkCultures.value.size}")
    }

    fun fetchICHCulturesData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting ich_cultures data fetch")
                _isLoadingICHCultures.value = true

                val ichCulturesData = withContext(Dispatchers.IO) {
                    try {
                        val ichCultures = supabaseHelper.getICHCultures()
                        Log.d("PlaceViewModel", "Supabase getICHCultures returned ${ichCultures.size} items")

                        if (ichCultures.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase ich_cultures returned empty list")
                        } else {
                            // Log first ich_culture for debugging
                            val firstICHCulture = ichCultures.firstOrNull()
                            if (firstICHCulture != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample ich_culture data: Id=${firstICHCulture.Id}, " +
                                            "title=${firstICHCulture.Title}, " +
                                            "institution=${firstICHCulture.Institution}, " +
                                            "address=${firstICHCulture.Address}, " +
                                            "category=${firstICHCulture.Category}"
                                )
                            }
                        }
                        ichCultures
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getICHCultures Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the ich_cultures value with the fetched data
                _ichCultures.value = ichCulturesData
                _filteredICHCultures.value = ichCulturesData

                Log.d("PlaceViewModel", "ICH_Cultures data fetch complete: ${ichCulturesData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching ich_cultures data: ${e.message}", e)
                _ichCultures.value = emptyList()
                _filteredICHCultures.value = emptyList()
            } finally {
                _isLoadingICHCultures.value = false
            }
        }
    }

    fun filterICHCultures(selectedCity: String, selectedDistrict: String) {
        if (_ichCultures.value.isEmpty()) {
            _filteredICHCultures.value = emptyList()
            return
        }

        Log.d("PlaceViewModel", "Filtering ich_cultures with city='$selectedCity', district='$selectedDistrict'")

        _filteredICHCultures.value = _ichCultures.value.filter { ichCulture ->
            // ich_culture는 인천광역시 데이터
            val cultureCity = "인천광역시"
            val cultureDistrict = ichCulture.Category ?: "" // Category를 district로 사용

            // City filtering
            val cityMatch = selectedCity == "전체" || cultureCity == selectedCity

            // District filtering
            val districtMatch = selectedDistrict == "전체" || cultureDistrict == selectedDistrict

            // Both conditions must match
            cityMatch && districtMatch
        }

        Log.d("PlaceViewModel", "Filtered ich_cultures: ${_filteredICHCultures.value.size} of ${_ichCultures.value.size}")
    }

    fun fetchBSCulturesData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting bs_cultures data fetch")
                _isLoadingBSCultures.value = true

                val bsCulturesData = withContext(Dispatchers.IO) {
                    try {
                        val bsCultures = supabaseHelper.getBSCultures()
                        Log.d("PlaceViewModel", "Supabase getBSCultures returned ${bsCultures.size} items")

                        if (bsCultures.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase bs_cultures returned empty list")
                        } else {
                            // Log first bs_culture for debugging
                            val firstBSCulture = bsCultures.firstOrNull()
                            if (firstBSCulture != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample bs_culture data: Id=${firstBSCulture.Id}, " +
                                            "title=${firstBSCulture.Title}, " +
                                            "institution=${firstBSCulture.Institution}, " +
                                            "address=${firstBSCulture.Address}"
                                )
                            }
                        }
                        bsCultures
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getBSCultures Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the bs_cultures value with the fetched data
                _bsCultures.value = bsCulturesData
                _filteredBSCultures.value = bsCulturesData

                Log.d("PlaceViewModel", "BS_Cultures data fetch complete: ${bsCulturesData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching bs_cultures data: ${e.message}", e)
                _bsCultures.value = emptyList()
                _filteredBSCultures.value = emptyList()
            } finally {
                _isLoadingBSCultures.value = false
            }
        }
    }

    // 5. KB_Cultures 데이터 가져오기 함수
    fun fetchKBCulturesData() {
        viewModelScope.launch {
            try {
                Log.d("PlaceViewModel", "Starting kb_cultures data fetch")
                _isLoadingKBCultures.value = true

                val kbCulturesData = withContext(Dispatchers.IO) {
                    try {
                        val kbCultures = supabaseHelper.getKBCultures()
                        Log.d("PlaceViewModel", "Supabase getKBCultures returned ${kbCultures.size} items")

                        if (kbCultures.isEmpty()) {
                            Log.d("PlaceViewModel", "Supabase kb_cultures returned empty list")
                        } else {
                            // Log first kb_culture for debugging
                            val firstKBCulture = kbCultures.firstOrNull()
                            if (firstKBCulture != null) {
                                Log.d(
                                    "PlaceViewModel", "Sample kb_culture data: Id=${firstKBCulture.Id}, " +
                                            "title=${firstKBCulture.Title}, " +
                                            "institution=${firstKBCulture.Institution}, " +
                                            "address=${firstKBCulture.Address}"
                                )
                            }
                        }
                        kbCultures
                    } catch (e: Exception) {
                        Log.e(
                            "PlaceViewModel",
                            "Error in getKBCultures Dispatchers.IO block: ${e.message}",
                            e
                        )
                        emptyList()
                    }
                }

                // Update the kb_cultures value with the fetched data
                _kbCultures.value = kbCulturesData
                _filteredKBCultures.value = kbCulturesData

                Log.d("PlaceViewModel", "KB_Cultures data fetch complete: ${kbCulturesData.size} items")
            } catch (e: Exception) {
                Log.e("PlaceViewModel", "Error fetching kb_cultures data: ${e.message}", e)
                _kbCultures.value = emptyList()
                _filteredKBCultures.value = emptyList()
            } finally {
                _isLoadingKBCultures.value = false
            }
        }
    }

    // 6. BS_Cultures 필터링 함수
    fun filterBSCultures(selectedCity: String, selectedDistrict: String) {
        if (_bsCultures.value.isEmpty()) {
            _filteredBSCultures.value = emptyList()
            return
        }

        Log.d("PlaceViewModel", "Filtering bs_cultures with city='$selectedCity', district='$selectedDistrict'")

        _filteredBSCultures.value = _bsCultures.value.filter { bsCulture ->
            // bs_culture는 부산광역시 데이터
            val cultureCity = "부산광역시"
            val cultureDistrict = bsCulture.Category ?: "" // Category를 district로 사용

            // City filtering
            val cityMatch = selectedCity == "전체" || cultureCity == selectedCity

            // District filtering
            val districtMatch = selectedDistrict == "전체" || cultureDistrict == selectedDistrict

            // Both conditions must match
            cityMatch && districtMatch
        }

        Log.d("PlaceViewModel", "Filtered bs_cultures: ${_filteredBSCultures.value.size} of ${_bsCultures.value.size}")
    }

    // 7. KB_Cultures 필터링 함수
    fun filterKBCultures(selectedCity: String, selectedDistrict: String) {
        if (_kbCultures.value.isEmpty()) {
            _filteredKBCultures.value = emptyList()
            return
        }

        Log.d("PlaceViewModel", "Filtering kb_cultures with city='$selectedCity', district='$selectedDistrict'")

        _filteredKBCultures.value = _kbCultures.value.filter { kbCulture ->
            // kb_culture는 경상북도 데이터
            val cultureCity = "경상북도"
            val cultureDistrict = kbCulture.Category ?: "" // Category를 district로 사용

            // City filtering
            val cityMatch = selectedCity == "전체" || cultureCity == selectedCity

            // District filtering
            val districtMatch = selectedDistrict == "전체" || cultureDistrict == selectedDistrict

            // Both conditions must match
            cityMatch && districtMatch
        }

        Log.d("PlaceViewModel", "Filtered kb_cultures: ${_filteredKBCultures.value.size} of ${_kbCultures.value.size}")
    }


    // 통합 필터링 함수
    fun filterAllCultures(selectedCity: String, selectedDistrict: String) {
        // Regular lectures 필터링
        filterLectures(selectedCity, selectedDistrict)

        // KK_Cultures 필터링
        filterKKCultures(selectedCity, selectedDistrict)

        // ICH_Cultures 필터링
        filterICHCultures(selectedCity, selectedDistrict)

        // BS_Cultures 필터링
        filterBSCultures(selectedCity, selectedDistrict)

        // KB_Cultures 필터링
        filterKBCultures(selectedCity, selectedDistrict)

        Log.d("PlaceViewModel", "Filtered all cultures - Regular: ${_filteredLectures.value.size}, " +
                "KK: ${_filteredKKCultures.value.size}, ICH: ${_filteredICHCultures.value.size}, " +
                "BS: ${_filteredBSCultures.value.size}, KB: ${_filteredKBCultures.value.size}")
    }

    // 9. getTotalFilteredCulturesCount 함수 수정 - BS_Cultures와 KB_Cultures 포함
    fun getTotalFilteredCulturesCount(): Int {
        return _filteredLectures.value.size + _filteredKKCultures.value.size + _filteredICHCultures.value.size +
                _filteredBSCultures.value.size + _filteredKBCultures.value.size
    }

    fun getFilteredPlacesByUserLocation(): List<Place> {
        Log.d("PlaceViewModel", "getFilteredPlacesByUserLocation called - City: $userCity, District: $userDistrict")

        if (userCity.isEmpty() || userDistrict.isEmpty() ||
            userCity == "위치 확인 중..." || userCity == "위치 권한 없음") {
            Log.d("PlaceViewModel", "위치 정보가 유효하지 않음, 빈 리스트 반환")
            return emptyList()
        }

        val filteredPlaces = mutableListOf<Place>()

        // 서울특별시 시설 필터링
        if (userCity == "서울특별시") {
            // API 데이터 (서울 열린데이터)
            _allPlaces.value.filter { place ->
                // API 데이터는 district 필드에 구 정보가 있음
                place.district == userDistrict &&
                        place.address.contains("서울")
            }.forEach { filteredPlaces.add(it) }

            // Supabase 데이터 (facility 테이블)
            _allPlaces.value.filter { place ->
                place.id.toIntOrNull() != null && // Supabase 데이터는 숫자 ID
                        place.address.contains("서울") &&
                        place.address.contains(userDistrict)
            }.forEach {
                if (!filteredPlaces.contains(it)) {
                    filteredPlaces.add(it)
                }
            }
        }

        // 경기도 시설 필터링
        if (userCity == "경기도") {
            // KK Facility 데이터
            _allPlaces.value.filter { place ->
                place.id.startsWith("kk_") || place.id.startsWith("kk2_")
            }.filter { place ->
                val addressParts = place.address.trim().split(" ")
                val placeDistrict = if (addressParts.size > 1) addressParts[1] else ""
                placeDistrict == userDistrict || place.address.contains(userDistrict)
            }.forEach { filteredPlaces.add(it) }

            // 기타 경기도 시설
            _allPlaces.value.filter { place ->
                !place.id.startsWith("kk_") && !place.id.startsWith("kk2_") &&
                        place.address.contains("경기") &&
                        place.address.contains(userDistrict)
            }.forEach {
                if (!filteredPlaces.contains(it)) {
                    filteredPlaces.add(it)
                }
            }
        }

        // 인천광역시 시설 필터링
        if (userCity == "인천광역시") {
            // ICH Facility 데이터
            _allPlaces.value.filter { place ->
                place.id.startsWith("ich_") || place.id.startsWith("ich2_")
            }.filter { place ->
                val addressParts = place.address.trim().split(" ")
                val placeDistrict = if (addressParts.size > 1) addressParts[1] else ""
                placeDistrict == userDistrict || place.address.contains(userDistrict)
            }.forEach { filteredPlaces.add(it) }

            // 기타 인천 시설
            _allPlaces.value.filter { place ->
                !place.id.startsWith("ich_") && !place.id.startsWith("ich2_") &&
                        place.address.contains("인천") &&
                        place.address.contains(userDistrict)
            }.forEach {
                if (!filteredPlaces.contains(it)) {
                    filteredPlaces.add(it)
                }
            }
        }

        // 부산광역시 시설 필터링
        if (userCity == "부산광역시") {
            // BS Facility 데이터
            _allPlaces.value.filter { place ->
                place.id.startsWith("bs_") || place.id.startsWith("bs2_")
            }.filter { place ->
                val addressParts = place.address.trim().split(" ")
                val placeDistrict = if (addressParts.size > 1) addressParts[1] else ""
                placeDistrict == userDistrict || place.address.contains(userDistrict)
            }.forEach { filteredPlaces.add(it) }

            // 기타 부산 시설
            _allPlaces.value.filter { place ->
                !place.id.startsWith("bs_") && !place.id.startsWith("bs2_") &&
                        place.address.contains("부산") &&
                        place.address.contains(userDistrict)
            }.forEach {
                if (!filteredPlaces.contains(it)) {
                    filteredPlaces.add(it)
                }
            }
        }

        // 경상북도 시설 필터링
        if (userCity == "경상북도") {
            // KB Facility 데이터
            _allPlaces.value.filter { place ->
                place.id.startsWith("kb_") || place.id.startsWith("kb2_")
            }.filter { place ->
                val addressParts = place.address.trim().split(" ")
                val placeDistrict = if (addressParts.size > 1) addressParts[1] else ""
                placeDistrict == userDistrict || place.address.contains(userDistrict)
            }.forEach { filteredPlaces.add(it) }

            // 기타 경북 시설
            _allPlaces.value.filter { place ->
                !place.id.startsWith("kb_") && !place.id.startsWith("kb2_") &&
                        place.address.contains("경상북도") &&
                        place.address.contains(userDistrict)
            }.forEach {
                if (!filteredPlaces.contains(it)) {
                    filteredPlaces.add(it)
                }
            }
        }

        Log.d("PlaceViewModel", "필터링 결과: ${filteredPlaces.size}개 시설")

        // 로그로 처음 3개 시설 출력
        filteredPlaces.take(3).forEach { place ->
            Log.d("PlaceViewModel", "시설: ${place.name} (${place.address})")
        }

        // 결과가 없을 경우 전체 데이터에서 무작위로 반환
        return if (filteredPlaces.isEmpty()) {
            Log.d("PlaceViewModel", "필터링 결과 없음, 전체 데이터에서 무작위 선택")
            _allPlaces.value.shuffled().take(10)
        } else {
            filteredPlaces  // 전체 리스트 반환
        }
    }

    // 사용자 위치 기반으로 일자리 필터링
    fun getFilteredJobsByUserLocation(): List<Any> {
        val filteredJobs = mutableListOf<Any>()

        if (userCity.isNotEmpty() && userDistrict.isNotEmpty()) {
            // Regular jobs 필터링
            _jobs.value.filter { job ->
                val location = job.Address ?: ""
                location.contains(userCity) && location.contains(userDistrict)
            }.forEach { filteredJobs.add(it) }

            // KK jobs 필터링
            _kkJobs.value.filter { kkJob ->
                val location = kkJob.Address ?: ""
                location.contains(userCity) && location.contains(userDistrict)
            }.forEach { filteredJobs.add(it) }

            // ICH jobs 필터링
            _ichJobs.value.filter { ichJob ->
                val location = ichJob.Address ?: ""
                location.contains(userCity) && location.contains(userDistrict)
            }.forEach { filteredJobs.add(it) }

            // BS jobs 필터링
            _bsJobs.value.filter { bsJob ->
                val location = bsJob.Address ?: ""
                location.contains(userCity) && location.contains(userDistrict)
            }.forEach { filteredJobs.add(it) }

            // KB jobs 필터링
            _kbJobs.value.filter { kbJob ->
                val location = kbJob.Address ?: ""
                location.contains(userCity) && location.contains(userDistrict)
            }.forEach { filteredJobs.add(it) }
        }

        return if (filteredJobs.isEmpty()) {
            // 해당 지역에 데이터가 없으면 전체 데이터 반환
            (_jobs.value + _kkJobs.value + _ichJobs.value + _bsJobs.value + _kbJobs.value)
        } else {
            filteredJobs
        }
    }

    // 사용자 위치 기반으로 문화강좌 필터링
    fun getFilteredCulturesByUserLocation(): List<Any> {
        val filteredCultures = mutableListOf<Any>()

        if (userCity.isNotEmpty() && userDistrict.isNotEmpty()) {
            // Regular lectures 필터링
            if (userCity == "서울특별시") {
                _lectures.value.filter { lecture ->
                    lecture.Category == userDistrict
                }.forEach { filteredCultures.add(it) }
            }

            // KK cultures 필터링 (경기도 데이터)
            if (userCity == "경기도") {
                _kkCultures.value.filter { kkCulture ->
                    kkCulture.Category == userDistrict
                }.forEach { filteredCultures.add(it) }
            }

            // ICH cultures 필터링 (인천광역시 데이터)
            if (userCity == "인천광역시") {
                _ichCultures.value.filter { ichCulture ->
                    ichCulture.Category == userDistrict
                }.forEach { filteredCultures.add(it) }
            }

            // BS cultures 필터링 (부산광역시 데이터)
            if (userCity == "부산광역시") {
                _bsCultures.value.filter { bsCulture ->
                    bsCulture.Category == userDistrict
                }.forEach { filteredCultures.add(it) }
            }

            // KB cultures 필터링 (경상북도 데이터)
            if (userCity == "경상북도") {
                _kbCultures.value.filter { kbCulture ->
                    kbCulture.Category == userDistrict
                }.forEach { filteredCultures.add(it) }
            }
        }

        return if (filteredCultures.isEmpty()) {
            // 해당 지역에 데이터가 없으면 전체 데이터 반환
            (_lectures.value + _kkCultures.value + _ichCultures.value + _bsCultures.value + _kbCultures.value)
        } else {
            filteredCultures
        }
    }

    fun isDataLoaded(): Boolean {
        return _allPlaces.value.isNotEmpty()
    }
}