package com.netcatgui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var etIpAddress: EditText
    private lateinit var etPort: EditText
    private lateinit var etPayloadPath: EditText
    private lateinit var btnBrowse: Button
    private lateinit var btnInject: Button
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private var selectedFileUri: Uri? = null
    private val PICK_FILE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etIpAddress   = findViewById(R.id.etIpAddress)
        etPort        = findViewById(R.id.etPort)
        etPayloadPath = findViewById(R.id.etPayloadPath)
        btnBrowse     = findViewById(R.id.btnBrowse)
        btnInject     = findViewById(R.id.btnInject)
        tvStatus      = findViewById(R.id.tvStatus)
        progressBar   = findViewById(R.id.progressBar)

        btnBrowse.setOnClickListener { openFilePicker() }
        btnInject.setOnClickListener { injectPayload() }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Seleccionar payload"), PICK_FILE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedFileUri = uri
                val fileName = getFileName(uri)
                etPayloadPath.setText(fileName)
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "archivo_seleccionado"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) {
                name = cursor.getString(idx)
            }
        }
        return name
    }

    private fun injectPayload() {
        val ip   = etIpAddress.text.toString().trim()
        val port = etPort.text.toString().trim().toIntOrNull()
        val uri  = selectedFileUri

        if (ip.isEmpty()) {
            showStatus("⚠ Ingresa una dirección IP", StatusType.ERROR); return
        }
        if (port == null || port !in 1..65535) {
            showStatus("⚠ Puerto inválido (1–65535)", StatusType.ERROR); return
        }
        if (uri == null) {
            showStatus("⚠ Selecciona un archivo payload", StatusType.ERROR); return
        }

        val bytes = try {
            contentResolver.openInputStream(uri)?.readBytes()
        } catch (e: Exception) {
            showStatus("✗ Error leyendo archivo: ${e.message}", StatusType.ERROR)
            return
        }

        if (bytes == null || bytes.isEmpty()) {
            showStatus("✗ El archivo está vacío", StatusType.ERROR); return
        }

        sendPayload(ip, port, bytes)
    }

    private fun sendPayload(ip: String, port: Int, data: ByteArray) {
        setUiEnabled(false)
        showStatus("Conectando a $ip:$port…", StatusType.PROGRESS)
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            val result = PayloadSender.send(ip, port, data)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                setUiEnabled(true)
                if (result.success) {
                    showStatus("✓ Payload enviado (${data.size} bytes)", StatusType.SUCCESS)
                } else {
                    showStatus("✗ Error: ${result.message}", StatusType.ERROR)
                }
            }
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        btnInject.isEnabled = enabled
        btnBrowse.isEnabled = enabled
        etIpAddress.isEnabled = enabled
        etPort.isEnabled = enabled
    }

    private fun showStatus(msg: String, type: StatusType) {
        tvStatus.text = msg
        tvStatus.setTextColor(
            ContextCompat.getColor(this, when (type) {
                StatusType.SUCCESS  -> R.color.statusSuccess
                StatusType.ERROR    -> R.color.statusError
                StatusType.PROGRESS -> R.color.statusProgress
            })
        )
    }

    enum class StatusType { SUCCESS, ERROR, PROGRESS }
}
