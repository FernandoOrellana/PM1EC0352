package com.uth.bitacora

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Chronometer
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerAsignatura: Spinner
    private lateinit var etTema: EditText
    private lateinit var etFecha: EditText
    private lateinit var etDescripcion: EditText
    private lateinit var cronometro: Chronometer
    private lateinit var btnGrabar: Button
    private lateinit var btnReproducir: Button
    private lateinit var btnGuardar: Button
    private lateinit var btnVerApuntes: Button
    private lateinit var btnDictar: Button

    private lateinit var dbHelper: DatabaseHelper

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var audioFilePath: String = ""

    // RequestPermission launcher for RECORD_AUDIO
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Permiso de audio concedido", Toast.LENGTH_SHORT).show()
        } else {
            mostrarExplicacionPermiso()
        }
    }

    // Speech-to-Text launcher
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
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dbHelper = DatabaseHelper(this)

        spinnerAsignatura = findViewById(R.id.spinner)
        etTema = findViewById(R.id.TextfieldTema)
        etFecha = findViewById(R.id.TextfieldFecha)
        etDescripcion = findViewById(R.id.TextAreaDescripcion)
        cronometro = findViewById(R.id.cronometro)
        btnGrabar = findViewById(R.id.btnGrabar)
        btnReproducir = findViewById(R.id.btnReproducir)
        btnGuardar = findViewById(R.id.btnGuardar)
        btnVerApuntes = findViewById(R.id.btnVerApuntes)
        btnDictar = findViewById(R.id.btnDictarDescripcion)

        // Configurar selector de fecha Modal (DatePickerDialog)
        etFecha.isFocusable = false
        etFecha.isClickable = true
        etFecha.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val dpd = DatePickerDialog(
                this,
                { _, y, m, d ->
                    val fechaFormateada = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                    etFecha.setText(fechaFormateada)
                },
                year, month, day
            )
            dpd.show()
        }

        // Acción Grabar Audio
        btnGrabar.setOnClickListener {
            if (checkAudioPermission()) {
                if (!isRecording) {
                    iniciarGrabacion()
                } else {
                    detenerGrabacion()
                }
            } else {
                solicitarPermisoAudio()
            }
        }

        // Acción Reproducir Audio de prueba
        btnReproducir.setOnClickListener {
            reproducirAudio()
        }

        // Acción Dictar Voz a Texto
        btnDictar.setOnClickListener {
            if (checkAudioPermission()) {
                iniciarDictadoVoz()
            } else {
                solicitarPermisoAudio()
            }
        }

        // Acción Guardar en SQLite
        btnGuardar.setOnClickListener {
            guardarApunte()
        }

        // Acción Ver Apuntes
        btnVerApuntes.setOnClickListener {
            val intent = Intent(this, ApuntesActivity::class.java)
            startActivity(intent)
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
            .setMessage("Esta aplicación necesita acceso al micrófono para grabar notas de voz y realizar dictados de texto.")
            .setPositiveButton("Solicitar de nuevo") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun iniciarGrabacion() {
        val file = File(externalCacheDir, "apunte_audio_${System.currentTimeMillis()}.3gp")
        audioFilePath = file.absolutePath

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder = recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFilePath)
            try {
                prepare()
                start()
                isRecording = true
                btnGrabar.text = "Detener"
                cronometro.base = SystemClock.elapsedRealtime()
                cronometro.start()
                Toast.makeText(this@MainActivity, "Grabando audio...", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Error al iniciar grabación: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun detenerGrabacion() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            btnGrabar.text = "Grabar"
            cronometro.stop()
            Toast.makeText(this, "Grabación guardada temporalmente", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al detener grabación", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reproducirAudio() {
        if (audioFilePath.isEmpty() || !File(audioFilePath).exists()) {
            mostrarAlerta("Atención", "No hay ninguna grabación disponible para reproducir.")
            return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFilePath)
                prepare()
                start()
            }
            Toast.makeText(this, "Reproduciendo audio...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            mostrarAlerta("Error", "No se pudo reproducir el audio: ${e.message}")
        }
    }

    private fun iniciarDictadoVoz() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicte la descripción...")
        }
        try {
            speechToTextLauncher.launch(intent)
        } catch (e: Exception) {
            mostrarAlerta("Error", "El dictado de voz no está disponible en este dispositivo.")
        }
    }

    private fun guardarApunte() {
        val asignatura = spinnerAsignatura.selectedItem?.toString() ?: ""
        val tema = etTema.text.toString().trim()
        val fecha = etFecha.text.toString().trim()
        val descripcion = etDescripcion.text.toString().trim()

        // 1. Mostrar las alertas respectivas si un campo obligatorio está vacío
        if (asignatura == "Seleccione un tema" || asignatura.isEmpty()) {
            mostrarAlerta("Campo Obligatorio", "Por favor seleccione una asignatura válida.")
            return
        }
        if (tema.isEmpty()) {
            mostrarAlerta("Campo Obligatorio", "El campo Tema no puede estar vacío.")
            return
        }
        if (fecha.isEmpty()) {
            mostrarAlerta("Campo Obligatorio", "El campo Fecha no puede estar vacío.")
            return
        }
        if (descripcion.isEmpty()) {
            mostrarAlerta("Campo Obligatorio", "El campo Descripción no puede estar vacío.")
            return
        }

        // Determinar si hay audio grabado o no
        val rutaAudioFinal = if (audioFilePath.isNotEmpty() && File(audioFilePath).exists()) audioFilePath else ""

        // 2. Validar con expresiones regulares:
        // Fecha AAAA-MM-DD
        val regexFecha = Regex("""^\d{4}-\d{2}-\d{2}$""")
        if (!regexFecha.matches(fecha)) {
            mostrarAlerta("Formato de Fecha Inválido", "La fecha debe tener el formato AAAA-MM-DD (ejemplo: 2025-03-30).")
            return
        }

        // Tema sin símbolos (solo letras, números y espacios)
        val regexTema = Regex("""^[a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\s]+$""")
        if (!regexTema.matches(tema)) {
            mostrarAlerta("Tema Inválido", "El tema no debe contener símbolos especiales (solo letras y números).")
            return
        }

        // Crear objeto e insertar en la base de datos SQLite
        val nuevoApunte = Apunte(
            asignatura = asignatura,
            tema = tema,
            fecha = fecha,
            descripcion = descripcion,
            audio = rutaAudioFinal
        )

        val id = dbHelper.insertApunte(nuevoApunte)
        if (id > 0) {
            Toast.makeText(this, "¡Apunte guardado con éxito!", Toast.LENGTH_LONG).show()
            limpiarCampos()
        } else {
            mostrarAlerta("Error", "Ocurrió un error al guardar el apunte en la base de datos.")
        }
    }

    private fun limpiarCampos() {
        spinnerAsignatura.setSelection(0)
        etTema.setText("")
        etFecha.setText("")
        etDescripcion.setText("")
        audioFilePath = ""
        cronometro.base = SystemClock.elapsedRealtime()
    }

    private fun mostrarAlerta(titulo: String, mensaje: String) {
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaPlayer?.release()
    }
}
