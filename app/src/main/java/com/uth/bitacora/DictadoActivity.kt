package com.uth.bitacora

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class DictadoActivity : AppCompatActivity() {

    private lateinit var btnAtras: Button
    private lateinit var tvInfoApunte: TextView
    private lateinit var btnIniciarDictado: Button
    private lateinit var etDescripcion: EditText
    private lateinit var btnGuardarDictado: Button

    private lateinit var dbHelper: DatabaseHelper
    private var apunteActual: Apunte? = null

    // Permiso de micrófono
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            iniciarDictadoVoz()
        } else {
            mostrarExplicacionPermiso()
        }
    }

    // Speech To Text Launcher
    private val speechToTextLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val textoDictado = matches[0]
                val textoActual = etDescripcion.text.toString()
                if (textoActual.isEmpty()) {
                    etDescripcion.setText(textoDictado)
                } else {
                    etDescripcion.setText("$textoActual $textoDictado")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictado)

        dbHelper = DatabaseHelper(this)

        btnAtras = findViewById(R.id.btnAtrasDictado)
        tvInfoApunte = findViewById(R.id.tvInfoApunte)
        btnIniciarDictado = findViewById(R.id.btnIniciarDictadoVoz)
        etDescripcion = findViewById(R.id.etDictadoDescripcion)
        btnGuardarDictado = findViewById(R.id.btnGuardarDictado)

        btnAtras.setOnClickListener {
            finish()
        }

        val apunteId = intent.getIntExtra("APUNTE_ID", -1)
        if (apunteId != -1) {
            apunteActual = dbHelper.getApunteById(apunteId)
        }

        val apunte = apunteActual
        if (apunte == null) {
            Toast.makeText(this, "Error al cargar el apunte seleccionado.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvInfoApunte.text = "Asignatura: ${apunte.asignatura}\nTema: ${apunte.tema}"
        etDescripcion.setText(apunte.descripcion)

        // Botón para dictar por voz
        btnIniciarDictado.setOnClickListener {
            if (checkAudioPermission()) {
                iniciarDictadoVoz()
            } else {
                solicitarPermisoAudio()
            }
        }

        // Botón para guardar únicamente la descripción
        btnGuardarDictado.setOnClickListener {
            val nuevaDesc = etDescripcion.text.toString().trim()
            if (nuevaDesc.isEmpty()) {
                mostrarAlerta("Campo Vacío", "La descripción no puede estar vacía.")
                return@setOnClickListener
            }

            apunte.descripcion = nuevaDesc
            val rows = dbHelper.updateApunte(apunte)
            if (rows > 0) {
                Toast.makeText(this, "Descripción actualizada correctamente", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                mostrarAlerta("Error", "No se pudo actualizar la descripción en la base de datos.")
            }
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun solicitarPermisoAudio() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            mostrarExplicacionPermiso()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun mostrarExplicacionPermiso() {
        AlertDialog.Builder(this)
            .setTitle("Permiso de Micrófono Requerido")
            .setMessage("Se requiere el permiso de micrófono para cambiar la descripción mediante dictado de voz.")
            .setPositiveButton("Aceptar") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun iniciarDictadoVoz() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicte la nueva descripción...")
        }
        try {
            speechToTextLauncher.launch(intent)
        } catch (e: Exception) {
            mostrarAlerta("Error", "El servicio de dictado de voz no está disponible.")
        }
    }

    private fun mostrarAlerta(titulo: String, mensaje: String) {
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton("Aceptar", null)
            .show()
    }
}
