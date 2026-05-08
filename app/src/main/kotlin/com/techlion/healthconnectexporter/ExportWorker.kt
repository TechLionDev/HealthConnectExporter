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
import androidx.health.connect.client.records.BodyTemperatureRecord
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
import androidx.health.connect.client.filter.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ExportWorker(private val context: Context) {

    private val healthConnectClient = HealthConnectClient.Builder(context).build()

    private val permissions = setOf(
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
        return healthConnectClient.permissionController.getGrantedPermissions()
            .containsAll(permissions)
    }

    suspend fun exportAll(callback: (String, Int, Int) -> Unit): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var totalRecords = 0
            @Suppress("UNCHECKED_CAST")
            val recordTypes = listOf(
                StepsRecord::class as KClass<out androidx.health.connect.client.records.Record> to "steps" to "count",
                ActiveCaloriesBurnedRecord::class to "activeCaloriesBurned" to "energy_kcal",
                HeartRateRecord::class to "heartRate" to "samples",
                RestingHeartRateRecord::class to "restingHeartRate" to "value",
                SleepSessionRecord::class to "sleepSession" to "stages",
                HydrationRecord::class to "hydration" to "volume_ml",
                NutritionRecord::class to "nutrition" to "calories",
                WeightRecord::class to "weight" to "kg",
                HeightRecord::class to "height" to "m",
                BloodGlucoseRecord::class to "bloodGlucose" to "level",
                BloodPressureRecord::class to "bloodPressure" to "bp",
                BasalBodyTemperatureRecord::class to "basalBodyTemperature" to "celsius",
                BodyTemperatureRecord::class to "bodyTemperature" to "celsius",
                OxygenSaturationRecord::class to "oxygenSaturation" to "percent",
                Vo2MaxRecord::class to "vo2Max" to "vo2max",
                SpeedRecord::class to "speed" to "mps",
                RespiratoryRateRecord::class to "respiratoryRate" to "rate",
                BodyFatRecord::class to "bodyFat" to "percent"
            )

            val epochStart = Instant.EPOCH

            for (entry in recordTypes.withIndex()) {
                val recordType = entry.value.first
                val fileName = entry.value.second
                var pageToken: String? = null
                var recordCount = 0
                val file = getExportFile(fileName)

                do {
                    val request = ReadRecordsRequest(
                        recordType = recordType,
                        timeRangeFilter = TimeRangeFilter.after(epochStart),
                        pageSize = 10000,
                        pageToken = pageToken
                    )

                    val response = healthConnectClient.readRecords(request)
                    val records = response.records

                    for (record in records) {
                        file.appendText(recordToCsvLine(record as androidx.health.connect.client.records.Record))
                        recordCount++
                    }

                    pageToken = response.pageToken
                } while (pageToken != null)

                if (recordCount > 0) {
                    callback(fileName, recordCount, entry.index + 1)
                    totalRecords += recordCount
                }
            }

            Result.success(totalRecords)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getExportFile(name: String): File {
        val exportDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "HealthConnectExport"
        )
        exportDir.mkdirs()
        return File(exportDir, "${name}.csv").also { if (it.exists()) it.delete() }
    }

    private fun formatTime(time: Instant): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC)
            .format(time)
    }

    @Suppress("UNCHECKED_CAST")
    private fun recordToCsvLine(record: androidx.health.connect.client.records.Record): String {
        return try {
            when (record) {
                is StepsRecord -> listOf(
                    formatTime(record.startTime),
                    formatTime(record.endTime),
                    record.count.toString(),
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is ActiveCaloriesBurnedRecord -> listOf(
                    formatTime(record.startTime),
                    formatTime(record.endTime),
                    record.energy?.inKilocalories?.toString() ?: "0",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is HeartRateRecord -> listOf(
                    formatTime(record.startTime),
                    formatTime(record.endTime),
                    record.samples.joinToString(";") { "${it.time}:${it.beatsPerMinute}" },
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is RestingHeartRateRecord -> listOf(
                    formatTime(record.startTime),
                    formatTime(record.endTime),
                    record.measuredValue.toString(),
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is SleepSessionRecord -> listOf(
                    formatTime(record.startTime),
                    formatTime(record.endTime),
                    record.title ?: "Sleep",
                    record.stages.joinToString(";") { "${it.stage.name}=${it.startTime}" },
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is HydrationRecord -> listOf(
                    formatTime(record.startTime),
                    formatTime(record.endTime),
                    record.volume?.inMilliliters?.toString() ?: "0",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is NutritionRecord -> listOf(
                    formatTime(record.startTime),
                    formatTime(record.endTime),
                    record.energy?.inKilocalories?.toString() ?: "0",
                    record.protein?.inGrams?.toString() ?: "0",
                    record.carbohydrates?.inGrams?.toString() ?: "0",
                    record.fat?.inGrams?.toString() ?: "0",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is WeightRecord -> listOf(
                    formatTime(record.time),
                    record.weight?.inKilograms?.toString() ?: "0",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is HeightRecord -> listOf(
                    formatTime(record.time),
                    record.height?.inMeters?.toString() ?: "0",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is BloodGlucoseRecord -> listOf(
                    formatTime(record.time),
                    record.level?.inMillimolesPerLiter?.toString() ?: "0",
                    record.specimenSource?.name ?: "",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is BloodPressureRecord -> listOf(
                    formatTime(record.startTime),
                    formatTime(record.endTime),
                    record.diastolic?.inMillimetersOfMercury?.toString() ?: "0",
                    record.systolic?.inMillimetersOfMercury?.toString() ?: "0",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is BasalBodyTemperatureRecord -> listOf(
                    formatTime(record.time),
                    record.measurement?.inCelsius?.toString() ?: "0",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is BodyTemperatureRecord -> listOf(
                    formatTime(record.time),
                    record.measurement?.inCelsius?.toString() ?: "0",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is OxygenSaturationRecord -> listOf(
                    formatTime(record.startTime),
                    formatTime(record.endTime),
                    record.percentage?.value?.times(100)?.toString() ?: "0",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is Vo2MaxRecord -> listOf(
                    formatTime(record.startTime),
                    formatTime(record.endTime),
                    record.rating?.name ?: "",
                    record.measurement?.inMillilitersPerKilogramPerMinute?.toString() ?: "0",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is SpeedRecord -> listOf(
                    formatTime(record.startTime),
                    formatTime(record.endTime),
                    record.samples.joinToString(";") { "${it.time}:${it.metersPerSecond}" },
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is RespiratoryRateRecord -> listOf(
                    formatTime(record.startTime),
                    formatTime(record.endTime),
                    record.measurement?.rate?.toString() ?: "0",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                is BodyFatRecord -> listOf(
                    formatTime(record.time),
                    record.percentage?.value?.times(100)?.toString() ?: "0",
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")

                else -> listOf(
                    formatTime(record.startTime),
                    record.javaClass.simpleName,
                    record.metadata.dataOrigin.packageName
                ).joinToString(",")
            } + "\n"
        } catch (e: Exception) {
            "${formatTime(record.startTime)},ERROR:${e.message}\n"
        }
    }
}
