package com.techlion.healthconnectexporter

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

class ExportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_START_TIME = "start_time"
        const val KEY_END_TIME = "end_time"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_FILE = "output_file"
        const val KEY_ERROR = "error"
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(applicationContext)

            val startTime = inputData.getString(KEY_START_TIME)?.let { Instant.parse(it) }
                ?: Instant.ofEpochSecond(0)
            val endTime = inputData.getString(KEY_END_TIME)?.let { Instant.parse(it) }
                ?: Instant.now()

            val timeRange = TimeRangeFilter.between(startTime, endTime)
            val allData = mutableMapOf<String, Any>()

            val recordTypes = listOf(
                "steps" to StepsRecord::class,
                "heart_rate" to HeartRateRecord::class,
                "resting_heart_rate" to RestingHeartRateRecord::class,
                "heart_rate_variability" to HeartRateVariabilityRmssdRecord::class,
                "blood_pressure" to BloodPressureRecord::class,
                "blood_glucose" to BloodGlucoseRecord::class,
                "body_temperature" to BodyTemperatureRecord::class,
                "oxygen_saturation" to OxygenSaturationRecord::class,
                "body_fat" to BodyFatRecord::class,
                "weight" to WeightRecord::class,
                "height" to HeightRecord::class,
                "lean_body_mass" to LeanBodyMassRecord::class,
                "basal_metabolic_rate" to BasalMetabolicRateRecord::class,
                "active_calories" to ActiveCaloriesBurnedRecord::class,
                "total_calories" to TotalCaloriesBurnedRecord::class,
                "exercise_sessions" to ExerciseSessionRecord::class,
                "total_steps" to TotalStepsRecord::class,
                "distance" to DistanceRecord::class,
                "floors_climbed" to FloorsClimbedRecord::class,
                "nutrition" to NutritionRecord::class,
                "hydration" to HydrationRecord::class,
                "sleep_sessions" to SleepSessionRecord::class,
                "sleep_stages" to SleepStageRecord::class,
                "vo2_max" to Vo2MaxRecord::class,
                "power" to PowerRecord::class,
                "speed" to SpeedRecord::class,
                "cycling_revolutions" to CyclingWheelRevolutionRecord::class,
                "cycling_rpm" to CyclingWheelRpmRecord::class,
                "respiratory_rate" to RespiratoryRateRecord::class
            )

            val total = recordTypes.size
            recordTypes.forEachIndexed { index, (name, recordClass) ->
                val progress = ((index + 1) * 100) / total
                setProgress(workDataOf(KEY_PROGRESS to progress))

                try {
                    val result = readAllPages(client, recordClass, timeRange)
                    allData[name] = result
                } catch (e: Exception) {
                    allData[name] = mapOf("error" to (e.message ?: "unknown"))
                }
            }

            // Write to file
            val json = gson.toJson(allData)
            val exportDir = File(applicationContext.filesDir, "exports")
            exportDir.mkdirs()
            val timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val file = File(exportDir, "health_export_$timestamp.json")
            file.writeText(json)

            setProgress(workDataOf(KEY_PROGRESS to 100))
            Result.success(workDataOf(KEY_OUTPUT_FILE to file.absolutePath))

        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Export failed")))
        }
    }

    private suspend inline fun <reified T : androidx.health.connect.client.records.Record> readAllPages(
        client: HealthConnectClient,
        recordClass: Class<T>,
        timeRange: TimeRangeFilter
    ): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        var lastRecordTime: Instant? = null

        while (true) {
            val request = ReadRecordsRequest(
                recordType = recordClass,
                timeRangeFilter = if (lastRecordTime != null) {
                    TimeRangeFilter.between(lastRecordTime, Instant.parse(inputData.getString(KEY_END_TIME) ?: Instant.now().toString()))
                } else {
                    timeRange
                },
                pageSize = 1000
            )

            val response = try {
                client.readRecords(request)
            } catch (e: Exception) {
                // If this record type isn't available, skip it
                return results
            }

            if (response.records.isEmpty()) break

            for (record in response.records) {
                val map = mutableMapOf<String, Any?>()
                // Use reflection to extract common fields
                record::class.java.declaredFields.forEach { field ->
                    field.isAccessible = true
                    val value = field.get(record)
                    if (value != null && !field.name.contains("$")) {
                        map[field.name] = value.toString()
                    }
                }
                // Also extract via toMap if available
                try {
                    val method = record::class.java.getMethod("toMap")
                    @Suppress("UNCHECKED_CAST")
                    val toMapResult = method.invoke(record) as? Map<String, Any?>
                    toMapResult?.let { map.putAll(it) }
                } catch (_: Exception) {}

                results.add(map)
                try {
                    val timeField = record::class.java.getMethod("getEndTime")
                    lastRecordTime = timeField.invoke(record) as? Instant
                } catch (_: Exception) {}
            }

            if (!response.hasNextPage) break
        }

        return results
    }
}