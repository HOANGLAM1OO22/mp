package com.example.mputils

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private val TAG = this::class.java.simpleName

    private val outputMp4Path = "/storage/emulated/0/Download/output.mp4"
    private var imagePath: String? = null
    private lateinit var manageFileAccessLauncher: ActivityResultLauncher<Intent>
    private lateinit var chooseImageLauncher: ActivityResultLauncher<String>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var ivImage: ImageView

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnImage = findViewById<Button>(R.id.btn_chooseImage)
        ivImage = findViewById(R.id.selectedImageView)

        initializeLaunchers()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VIDEO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            manageFileAccessLauncher.launch(intent)
        } else {

            chooseImageLauncher = registerForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {

                    ivImage.setImageURI(uri)
                    val fileName = getFileNameFromUri(uri)
                    Toast.makeText(this, "Selected File: $fileName", Toast.LENGTH_SHORT).show()
                    val filePath = copyToAppStorage(uri, fileName)
                    Log.i(TAG, "onCreate: filePath = ${filePath}")

                    val inImg = filePath?.let { File(it) }
                    Log.i(TAG, "onCreate: inImg = ${inImg}")
                } else {
                    Toast.makeText(this, "No image selected!", Toast.LENGTH_SHORT).show()
                }
            }

            btnImage.setOnClickListener {
                chooseImageLauncher.launch("image/*")
            }

        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (columnIndex != -1) {
                    fileName = it.getString(columnIndex)
                }
            }
        }
        return fileName
    }

    private fun copyToAppStorage(uri: Uri, fileName: String?): String? {
        val destinationPath = File(filesDir, "${fileName}")
        contentResolver.openInputStream(uri)?.use { inputStream ->
            destinationPath.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return destinationPath.absolutePath
    }
    private fun initializeLaunchers() {

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "Permission granted!")
                // Proceed with your logic
            } else {
                Log.e(TAG, "Permission denied!")
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Ứng dụng cần quyền để truy cập bộ nhớ!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        manageFileAccessLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                Log.d(TAG, "File access granted!")
                // Proceed with your logic
            } else {
                Toast.makeText(
                    this,
                    "Quyền truy cập quản lý tệp bị từ chối!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
