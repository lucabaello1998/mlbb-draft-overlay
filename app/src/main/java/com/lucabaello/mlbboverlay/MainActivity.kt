package com.lucabaello.mlbboverlay

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var estado: TextView
    private lateinit var botonPermiso: Button
    private lateinit var botonIniciar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0b0e14"))
            setPadding(dp(24), dp(56), dp(24), dp(24))
        }

        val titulo = TextView(this).apply {
            text = "MLBB Draft"
            setTextColor(Color.parseColor("#e7ecf3"))
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }

        val bajada = TextView(this).apply {
            text = "Burbuja sobre el juego: la tocás, lee la pantalla de draft y te muestra los counters."
            setTextColor(Color.parseColor("#8b95a7"))
            textSize = 14f
            setPadding(0, dp(10), 0, dp(28))
        }

        estado = TextView(this).apply {
            setTextColor(Color.parseColor("#d4af37"))
            textSize = 13f
            setPadding(0, 0, 0, dp(18))
        }

        botonPermiso = boton("1. Permitir dibujar sobre otras apps") { pedirOverlay() }
        botonIniciar = boton("2. Iniciar captura") { pedirCaptura() }
        val detener = boton("Detener") { detenerServicio() }

        raiz.addView(titulo)
        raiz.addView(bajada)
        raiz.addView(estado)
        raiz.addView(botonPermiso)
        raiz.addView(botonIniciar)
        raiz.addView(detener)
        setContentView(raiz)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2)
        }
    }

    override fun onResume() {
        super.onResume()
        actualizarEstado()
    }

    private fun actualizarEstado() {
        val overlay = Settings.canDrawOverlays(this)
        botonPermiso.isEnabled = !overlay
        botonPermiso.text = if (overlay) "1. Permiso de superposición ✓" else "1. Permitir dibujar sobre otras apps"
        botonIniciar.isEnabled = overlay
        estado.text = if (overlay) {
            "Listo. Tocá Iniciar y aceptá la captura de pantalla."
        } else {
            "Falta el permiso de superposición."
        }
    }

    private fun pedirOverlay() {
        if (Settings.canDrawOverlays(this)) {
            actualizarEstado()
            return
        }
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun pedirCaptura() {
        if (!Settings.canDrawOverlays(this)) {
            estado.text = "Primero dale el permiso de superposición."
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), 1)
    }

    @Deprecated("Activity result sin AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1) return
        if (resultCode != RESULT_OK || data == null) {
            estado.text = "Captura cancelada."
            return
        }

        val servicio = Intent(this, CaptureService::class.java).apply {
            putExtra(CaptureService.EXTRA_CODIGO, resultCode)
            putExtra(CaptureService.EXTRA_DATOS, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(servicio)
        } else {
            startService(servicio)
        }
        estado.text = "Captura activa: buscá la burbuja sobre el juego."
    }

    private fun detenerServicio() {
        startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACCION_DETENER))
        estado.text = "Captura detenida."
    }

    private fun boton(texto: String, accion: () -> Unit): Button =
        Button(this).apply {
            this.text = texto
            textSize = 15f
            setOnClickListener { accion() }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(10)
            layoutParams = params
        }

    private fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density).toInt()
}
