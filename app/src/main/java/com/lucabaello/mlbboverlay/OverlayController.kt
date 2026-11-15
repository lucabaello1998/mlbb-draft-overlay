package com.lucabaello.mlbboverlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/**
 * Burbuja flotante + panel de resultados.
 *
 * La burbuja es lo único que queda en pantalla cuando la app está inactiva: no
 * captura nada ni corre nada hasta que la tocás. Eso es lo que mantiene el
 * consumo en cero mientras jugás.
 */
class OverlayController(private val contexto: Context, private val alTocar: () -> Unit) {

    private val manager = contexto.getSystemService(WindowManager::class.java)
    private var burbuja: TextView? = null
    private var panel: LinearLayout? = null
    private var contenido: TextView? = null
    private var paramsBurbuja: WindowManager.LayoutParams? = null

    @SuppressLint("ClickableViewAccessibility")
    fun mostrar() {
        if (burbuja != null) return

        val vista = TextView(contexto).apply {
            text = "ML"
            setTextColor(Color.parseColor("#14161d"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = contexto.getDrawable(R.drawable.fondo_burbuja)
        }

        val params = WindowManager.LayoutParams(
            dp(54),
            dp(54),
            tipoVentana(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(10)
            y = dp(240)
        }

        var inicioX = 0
        var inicioY = 0
        var downX = 0f
        var downY = 0f
        var arrastrado = false

        vista.setOnTouchListener { _, evento ->
            when (evento.action) {
                MotionEvent.ACTION_DOWN -> {
                    inicioX = params.x
                    inicioY = params.y
                    downX = evento.rawX
                    downY = evento.rawY
                    arrastrado = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (evento.rawX - downX).toInt()
                    val dy = (evento.rawY - downY).toInt()
                    if (abs(dx) > dp(8) || abs(dy) > dp(8)) arrastrado = true
                    params.x = inicioX + dx
                    params.y = inicioY + dy
                    manager.updateViewLayout(vista, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!arrastrado) alTocar()
                    true
                }

                else -> false
            }
        }

        manager.addView(vista, params)
        burbuja = vista
        paramsBurbuja = params
    }

    fun mostrarCargando() = mostrarPanel("Leyendo la pantalla…")

    fun mostrarPanel(texto: String) {
        val caja = panel ?: crearPanel()
        contenido?.text = texto
        caja.visibility = View.VISIBLE
    }

    private fun crearPanel(): LinearLayout {
        val cabecera = LinearLayout(contexto).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(8), dp(4))
        }

        val titulo = TextView(contexto).apply {
            text = "DRAFT"
            setTextColor(Color.parseColor("#d4af37"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }

        val cerrar = TextView(contexto).apply {
            text = "×"
            setTextColor(Color.parseColor("#8b95a7"))
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(6), 0)
            setOnClickListener { ocultarPanel() }
        }

        cabecera.addView(titulo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        cabecera.addView(cerrar)

        val texto = TextView(contexto).apply {
            setTextColor(Color.parseColor("#e7ecf3"))
            textSize = 12.5f
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(14), dp(2), dp(14), dp(14))
        }

        val scroll = ScrollView(contexto).apply { addView(texto) }

        val caja = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            background = contexto.getDrawable(R.drawable.fondo_panel)
            addView(cabecera)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360)))
        }

        val params = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            tipoVentana(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val base = paramsBurbuja
            x = ((base?.x ?: dp(10)) + dp(64)).coerceAtLeast(0)
            y = ((base?.y ?: dp(240)) - dp(40)).coerceAtLeast(0)
        }

        manager.addView(caja, params)
        panel = caja
        contenido = texto
        return caja
    }

    fun ocultarPanel() {
        panel?.let { runCatching { manager.removeView(it) } }
        panel = null
        contenido = null
    }

    fun cerrar() {
        ocultarPanel()
        burbuja?.let { runCatching { manager.removeView(it) } }
        burbuja = null
        paramsBurbuja = null
    }

    private fun tipoVentana(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(valor: Int): Int =
        (valor * contexto.resources.displayMetrics.density).toInt()
}
