package com.techlion.healthconnectexporter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var healthConnectClient: HealthConnectClient
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var exportButton: Button

    private val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(TotalStepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(SleepStageRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(PowerRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(CyclingWheelRevolutionRecord::class),
        HealthPermission.getReadPermission(CyclingWheelRpmRecord::class),
        HealthPermission.getReadPermission(MenstruationFlowRecord::class),
        HealthPermission.getReadPermission(OvulationTestRecord::class),
        HealthPermission.getReadPermission(CervicalMucusRecord::class),
        HealthPermission.getReadPermission(BasalBodyTemperatureRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class)
    )

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(requiredPermissions)) {
            runExport()
        } else {
            statusText.text = "Permissions denied. Cannot export."
            exportButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        healthConnectClient = HealthConnectClient.getOrCreate(this)

        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        exportButton = findViewById(R.id.exportButton)

        checkAvailability()
    }

    private fun checkAvailability() {
        val availability = HealthConnectClient.getSdkStatus(this)
        when (availability) {
            HealthConnectClient.SDK_AVAILABLE -> {
                statusText.text = "Health Connect ready. Tap Export to pull all data."
                exportButton.isEnabled = true
                exportButton.setOnClickListener { requestPermissions() }
            }
            HealthConnectClient.SDK_UNAVAILABLE -> {
                statusText.text = "Health Connect is not installed. Please install it from the Play Store."
                exportButton.isEnabled = false
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                statusText.text = "Health Connect needs an update. Please update it from the Play Store."
                exportButton.isEnabled = false
            }
        }
    }

    private fun requestPermissions() {
        val granted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }.toSet()

        if (granted.containsAll(requiredPermissions)) {
            runExport()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun runExport() {
        exportButton.isEnabled = false
        statusText.text = "Starting export..."

        val workRequest = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(workDataOf(
                ExportWorker.KEY_START_TIME to "2000-01-01T00:00:00Z",
                ExportWorker.KEY_END_TIME to Instant.now().toString()
            ))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniqueWork(
                "health_export",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

        WorkManager.getInstance(this)
            .getWorkInfoByIdLiveData(workRequest.id)
            .observe(this) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt(ExportWorker.KEY_PROGRESS, 0)
                        progressBar.progress = progress
                        statusText.text = "Exporting... $progress%"
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        progressBar.progress = 100
                        val output = workInfo.outputData.getString(ExportWorker.KEY_OUTPUT_FILE)
                        statusText.text = "Done! Saved to: $output"
                        exportButton.isEnabled = true
                        Toast.makeText(this, "Export complete!", Toast.LENGTH_LONG).show()
                    }
                    WorkInfo.State.FAILED -> {
                        val error = workInfo.outputData.getString(ExportWorker.KEY_ERROR)
                        statusText.text = "Failed: $error"
                        exportButton.isEnabled = true
                    }
                    else -> {}
                }
            }
    }
}