package com.lucabaello.mlbboverlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder

/**
 * Servicio en primer plano que sostiene la proyección de pantalla y la burbuja.
 *
 * La captura NO es continua: el VirtualDisplay se crea cuando tocás la burbuja,
 * se toma un frame y se libera. Entre toques la app no consume CPU ni GPU.
 */
class CaptureService : Service() {

    companion object {
        const val ACCION_DETENER = "com.lucabaello.mlbboverlay.DETENER"
        const val EXTRA_CODIGO = "codigo"
        const val EXTRA_DATOS = "datos"
        private const val CANAL = "captura"
        private const val NOTIFICACION = 42
    }

    private var proyeccion: MediaProjection? = null
    private var burbuja: OverlayController? = null
    private var hilo: HandlerThread? = null
    private var manejador: Handler? = null
    private var ocupado = false

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
        return START_STICKY
    }

    private fun capturar() {
        if (ocupado) return
        ocupado = true
        burbuja?.mostrarCargando()

        manejador?.post {
            try {
                val bitmap = capturarPantalla()
                if (bitmap == null) {
                    burbuja?.mostrarPanel("No pude capturar la pantalla.\nCerrá y volvé a iniciar la app.")
                    ocupado = false
                    return@post
                }
                val reducido = reducir(bitmap, 1200)
                OcrAnalisis.analizar(reducido, bitmap.height) { texto ->
                    burbuja?.mostrarPanel(texto)
                    ocupado = false
                }
            } catch (error: Exception) {
                burbuja?.mostrarPanel("Error al capturar: ${error.message}")
                ocupado = false
            }
        }
    }

    /** Un solo frame: se crea el display virtual, se toma la imagen y se libera. */
    private fun capturarPantalla(): Bitmap? {
        val proyeccion = this.proyeccion ?: return null
        val metricas = resources.displayMetrics
        val ancho = metricas.widthPixels
        val alto = metricas.heightPixels

        val lector = ImageReader.newInstance(ancho, alto, PixelFormat.RGBA_8888, 2)
        val display = proyeccion.createVirtualDisplay(
            "mlbb-draft",
            ancho,
            alto,
            metricas.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            lector.surface,
            null,
            null
        )

        var imagen: Image? = null
        val limite = System.currentTimeMillis() + 2500
        while (System.currentTimeMillis() < limite && imagen == null) {
            imagen = lector.acquireLatestImage()
            if (imagen == null) Thread.sleep(25)
        }

        val bitmap = imagen?.let { convertir(it, ancho, alto) }
        imagen?.close()
        display.release()
        lector.close()
        return bitmap
    }

    private fun convertir(imagen: Image, ancho: Int, alto: Int): Bitmap {
        val plano = imagen.planes[0]
        val buffer = plano.buffer
        val pixelStride = plano.pixelStride
        val rowStride = plano.rowStride
        val relleno = rowStride - pixelStride * ancho
        val anchoReal = ancho + relleno / pixelStride

        val bitmap = Bitmap.createBitmap(anchoReal, alto, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return if (anchoReal == ancho) bitmap else Bitmap.createBitmap(bitmap, 0, 0, ancho, alto)
    }

    private fun reducir(bitmap: Bitmap, maxAncho: Int): Bitmap {
        if (bitmap.width <= maxAncho) return bitmap
        val escala = maxAncho.toFloat() / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, maxAncho, (bitmap.height * escala).toInt(), true)
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
                Notification.Action.Builder(null, getString(R.string.notificacion_detener), detener).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICACION, notificacion, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICACION, notificacion)
        }
    }

    private fun detener() {
        burbuja?.cerrar()
        burbuja = null
        runCatching { proyeccion?.stop() }
        proyeccion = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        burbuja?.cerrar()
        burbuja = null
        runCatching { proyeccion?.stop() }
        proyeccion = null
        hilo?.quitSafely()
        super.onDestroy()
    }
}
