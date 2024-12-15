package com.example.pruebasql

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.pruebasql.data.entities.ScanData
import com.google.android.material.textfield.TextInputEditText

class ScanResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_scan_result)
        val scanResultTextView: TextView = findViewById(R.id.scanResultTextView)
        val submitButton: Button = findViewById(R.id.submitButton)
        val loaderButton: ProgressBar = findViewById(R.id.buttonLoader)
        val inputText: TextInputEditText =  findViewById(R.id.inputText)

    // Obtener el código escaneado del intent
        val scanResult = intent.getStringExtra("SCAN_RESULT")?: ""
        scanResultTextView.text = scanResult


        submitButton.setOnClickListener {

            val inputValue = inputText.text.toString()

            if(inputValue.isNotEmpty()){
                submitButton.isEnabled = false
                submitButton.visibility = Button.GONE
                loaderButton.visibility = ProgressBar.VISIBLE

                Handler(Looper.getMainLooper()).postDelayed({
                    submitButton.isEnabled = true
                    submitButton.visibility = Button.VISIBLE
                    loaderButton.visibility = ProgressBar.GONE

                    val scanData = ScanData(scanResult, inputValue)

                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("CODE", scanData)
                    }
                    startActivity(intent)
                    finish()
                }, 3000)
            }else{
                submitButton.isEnabled = true
                submitButton.visibility = Button.VISIBLE
                loaderButton.visibility = ProgressBar.GONE

                Toast.makeText(this, "Ingrese una medicion", Toast.LENGTH_SHORT).show()
            }

        }
    }
}