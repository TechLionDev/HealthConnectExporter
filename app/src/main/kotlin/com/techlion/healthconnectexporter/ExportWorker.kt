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
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.records.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

class ExportWorker(private val context: Context) {

    private val client = HealthConnectClient.Builder(context).build()

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
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
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class)
    )

    suspend fun hasAllPermissions(): Boolean {
        return client.permissionController.getGrantedPermissions()
            .containsAll(permissions)
    }

    suspend fun exportAll(onProgress: (String, Int, Int) -> Unit): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var total = 0
            val epochStart = Instant.EPOCH

            val recordTypes: List<Pair<KClass<out androidx.health.connect.client.records.Record>, String>> = listOf(
                StepsRecord::class to "steps",
                ActiveCaloriesBurnedRecord::class to "activeCaloriesBurned",
                HeartRateRecord::class to "heartRate",
                RestingHeartRateRecord::class to "restingHeartRate",
                SleepSessionRecord::class to "sleepSession",
                HydrationRecord::class to "hydration",
                NutritionRecord::class to "nutrition",
                WeightRecord::class to "weight",
                HeightRecord::class to "height",
                BloodGlucoseRecord::class to "bloodGlucose",
                BloodPressureRecord::class to "bloodPressure",
                BasalBodyTemperatureRecord::class to "basalBodyTemperature",
                BodyTemperatureRecord::class to "bodyTemperature",
                OxygenSaturationRecord::class to "oxygenSaturation",
                Vo2MaxRecord::class to "vo2Max",
                SpeedRecord::class to "speed",
                RespiratoryRateRecord::class to "respiratoryRate",
                BodyFatRecord::class to "bodyFat"
            )

            for ((index, pair) in recordTypes.withIndex()) {
                val recordType = pair.first
                val name = pair.second
                var count = 0

                try {
                    count = exportRecordType(recordType, name, epochStart)
                } catch (e: Exception) {
                    // Some record types may not exist on all devices — skip silently
                }

                if (count > 0) {
                    onProgress(name, count, index + 1)
                    total += count
                }
            }

            Result.success(total)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @ androidx.annotation.OptIn(androidx.health.connect.client.ExperimentalHealthRecordMetadata::class)
    private suspend inline fun <reified T : androidx.health.connect.client.records.Record> exportRecordType(
        recordType: KClass<out androidx.health.connect.client.records.Record>,
        name: String,
        epochStart: Instant
    ): Int {
        var pageToken: String? = null
        var recordCount = 0
        val file = getExportFile(name)

        do {
            val request = ReadRecordsRequest(
                recordType = recordType.kotlin as KClass<T>,
                timeRangeFilter = TimeRangeFilter.after(epochStart),
                pageSize = 10000,
                pageToken = pageToken
            )
            val response = client.readRecords(request)
            for (record in response.records) {
                file.appendText(toCsv(record as androidx.health.connect.client.records.Record))
                recordCount++
            }
            pageToken = response.pageToken
        } while (pageToken != null)

        return recordCount
    }

    private fun getExportFile(name: String): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "HealthConnectExport")
        dir.mkdirs()
        return File(dir, "$name.csv").also { if (it.exists()) it.delete() }
    }

    private fun formatTime(time: Instant): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC).format(time)

    private fun toCsv(record: androidx.health.connect.client.records.Record): String {
        return try {
            when (record) {
                is StepsRecord -> "${formatTime(record.startTime)},${formatTime(record.endTime)},${record.count},${record.metadata.dataOrigin.packageName}"
                is ActiveCaloriesBurnedRecord -> "${formatTime(record.startTime)},${formatTime(record.endTime)},${record.energy?.inKilocalories ?: 0.0},${record.metadata.dataOrigin.packageName}"
                is HeartRateRecord -> {
                    val samples = record.samples.joinToString(";") { "${formatTime(it.time)},${it.beatsPerMinute}" }
                    "${formatTime(record.startTime)},${formatTime(record.endTime)},$samples,${record.metadata.dataOrigin.packageName}"
                }
                is RestingHeartRateRecord -> "${formatTime(record.startTime)},${formatTime(record.endTime)},${record.measuredValue},${record.metadata.dataOrigin.packageName}"
                is SleepSessionRecord -> {
                    val stages = record.stages.joinToString(";") { "${it.stage.name},${formatTime(it.startTime)},${formatTime(it.endTime)}" }
                    "${formatTime(record.startTime)},${formatTime(record.endTime)},${record.title ?: "Sleep"},$stages,${record.metadata.dataOrigin.packageName}"
                }
                is HydrationRecord -> "${formatTime(record.startTime)},${formatTime(record.endTime)},${record.volume?.inMilliliters ?: 0.0},${record.metadata.dataOrigin.packageName}"
                is NutritionRecord -> {
                    val energy = record.energy?.inKilocalories ?: 0.0
                    val protein = record.protein?.inGrams ?: 0.0
                    val carbs = record.carbohydrates?.inGrams ?: 0.0
                    val fat = record.fat?.inGrams ?: 0.0
                    "${formatTime(record.startTime)},${formatTime(record.endTime)},$energy,$protein,$carbs,$fat,${record.metadata.dataOrigin.packageName}"
                }
                is WeightRecord -> "${formatTime(record.time)},${record.weight?.inKilograms ?: 0.0},${record.metadata.dataOrigin.packageName}"
                is HeightRecord -> "${formatTime(record.time)},${record.height?.inMeters ?: 0.0},${record.metadata.dataOrigin.packageName}"
                is BloodGlucoseRecord -> "${formatTime(record.time)},${record.level?.inMillimolesPerLiter ?: 0.0},${record.metadata.dataOrigin.packageName}"
                is BloodPressureRecord -> "${formatTime(record.startTime)},${formatTime(record.endTime)},${record.diastolic?.inMillimetersOfMercury ?: 0.0},${record.systolic?.inMillimetersOfMercury ?: 0.0},${record.metadata.dataOrigin.packageName}"
                is BasalBodyTemperatureRecord -> "${formatTime(record.time)},${record.measurement?.inCelsius ?: 0.0},${record.metadata.dataOrigin.packageName}"
                is BodyTemperatureRecord -> "${formatTime(record.time)},${record.measurement?.inCelsius ?: 0.0},${record.metadata.dataOrigin.packageName}"
                is OxygenSaturationRecord -> "${formatTime(record.startTime)},${formatTime(record.endTime)},${record.percentage?.value ?: 0.0},${record.metadata.dataOrigin.packageName}"
                is Vo2MaxRecord -> "${formatTime(record.startTime)},${formatTime(record.endTime)},${record.rating?.name ?: ""},${record.measurement?.inMillilitersPerKilogramPerMinute ?: 0.0},${record.metadata.dataOrigin.packageName}"
                is SpeedRecord -> {
                    val samples = record.samples.joinToString(";") { "${formatTime(it.time)},${it.metersPerSecond}" }
                    "${formatTime(record.startTime)},${formatTime(record.endTime)},$samples,${record.metadata.dataOrigin.packageName}"
                }
                is RespiratoryRateRecord -> {
                    val rate = record.rate?.value ?: 0.0
                    "${formatTime(record.startTime)},${formatTime(record.endTime)},$rate,${record.metadata.dataOrigin.packageName}"
                }
                is BodyFatRecord -> "${formatTime(record.time)},${record.percentage?.value ?: 0.0},${record.metadata.dataOrigin.packageName}"
                else -> "${formatTime(record.startTime)},${record.javaClass.simpleName},${record.metadata.dataOrigin.packageName}"
            } + "\n"
        } catch (e: Exception) {
            "1970-01-01 00:00:00,ERROR:${e.message}\n"
        }
    }
}
