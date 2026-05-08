package com.techlion.healthconnectexporter

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import androidx.health.connect.client.HealthConnectClient

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var exportButton: Button

    private val exportWorker = ExportWorker(this)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            export()
        } else {
            statusText.text = "Permissions required to export data."
            exportButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(android.R.id.content)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        exportButton = findViewById(R.id.exportButton)

        exportButton.setOnClickListener { checkAndRequestPermissions() }

        updateStatus()
    }

    private fun checkAndRequestPermissions() {
        lifecycleScope.launch {
            if (exportWorker.hasAllPermissions()) {
                export()
            } else {
                val perms = exportWorker.permissions.toList()
                permissionLauncher.launch(perms.toTypedArray())
            }
        }
    }

    private fun export() {
        statusText.text = "Exporting..."
        progressBar.visibility = View.VISIBLE
        exportButton.isEnabled = false

        lifecycleScope.launch {
            val result = exportWorker.exportAll { name, count, _ ->
                runOnUiThread {
                    statusText.text = "Exporting $name: $count records..."
                }
            }

            progressBar.visibility = View.GONE
            exportButton.isEnabled = true

            result.onSuccess { total ->
                statusText.text = "Exported $total records total."
                shareExports()
            }.onFailure { error ->
                statusText.text = "Export failed: ${error.message}"
            }
        }
    }

    private fun shareExports() {
        val exportDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "HealthConnectExport")
        val files = exportDir.listFiles() ?: return
        if (files.isEmpty()) {
            Snackbar.make(findViewById<View>(android.R.id.content), "No exports found", Snackbar.LENGTH_SHORT).show()
            return
        }
        val uris = files.map {
            androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Health Connect Export"))
    }

    private fun updateStatus() {
        val exportDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "HealthConnectExport")
        val files = exportDir.listFiles()
        statusText.text = if (files.isNullOrEmpty()) {
            "Grant permissions and tap Export."
        } else {
            "${files.size} export files ready."
        }
    }
}
