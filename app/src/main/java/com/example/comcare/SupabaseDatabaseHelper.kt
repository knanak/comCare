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

                    Log.d("supabase", "Fetched batch size: ${batch.size}")
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
}