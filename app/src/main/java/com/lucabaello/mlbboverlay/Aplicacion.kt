package com.lucabaello.mlbboverlay

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda el último crash en un archivo para poder diagnosticar sin conectar el
 * teléfono. La app lo muestra en la pantalla principal.
 */
class Aplicacion : Application() {

    override fun onCreate() {
        super.onCreate()
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            runCatching { guardar(error) }
            anterior?.uncaughtException(hilo, error)
        }
    }

    private fun guardar(error: Throwable) {
        val texto = StringWriter()
        error.printStackTrace(PrintWriter(texto))
        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val archivo = File(filesDir, "ultimo-error.txt")
        archivo.writeText(
            "Fecha: $fecha\n" +
                "Android: ${Build.VERSION.SDK_INT} (${Build.MANUFACTURER} ${Build.MODEL})\n" +
                "App: ${BuildConfig.VERSION_NAME}\n\n" +
                texto.toString()
        )
    }

    companion object {
        fun ultimoError(contexto: android.content.Context): String? {
            val archivo = File(contexto.filesDir, "ultimo-error.txt")
            if (!archivo.exists()) return null
            return runCatching { archivo.readText() }.getOrNull()
        }

        fun limpiarError(contexto: android.content.Context) {
            runCatching { File(contexto.filesDir, "ultimo-error.txt").delete() }
        }
    }
}
