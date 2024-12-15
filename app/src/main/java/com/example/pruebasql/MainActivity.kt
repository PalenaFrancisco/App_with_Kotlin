package com.example.pruebasql

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.pruebasql.R
import com.example.pruebasql.data.entities.ScanData
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

inline fun <reified T : Parcelable> Intent.getParcelableCompat(key: String): T? {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            getParcelableExtra(key, T::class.java)
        else ->
            @Suppress("DEPRECATION")
            getParcelableExtra(key) as? T
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var btnOpenCamera: Button
    private lateinit var btnCloseCamera: Button
    private lateinit var viewFinder: androidx.camera.view.PreviewView
    private lateinit var cameraOverlay: View
    private lateinit var scanGuideOverlay: View
    private lateinit var cameraControlsLayout: LinearLayout
    private lateinit var flashToggle: Button
    private var lastScannedBarcode: String? = null
    private var lastScanTime: Long = 0
    private val SCAN_INTERVAL = 2000L
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var isFlashOn = false

    // Configurar opciones de escaneo
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13,Barcode.FORMAT_EAN_8,)
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    // Executor para análisis de imágenes
    private lateinit var cameraExecutor: ExecutorService

    // Registro de permisos de cámara
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startBarcodeScanner()
        } else {
            Toast.makeText(
                this,
                "Permisos de cámara denegados",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.rootLayout)
        btnOpenCamera = findViewById(R.id.btnOpenCamera)
        btnCloseCamera = findViewById(R.id.btnCloseCamera)
        viewFinder = findViewById(R.id.viewFinder)
        cameraOverlay = findViewById(R.id.cameraOverlay)
        scanGuideOverlay = findViewById(R.id.scanGuideOverlay)
        cameraControlsLayout = findViewById(R.id.cameraControlsLayout)
        flashToggle = findViewById(R.id.btnFlashlight)

        cameraExecutor = Executors.newSingleThreadExecutor()

        initializeFlashlight()

        // Configurar botón para abrir cámara
        btnOpenCamera.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        flashToggle.setOnClickListener {
            toggleFlashlight()
        }

        // Configurar botón para cerrar cámara
        btnCloseCamera.setOnClickListener {
            closeCamera()
        }

        val scanResult = intent.getParcelableCompat<ScanData>("CODE")
        if (scanResult != null){
            Toast.makeText(this, "${scanResult.code}, ${scanResult.inputValue}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeFlashlight() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    private fun toggleFlashlight() {
        if (cameraId == null) {
            Toast.makeText(this, "Linterna no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            isFlashOn = !isFlashOn
            cameraManager.setTorchMode(cameraId!!, isFlashOn)
            flashToggle.text = if (isFlashOn) "Apagar Linterna" else "Encender Linterna"
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                this,
                "Error al cambiar el estado de la linterna",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startBarcodeScanner() {
        btnOpenCamera.visibility = View.GONE
        viewFinder.visibility = View.VISIBLE
        cameraOverlay.visibility = View.VISIBLE
        scanGuideOverlay.visibility = View.VISIBLE
        cameraControlsLayout.visibility = View.VISIBLE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeScannerAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@MainActivity,
                        "Error al iniciar cámara: ${exc.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                    closeCamera()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    inner class BarcodeScannerAnalyzer : ImageAnalysis.Analyzer {
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    CoroutineScope(Dispatchers.Main).launch {
                        processBarcodes(barcodes)
                    }
                }
                .addOnFailureListener { exc ->
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            this@MainActivity,
                            "Error de escaneo: ${exc.localizedMessage}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun processBarcodes(barcodes: List<Barcode>) {
        if (barcodes.isNotEmpty()) {
            val barcode = barcodes[0]
            val scanResult = barcode.displayValue ?: return
            val currentTime = System.currentTimeMillis()

            if (scanResult != lastScannedBarcode || currentTime - lastScanTime > SCAN_INTERVAL) {
                val intent = Intent(this, ScanResultActivity::class.java).apply {
                    putExtra("SCAN_RESULT", scanResult)
                }
                startActivity(intent)
                finish()

                lastScannedBarcode = scanResult
                lastScanTime = currentTime
            }
        }
    }

    private fun closeCamera() {
        val cameraProvider = ProcessCameraProvider.getInstance(this).get()
        cameraProvider.unbindAll()
        btnOpenCamera.visibility = View.VISIBLE
        viewFinder.visibility = View.GONE
        cameraOverlay.visibility = View.GONE
        scanGuideOverlay.visibility = View.GONE
        cameraControlsLayout.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}