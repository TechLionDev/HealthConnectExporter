package com.techlion.healthconnectexporter

import android.content.Intent
import android.os.Bundle
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        exportButton = findViewById(R.id.exportButton)

        exportButton.setOnClickListener { checkAndRequestPermissions() }
        findViewById<Button>(R.id.shareButton).setOnClickListener { shareExportedFiles() }
        findViewById<Button>(R.id.clearButton).setOnClickListener { clearExports() }

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
        progressBar.visibility = android.view.View.VISIBLE
        exportButton.isEnabled = false

        lifecycleScope.launch {
            val result = exportWorker.exportAll { name, count, _ ->
                runOnUiThread {
                    statusText.text = "Exporting $name: $count records..."
                }
            }

            progressBar.visibility = android.view.View.GONE
            exportButton.isEnabled = true

            result.onSuccess { total ->
                statusText.text = "Exported $total records total.\nTap Share to send the files."
                Snackbar.make(findViewById(R.id.main), "Export complete!", Snackbar.LENGTH_LONG).show()
            }.onFailure { error ->
                statusText.text = "Export failed: ${error.message}"
                Snackbar.make(findViewById(R.id.main), "Error: ${error.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun shareExportedFiles() {
        val exportDir = File(getExternalFilesDir(null), "HealthConnectExport")
        if (!exportDir.exists() || exportDir.listFiles()?.isEmpty() != false) {
            Snackbar.make(findViewById(R.id.main), "No exports found", Snackbar.LENGTH_SHORT).show()
            return
        }

        val files = exportDir.listFiles() ?: return
        val uris = files.map { androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.provider", it) }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Health Connect Export"))
    }

    private fun clearExports() {
        val exportDir = File(getExternalFilesDir(null), "HealthConnectExport")
        exportDir.listFiles()?.forEach { it.delete() }
        updateStatus()
        Snackbar.make(findViewById(R.id.main), "Exports cleared", Snackbar.LENGTH_SHORT).show()
    }

    private fun updateStatus() {
        val exportDir = File(getExternalFilesDir(null), "HealthConnectExport")
        val files = exportDir.listFiles()
        statusText.text = if (files.isNullOrEmpty()) {
            "No exports yet.\nGrant permissions and tap Export."
        } else {
            "${files.size} export files ready:\n${files.joinToString("\n") { it.name }}"
        }
    }
}
