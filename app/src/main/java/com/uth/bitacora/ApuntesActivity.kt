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
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Chronometer
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Calendar
import java.util.Locale

class ApuntesActivity : AppCompatActivity() {

    private lateinit var btnAtras: Button
    private lateinit var spinnerFiltro: Spinner
    private lateinit var listViewApuntes: ListView
    private lateinit var btnReproducirNota: Button
    private lateinit var btnDictadorTexto: Button
    private lateinit var btnEliminarApuntes: Button
    private lateinit var btnActualizarApunte: Button

    private lateinit var dbHelper: DatabaseHelper
    private var listaApuntes: MutableList<Apunte> = mutableListOf()
    private lateinit var adapter: ArrayAdapter<Apunte>
    private var apunteSeleccionado: Apunte? = null

    private var mediaPlayer: MediaPlayer? = null
    private var etDialogDescripcion: EditText? = null

    // Permiso de micrófono para Dictado
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            iniciarDictadoVoz()
        } else {
            mostrarExplicacionPermiso()
        }
    }

    // Launcher para DictadoActivity
    private val dictadoActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val asignatura = spinnerFiltro.selectedItem?.toString() ?: ""
            cargarApuntes(asignatura)
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
                etDialogDescripcion?.let { et ->
                    val actual = et.text.toString()
                    if (actual.isEmpty()) {
                        et.setText(textoDictado)
                    } else {
                        et.setText("$actual $textoDictado")
                    }
                } ?: run {
                    Toast.makeText(this, "Dictado recibido: $textoDictado", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.apuntes_list)

        dbHelper = DatabaseHelper(this)

        btnAtras = findViewById(R.id.btnatras)
        spinnerFiltro = findViewById(R.id.spinner3)
        listViewApuntes = findViewById(R.id.ListApuntes)
        btnReproducirNota = findViewById(R.id.btnReproducirNota)
        btnDictadorTexto = findViewById(R.id.btnDictadorTexto)
        btnEliminarApuntes = findViewById(R.id.btnEliminarApuntes)
        btnActualizarApunte = findViewById(R.id.btnActualizarApunte)

        // Botón Atrás
        btnAtras.setOnClickListener {
            finish()
        }

        // Cargar lista de apuntes
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, listaApuntes)
        listViewApuntes.adapter = adapter
        listViewApuntes.choiceMode = ListView.CHOICE_MODE_SINGLE

        // Selección en ListView
        listViewApuntes.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            apunteSeleccionado = listaApuntes[position]
            Toast.makeText(this, "Seleccionado: ${apunteSeleccionado?.tema}", Toast.LENGTH_SHORT).show()
        }

        // 4. Filtrar por asignatura con consulta parametrizada (?)
        spinnerFiltro.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val asignatura = parent?.getItemAtPosition(position).toString()
                cargarApuntes(asignatura)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 5. Reproducir la nota de voz guardada con MediaPlayer
        btnReproducirNota.setOnClickListener {
            val apunte = apunteSeleccionado
            if (apunte == null) {
                mostrarAlerta("Atención", "Por favor seleccione un apunte de la lista.")
                return@setOnClickListener
            }
            reproducirAudioApunte(apunte.audio)
        }

        // 2. Eliminar el apunte seleccionado
        btnEliminarApuntes.setOnClickListener {
            val apunte = apunteSeleccionado
            if (apunte == null) {
                mostrarAlerta("Atención", "Por favor seleccione un apunte de la lista para eliminar.")
                return@setOnClickListener
            }
            confirmarEliminacion(apunte)
        }

        // 3. Actualizar el apunte seleccionado
        btnActualizarApunte.setOnClickListener {
            val apunte = apunteSeleccionado
            if (apunte == null) {
                mostrarAlerta("Atención", "Por favor seleccione un apunte de la lista para actualizar.")
                return@setOnClickListener
            }
            mostrarDialogoActualizar(apunte)
        }

        // 1. Dictar la descripción del apunte en una pantalla dedicada
        btnDictadorTexto.setOnClickListener {
            val apunte = apunteSeleccionado
            if (apunte == null) {
                mostrarAlerta("Atención", "Por favor seleccione un apunte de la lista para dictar su descripción.")
                return@setOnClickListener
            }
            val intent = Intent(this, DictadoActivity::class.java).apply {
                putExtra("APUNTE_ID", apunte.id)
            }
            dictadoActivityLauncher.launch(intent)
        }
    }

    private fun cargarApuntes(asignatura: String) {
        listaApuntes.clear()
        if (asignatura == "Seleccione un tema" || asignatura.isEmpty()) {
            // Listar desde la base de datos todos los apuntes guardados
            listaApuntes.addAll(dbHelper.getAllApuntes())
        } else {
            // Filtrar los apuntes por asignatura con consulta parametrizada (?)
            listaApuntes.addAll(dbHelper.getApuntesByAsignatura(asignatura))
        }
        apunteSeleccionado = null
        adapter.notifyDataSetChanged()
    }

    private fun reproducirAudioApunte(audioPath: String) {
        if (audioPath.isEmpty() || !File(audioPath).exists()) {
            mostrarAlerta("Sin Nota de Voz", "Este apunte fue guardado sin nota de voz.")
            return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()
                start()
            }
            Toast.makeText(this, "Reproduciendo nota de voz...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            mostrarAlerta("Error", "Error al reproducir nota de voz: ${e.message}")
        }
    }

    private fun confirmarEliminacion(apunte: Apunte) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Está seguro de que desea eliminar el apunte '${apunte.tema}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                val result = dbHelper.deleteApunte(apunte.id)
                if (result > 0) {
                    Toast.makeText(this, "Apunte eliminado", Toast.LENGTH_SHORT).show()
                    val asignatura = spinnerFiltro.selectedItem?.toString() ?: ""
                    cargarApuntes(asignatura)
                } else {
                    mostrarAlerta("Error", "No se pudo eliminar el apunte.")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoActualizar(apunte: Apunte) {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        var nuevoAudioPath = apunte.audio
        var isRecordingDialog = false
        var dialogMediaRecorder: MediaRecorder? = null
        var dialogMediaPlayer: MediaPlayer? = null

        val etTema = EditText(context).apply {
            hint = "Tema"
            setText(apunte.tema)
        }
        val etFecha = EditText(context).apply {
            hint = "Fecha (AAAA-MM-DD)"
            setText(apunte.fecha)
            isFocusable = false
            isClickable = true
            setOnClickListener {
                val calendar = Calendar.getInstance()
                val dpd = DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        val fechaFormateada = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                        setText(fechaFormateada)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                dpd.show()
            }
        }
        val etDesc = EditText(context).apply {
            hint = "Descripción"
            setText(apunte.descripcion)
        }
        etDialogDescripcion = etDesc

        // Sección de Audio en el diálogo
        val tvEstadoAudio = TextView(context).apply {
            val tieneAudio = nuevoAudioPath.isNotEmpty() && File(nuevoAudioPath).exists()
            text = if (tieneAudio) "Audio: 🔊 Con nota de voz" else "Audio: 🔇 Sin nota de voz"
            setPadding(0, 15, 0, 5)
            textSize = 14f
        }

        val cronometroDialog = Chronometer(context).apply {
            textSize = 16f
            setPadding(0, 5, 0, 5)
        }

        val btnGrabarAudioDialog = Button(context).apply {
            text = "🎤 Grabar Audio"
        }

        val btnReproducirAudioDialog = Button(context).apply {
            text = "▶ Reproducir"
        }

        val layoutBotonesAudio = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btnGrabarAudioDialog)
            addView(btnReproducirAudioDialog)
        }

        btnGrabarAudioDialog.setOnClickListener {
            if (!checkAudioPermission()) {
                solicitarPermisoAudio()
                return@setOnClickListener
            }

            if (!isRecordingDialog) {
                val file = File(externalCacheDir, "apunte_audio_${System.currentTimeMillis()}.3gp")
                nuevoAudioPath = file.absolutePath

                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                try {
                    dialogMediaRecorder = recorder.apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                        setOutputFile(nuevoAudioPath)
                        prepare()
                        start()
                    }
                    isRecordingDialog = true
                    btnGrabarAudioDialog.text = "⏹ Detener"
                    cronometroDialog.base = SystemClock.elapsedRealtime()
                    cronometroDialog.start()
                    Toast.makeText(context, "Grabando nuevo audio...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error al iniciar grabación", Toast.LENGTH_SHORT).show()
                }
            } else {
                try {
                    dialogMediaRecorder?.stop()
                    dialogMediaRecorder?.release()
                    dialogMediaRecorder = null
                    isRecordingDialog = false
                    btnGrabarAudioDialog.text = "🎤 Grabar Audio"
                    cronometroDialog.stop()
                    tvEstadoAudio.text = "Audio: 🔊 Nuevo audio grabado"
                    Toast.makeText(context, "Nuevo audio listo para guardar", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        btnReproducirAudioDialog.setOnClickListener {
            if (nuevoAudioPath.isEmpty() || !File(nuevoAudioPath).exists()) {
                Toast.makeText(context, "No hay audio disponible para reproducir", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                dialogMediaPlayer?.release()
                dialogMediaPlayer = MediaPlayer().apply {
                    setDataSource(nuevoAudioPath)
                    prepare()
                    start()
                }
                Toast.makeText(context, "Reproduciendo audio...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        layout.addView(etTema)
        layout.addView(etFecha)
        layout.addView(etDesc)
        layout.addView(tvEstadoAudio)
        layout.addView(cronometroDialog)
        layout.addView(layoutBotonesAudio)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Actualizar Apunte")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoTema = etTema.text.toString().trim()
                val nuevaFecha = etFecha.text.toString().trim()
                val nuevaDesc = etDesc.text.toString().trim()

                if (nuevoTema.isEmpty() || nuevaFecha.isEmpty() || nuevaDesc.isEmpty()) {
                    mostrarAlerta("Error", "Todos los campos son obligatorios.")
                    return@setPositiveButton
                }

                // Validaciones Regex
                val regexFecha = Regex("""^\d{4}-\d{2}-\d{2}$""")
                if (!regexFecha.matches(nuevaFecha)) {
                    mostrarAlerta("Error", "La fecha debe tener formato AAAA-MM-DD.")
                    return@setPositiveButton
                }

                val regexTema = Regex("""^[a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\s]+$""")
                if (!regexTema.matches(nuevoTema)) {
                    mostrarAlerta("Error", "El tema no debe contener símbolos especiales.")
                    return@setPositiveButton
                }

                apunte.tema = nuevoTema
                apunte.fecha = nuevaFecha
                apunte.descripcion = nuevaDesc
                apunte.audio = nuevoAudioPath

                val updatedRows = dbHelper.updateApunte(apunte)
                if (updatedRows > 0) {
                    Toast.makeText(context, "Apunte actualizado correctamente", Toast.LENGTH_SHORT).show()
                    val asignatura = spinnerFiltro.selectedItem?.toString() ?: ""
                    cargarApuntes(asignatura)
                } else {
                    mostrarAlerta("Error", "No se pudo actualizar el apunte en la base de datos.")
                }
            }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener {
                try {
                    dialogMediaRecorder?.release()
                    dialogMediaPlayer?.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .create()

        dialog.show()
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
            .setMessage("Se requiere el permiso de micrófono para la función de dictado de texto.")
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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicte la descripción...")
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

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}
