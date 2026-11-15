package com.lucabaello.mlbboverlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
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
 * Las dos vistas se crean UNA vez y se muestran u ocultan cambiando visibilidad:
 * nunca se agregan ni se quitan del WindowManager después de arrancar. Eso evita
 * que un toque (o un error) se lleve puesta la ventana y parezca que la app se
 * cerró.
 */
class OverlayController(private val contexto: Context, private val alTocar: () -> Unit) {

    private val manager = contexto.getSystemService(WindowManager::class.java)
    private val principal = Handler(Looper.getMainLooper())

    private var burbuja: TextView? = null
    private var panel: LinearLayout? = null
    private var contenido: TextView? = null
    private var estado: TextView? = null

    private var paramsBurbuja: WindowManager.LayoutParams? = null
    private var paramsPanel: WindowManager.LayoutParams? = null

    private var ultimoTexto = ""

    @SuppressLint("ClickableViewAccessibility")
    fun mostrar() {
        if (burbuja != null) return
        try {
            crearBurbuja()
            crearPanel()
        } catch (error: Exception) {
            // Si el sistema no deja dibujar, que no se caiga el servicio.
            android.util.Log.e("mlbb", "No se pudo crear el overlay", error)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun crearBurbuja() {
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
        var downEn = 0L

        vista.setOnTouchListener { _, evento ->
            try {
                when (evento.action) {
                    MotionEvent.ACTION_DOWN -> {
                        inicioX = params.x
                        inicioY = params.y
                        downX = evento.rawX
                        downY = evento.rawY
                        downEn = System.currentTimeMillis()
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
                        val sostener = System.currentTimeMillis() - downEn > 450
                        if (!arrastrado) {
                            if (sostener) alternarPanel() else alTocar()
                        }
                        true
                    }

                    else -> false
                }
            } catch (error: Exception) {
                android.util.Log.e("mlbb", "toque de la burbuja", error)
                false
            }
        }

        manager.addView(vista, params)
        burbuja = vista
        paramsBurbuja = params
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun crearPanel() {
        val cabecera = LinearLayout(contexto).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(4))
        }

        val titulo = TextView(contexto).apply {
            text = "DRAFT"
            setTextColor(Color.parseColor("#d4af37"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }

        val minimizar = TextView(contexto).apply {
            text = "—"
            setTextColor(Color.parseColor("#8b95a7"))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(4), 0)
            // Ocultar el panel no apaga nada: la burbuja sigue y se reabre tocándola.
            setOnClickListener { ocultarPanel() }
        }

        cabecera.addView(titulo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        cabecera.addView(minimizar)

        estado = TextView(contexto).apply {
            setTextColor(Color.parseColor("#8b95a7"))
            textSize = 10.5f
            setPadding(dp(14), 0, dp(14), dp(4))
        }

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
            addView(estado)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(330)))
            visibility = View.GONE
        }

        val params = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            tipoVentana(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(74)
            y = dp(200)
        }

        // Arrastrar por la cabecera para que no tape el draft.
        var inicioX = 0
        var inicioY = 0
        var downX = 0f
        var downY = 0f
        cabecera.setOnTouchListener { _, evento ->
            try {
                when (evento.action) {
                    MotionEvent.ACTION_DOWN -> {
                        inicioX = params.x
                        inicioY = params.y
                        downX = evento.rawX
                        downY = evento.rawY
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        params.x = inicioX + (evento.rawX - downX).toInt()
                        params.y = inicioY + (evento.rawY - downY).toInt()
                        manager.updateViewLayout(caja, params)
                        true
                    }

                    else -> false
                }
            } catch (error: Exception) {
                android.util.Log.e("mlbb", "arrastre del panel", error)
                false
            }
        }

        manager.addView(caja, params)
        panel = caja
        contenido = texto
        paramsPanel = params
    }

    fun mostrarCargando(mensaje: String = "Leyendo la pantalla…") {
        principal.post {
            try {
                estado?.text = mensaje
                panel?.visibility = View.VISIBLE
            } catch (error: Exception) {
                android.util.Log.e("mlbb", "mostrar cargando", error)
            }
        }
    }

    /** Muestra el informe. Si es igual al anterior, sólo actualiza la línea de estado. */
    fun mostrarPanel(texto: String, linea: String = "") {
        principal.post {
            try {
                estado?.text = linea
                if (texto != ultimoTexto) {
                    contenido?.text = texto
                    ultimoTexto = texto
                }
                panel?.visibility = View.VISIBLE
            } catch (error: Exception) {
                android.util.Log.e("mlbb", "mostrar panel", error)
            }
        }
    }

    fun ocultarPanel() {
        principal.post {
            try {
                panel?.visibility = View.GONE
            } catch (error: Exception) {
                android.util.Log.e("mlbb", "ocultar panel", error)
            }
        }
    }

    private fun alternarPanel() {
        val visible = panel?.visibility == View.VISIBLE
        if (visible) ocultarPanel() else principal.post { panel?.visibility = View.VISIBLE }
    }

    fun cerrar() {
        try {
            panel?.let { runCatching { manager.removeView(it) } }
            burbuja?.let { runCatching { manager.removeView(it) } }
        } catch (error: Exception) {
            android.util.Log.e("mlbb", "cerrar overlay", error)
        }
        panel = null
        burbuja = null
        contenido = null
        estado = null
        paramsPanel = null
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
