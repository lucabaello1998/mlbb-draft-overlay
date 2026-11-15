package com.lucabaello.mlbboverlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager

/**
 * Servicio en primer plano que sostiene la proyección de pantalla y la burbuja.
 *
 * El VirtualDisplay se crea UNA vez al iniciar y se reutiliza: crear y liberar
 * uno por captura terminaba matando la proyección en algunos teléfonos. Entre
 * capturas no se procesa nada; el display sólo espeja la pantalla.
 */
class CaptureService : Service() {

    companion object {
        const val ACCION_DETENER = "com.lucabaello.mlbboverlay.DETENER"
        const val EXTRA_CODIGO = "codigo"
        const val EXTRA_DATOS = "datos"
        private const val CANAL = "captura"
        private const val NOTIFICACION = 42
        private const val MAX_FALLOS = 3
    }

    private var proyeccion: MediaProjection? = null
    private var lector: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var burbuja: OverlayController? = null
    private var hilo: HandlerThread? = null
    private var manejador: Handler? = null

    private var ocupado = false
    private var fallosSeguidos = 0

    private var auto = false
    private var segundos = 5

    private val bucle = object : Runnable {
        override fun run() {
            if (!auto) return
            // Con la pantalla apagada no hay nada que leer: no gastamos bateria.
            if (!ocupado && pantallaEncendida()) capturar(automatico = true)
            manejador?.postDelayed(this, segundos * 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Heroes.cargar(this)
        hilo = HandlerThread("captura").also { it.start() }
        manejador = Handler(hilo!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACCION_DETENER) {
            detener()
            return START_NOT_STICKY
        }

        val codigo = intent?.getIntExtra(EXTRA_CODIGO, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val datos = intent?.getParcelableExtra<Intent>(EXTRA_DATOS)
        if (codigo != Activity.RESULT_OK || datos == null) {
            detener()
            return START_NOT_STICKY
        }

        iniciarPrimerPlano()

        val manager = getSystemService(MediaProjectionManager::class.java)
        proyeccion = manager.getMediaProjection(codigo, datos)?.also { proyeccionActiva ->
            proyeccionActiva.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        detener()
                    }
                },
                manejador
            )
        }

        burbuja = OverlayController(this) { capturar() }.also { it.mostrar() }

        val prefs = getSharedPreferences("mlbb", Context.MODE_PRIVATE)
        auto = prefs.getBoolean("auto", false)
        segundos = prefs.getInt("segundos", 5).coerceIn(2, 60)

        // El display se arma una sola vez, acá, antes de la primera captura.
        manejador?.post {
            if (!prepararDisplay()) {
                burbuja?.mostrarPanel(
                    "No se pudo preparar la captura de pantalla.\nCerrá la app y volvé a iniciar.",
                    "Sin captura"
                )
                return@post
            }
            if (auto) manejador?.postDelayed(bucle, segundos * 1000L)
        }

        return START_STICKY
    }

    private fun prepararDisplay(): Boolean {
        if (lector != null && display != null) return true
        val proyeccion = this.proyeccion ?: return false
        return try {
            val metricas = resources.displayMetrics
            val ancho = metricas.widthPixels
            val alto = metricas.heightPixels
            val nuevoLector = ImageReader.newInstance(ancho, alto, PixelFormat.RGBA_8888, 3)
            val nuevoDisplay = proyeccion.createVirtualDisplay(
                "mlbb-draft",
                ancho,
                alto,
                metricas.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                nuevoLector.surface,
                null,
                null
            )
            lector = nuevoLector
            display = nuevoDisplay
            true
        } catch (error: Throwable) {
            android.util.Log.e("mlbb", "no se pudo preparar el display", error)
            false
        }
    }

    private fun capturar(automatico: Boolean = false) {
        if (ocupado) return
        ocupado = true
        if (!automatico) burbuja?.mostrarCargando()

        manejador?.post {
            try {
                val bitmap = capturarPantalla()
                if (bitmap == null) {
                    fallosSeguidos += 1
                    if (!automatico || fallosSeguidos >= MAX_FALLOS) cortarAuto("no pude capturar la pantalla")
                    if (!automatico) {
                        burbuja?.mostrarPanel("No pude capturar la pantalla.\nVolvé a iniciar la app.", "Sin captura")
                    }
                    ocupado = false
                    return@post
                }

                fallosSeguidos = 0
                val entrada = reducir(bitmap, 1000)
                if (entrada !== bitmap) bitmap.recycle()

                OcrAnalisis.analizar(entrada, entrada.height) { resultado ->
                    runCatching { entrada.recycle() }
                    if (automatico && resultado.heroes.isEmpty()) {
                        burbuja?.ocultarPanel()
                    } else {
                        burbuja?.mostrarPanel(resultado.informe, resultado.estado)
                    }
                    ocupado = false
                }
            } catch (error: Throwable) {
                android.util.Log.e("mlbb", "captura fallida", error)
                fallosSeguidos += 1
                if (fallosSeguidos >= MAX_FALLOS) cortarAuto("error al leer la pantalla")
                if (!automatico) {
                    burbuja?.mostrarPanel("Error al capturar: ${error.message}", "Error")
                }
                ocupado = false
            }
        }
    }

    /** Un frame del display que ya está espejando. */
    private fun capturarPantalla(): Bitmap? {
        if (!prepararDisplay()) return null
        val lector = this.lector ?: return null
        val metricas = resources.displayMetrics
        val ancho = metricas.widthPixels
        val alto = metricas.heightPixels

        var imagen: Image? = null
        try {
            imagen = esperarImagen(lector) ?: return null
            return convertir(imagen, ancho, alto)
        } catch (error: IllegalStateException) {
            // La cola del lector se llenó (maxImages): se rehace y se sigue.
            android.util.Log.e("mlbb", "cola de imágenes llena, reinicio el lector", error)
            reiniciarLector()
            return null
        } finally {
            // Cerrar SIEMPRE: una imagen sin cerrar ocupa un slot y a la tercera
            // el lector deja de entregar frames.
            runCatching { imagen?.close() }
        }
    }

    private fun esperarImagen(lector: ImageReader): Image? {
        val limite = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < limite) {
            val imagen = lector.acquireLatestImage()
            if (imagen != null) return imagen
            Thread.sleep(20)
        }
        return null
    }

    private fun reiniciarLector() {
        soltarDisplay()
        prepararDisplay()
    }

    private fun convertir(imagen: Image, ancho: Int, alto: Int): Bitmap {
        val plano = imagen.planes[0]
        val buffer = plano.buffer
        val pixelStride = plano.pixelStride
        val rowStride = plano.rowStride
        val relleno = rowStride - pixelStride * ancho
        // Si el relleno viniera negativo, el bitmap tendría menos píxeles que el
        // buffer y copyPixelsFromBuffer explotaría: en ese caso se usa el ancho
        // tal cual.
        val anchoReal = if (relleno > 0) ancho + relleno / pixelStride else ancho

        val bitmap = Bitmap.createBitmap(anchoReal, alto, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return if (anchoReal == ancho) bitmap else Bitmap.createBitmap(bitmap, 0, 0, ancho, alto)
    }

    private fun reducir(bitmap: Bitmap, maxAncho: Int): Bitmap {
        if (bitmap.width <= maxAncho) return bitmap
        val escala = maxAncho.toFloat() / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, maxAncho, (bitmap.height * escala).toInt(), true)
    }

    private fun pantallaEncendida(): Boolean =
        getSystemService(PowerManager::class.java)?.isInteractive ?: true

    private fun cortarAuto(motivo: String) {
        auto = false
        manejador?.removeCallbacks(bucle)
        burbuja?.mostrarPanel(
            "Se cortó el análisis automático: $motivo.\n\nPodés seguir usando la burbuja a mano.",
            "Auto detenido"
        )
    }

    private fun iniciarPrimerPlano() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CANAL, getString(R.string.canal_nombre), NotificationManager.IMPORTANCE_LOW)
            )
        }

        val detener = PendingIntent.getService(
            this,
            1,
            Intent(this, CaptureService::class.java).setAction(ACCION_DETENER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val constructor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CANAL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notificacion = constructor
            .setContentTitle(getString(R.string.notificacion_titulo))
            .setContentText(getString(R.string.notificacion_texto))
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_launcher),
                    getString(R.string.notificacion_detener),
                    detener
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICACION, notificacion, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICACION, notificacion)
        }
    }

    private fun soltarDisplay() {
        runCatching { display?.release() }
        display = null
        runCatching { lector?.close() }
        lector = null
    }

    private fun detener() {
        auto = false
        manejador?.removeCallbacks(bucle)
        burbuja?.cerrar()
        burbuja = null
        soltarDisplay()
        runCatching { proyeccion?.stop() }
        proyeccion = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        auto = false
        manejador?.removeCallbacks(bucle)
        burbuja?.cerrar()
        burbuja = null
        soltarDisplay()
        runCatching { proyeccion?.stop() }
        proyeccion = null
        hilo?.quitSafely()
        super.onDestroy()
    }
}
