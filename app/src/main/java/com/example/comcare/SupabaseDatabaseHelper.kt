package com.example.comcare

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
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
    private fun getCurrentTimestampIso8601(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

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
        val id: Int,
        val JobTitle: String? = null,
        val DateOfRegistration: String? = null,
        val Deadline: String? = null,
        val JobCategory: String? = null,
        val ExperienceRequired: String? = null,
        val EmploymentType: String? = null,
        val Salary: String? = null,
        val SocialEnsurance: String? = null,
        val RetirementBenefit: String? = null,
        val Location: String? = null,
        val WorkingHours: String? = null,
        val WorkingType: String? = null,
        val CompanyName: String? = null,
        val JobDescription: String? = null,
        val ApplicationMethod: String? = null,
        val ApplicationType: String? = null,
        val document: String? = null
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
                        Log.d("supabase", "Sample job: id=${sample.id}, " +
                                "title=${sample.JobTitle}, " +
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
        val Institution: String? = null,
        val Title: String? = null,
        val Recruitment_period: String? = null,
        val Education_period: String? = null,
        val Fees: String? = null,
        val Quota: String? = null
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
        val Category: String? = null,
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
        val WorkingHours: String? = null,
        val WorkingType: String? = null,
        val CompanyName: String? = null,
        val JobDescription: String? = null,
        val ApplicationMethod: String? = null,
        val ApplicationType: String? = null,
        val document: String? = null
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
}