package com.techlion.healthconnectexporter

import android.content.Context
import android.os.Environment
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.filter.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

class ExportWorker(private val context: Context) {

    private val healthConnectClient = HealthConnectClient.getOrCreate(context)

    private val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(BasalBodyTemperatureRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class)
    )

    suspend fun hasAllPermissions(): Boolean {
        return healthConnectClient.permissionController.getGrantedPermissions()
            .containsAll(permissions)
    }

    suspend fun exportAll(callback: (String, Int, Int) -> Unit): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var totalRecords = 0
            val epochStart = Instant.EPOCH

            val recordHandlers: List<Pair<String, suspend (String) -> Int>> = listOf(
                "steps" to { name -> readAndExport<StepsRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.startTime)},${formatTime(rec.endTime)},${rec.count},${rec.metadata.dataOrigin.packageName}"
                }},
                "activeCaloriesBurned" to { name -> readAndExport<ActiveCaloriesBurnedRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.startTime)},${formatTime(rec.endTime)},${rec.energy.inKilocalories},${rec.metadata.dataOrigin.packageName}"
                }},
                "totalCaloriesBurned" to { name -> readAndExport<TotalCaloriesBurnedRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.startTime)},${formatTime(rec.endTime)},${rec.energy.inKilocalories},${rec.metadata.dataOrigin.packageName}"
                }},
                "heartRate" to { name -> readAndExport<HeartRateRecord>(name, epochStart) { rec ->
                    val samples = rec.samples.joinToString(";") { "${it.time}:${it.beatsPerMinute}" }
                    "${formatTime(rec.startTime)},${formatTime(rec.endTime)},$samples,${rec.metadata.dataOrigin.packageName}"
                }},
                "restingHeartRate" to { name -> readAndExport<RestingHeartRateRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.startTime)},${formatTime(rec.endTime)},${rec.measuredValue},${rec.metadata.dataOrigin.packageName}"
                }},
                "sleepSession" to { name -> readAndExport<SleepSessionRecord>(name, epochStart) { rec ->
                    val stages = rec.stages.joinToString(";") { "${it.stage.name}=${formatTime(it.startTime)}" }
                    "${formatTime(rec.startTime)},${formatTime(rec.endTime)},${rec.title ?: "Sleep"},$stages,${rec.metadata.dataOrigin.packageName}"
                }},
                "hydration" to { name -> readAndExport<HydrationRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.startTime)},${formatTime(rec.endTime)},${rec.volume.inMilliliters},${rec.metadata.dataOrigin.packageName}"
                }},
                "nutrition" to { name -> readAndExport<NutritionRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.startTime)},${formatTime(rec.endTime)},${rec.energy?.inKilocalories ?: 0},${rec.protein?.inGrams ?: 0},${rec.totalCarbohydrate?.inGrams ?: 0},${rec.totalFat?.inGrams ?: 0},${rec.metadata.dataOrigin.packageName}"
                }},
                "weight" to { name -> readAndExport<WeightRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.time)},${rec.weight.inKilograms},${rec.metadata.dataOrigin.packageName}"
                }},
                "height" to { name -> readAndExport<HeightRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.time)},${rec.height.inMeters},${rec.metadata.dataOrigin.packageName}"
                }},
                "bloodGlucose" to { name -> readAndExport<BloodGlucoseRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.time)},${rec.level.inMillimolesPerLiter},${rec.specimenSource?.name ?: ""},${rec.metadata.dataOrigin.packageName}"
                }},
                "bloodPressure" to { name -> readAndExport<BloodPressureRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.startTime)},${formatTime(rec.endTime)},${rec.diastolic?.inMillimetersOfMercury ?: 0},${rec.systolic?.inMillimetersOfMercury ?: 0},${rec.metadata.dataOrigin.packageName}"
                }},
                "basalBodyTemperature" to { name -> readAndExport<BasalBodyTemperatureRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.time)},${rec.measurement.inCelsius},${rec.metadata.dataOrigin.packageName}"
                }},
                "oxygenSaturation" to { name -> readAndExport<OxygenSaturationRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.startTime)},${formatTime(rec.endTime)},${rec.percentage?.value?.times(100) ?: 0},${rec.metadata.dataOrigin.packageName}"
                }},
                "respiratoryRate" to { name -> readAndExport<RespiratoryRateRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.startTime)},${formatTime(rec.endTime)},${rec.measurement?.rate ?: 0},${rec.metadata.dataOrigin.packageName}"
                }},
                "bodyFat" to { name -> readAndExport<BodyFatRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.time)},${rec.percentage?.value?.times(100) ?: 0},${rec.metadata.dataOrigin.packageName}"
                }},
                "distance" to { name -> readAndExport<DistanceRecord>(name, epochStart) { rec ->
                    "${formatTime(rec.startTime)},${formatTime(rec.endTime)},${rec.distance.inMeters},${rec.metadata.dataOrigin.packageName}"
                }}
            )

            for ((index, handler) in recordHandlers.withIndex()) {
                val fileName = handler.first
                val count = handler.second(fileName)
                if (count > 0) {
                    callback(fileName, count, index + 1)
                    totalRecords += count
                }
            }

            Result.success(totalRecords)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend inline fun <reified T : androidx.health.connect.client.records.Record> readAndExport(
        fileName: String,
        startTime: Instant,
        crossinline toLine: (T) -> String
    ): Int {
        var pageToken: String? = null
        var count = 0
        val file = getExportFile(fileName)
        file.delete()

        do {
            val request = ReadRecordsRequest(
                recordType = T::class,
                timeRangeFilter = TimeRangeFilter.after(startTime),
                pageSize = 5000,
                pageToken = pageToken
            )
            val response = healthConnectClient.readRecords(request)
            for (record in response.records) {
                file.appendText(toLine(record as T) + "\n")
                count++
            }
            pageToken = response.pageToken
        } while (pageToken != null)

        return count
    }

    private fun getExportFile(name: String): File {
        val exportDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "HealthConnectExport"
        )
        exportDir.mkdirs()
        return File(exportDir, "${name}.csv")
    }

    private fun formatTime(time: Instant): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC)
            .format(time)
    }
}
