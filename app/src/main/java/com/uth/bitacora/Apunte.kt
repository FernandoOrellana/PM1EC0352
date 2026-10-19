package com.uth.bitacora

import java.io.File

data class Apunte(
    var id: Int = 0,
    var asignatura: String,
    var tema: String,
    var fecha: String,
    var descripcion: String,
    var audio: String
) {
    override fun toString(): String {
        val tieneAudio = audio.isNotEmpty() && File(audio).exists()
        val etiquetaAudio = if (tieneAudio) "🔊 Con nota de voz" else "🔇 Sin nota de voz"
        return "[$id] $asignatura - $tema ($fecha)\n$descripcion\n$etiquetaAudio"
    }
}
