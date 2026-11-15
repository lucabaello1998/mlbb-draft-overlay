package com.lucabaello.mlbboverlay

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Lee la captura y arma el informe del draft.
 *
 * En la pantalla de carga el juego imprime el nombre del héroe debajo del arte,
 * así que alcanza con OCR: no hace falta comparar imágenes (que con skins, tintes
 * y compresión falla). Se buscan los nombres y se separan por posición vertical.
 */
object OcrAnalisis {

    private val reconocedor by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun analizar(bitmap: Bitmap, alto: Int, onListo: (String) -> Unit) {
        val imagen = InputImage.fromBitmap(bitmap, 0)
        reconocedor.process(imagen)
            .addOnSuccessListener { texto -> onListo(construir(texto, alto)) }
            .addOnFailureListener { error ->
                onListo("No pude leer la pantalla.\n${error.message ?: "error desconocido"}")
            }
    }

    private data class Detectado(val heroe: Hero, val y: Int)

    private fun construir(texto: Text, alto: Int): String {
        val detectados = ArrayList<Detectado>()
        val vistos = HashSet<String>()

        for (bloque in texto.textBlocks) {
            for (linea in bloque.lines) {
                val heroe = Heroes.buscar(linea.text) ?: continue
                if (!vistos.add(heroe.id)) continue
                val caja = linea.boundingBox ?: continue
                detectados += Detectado(heroe, caja.centerY())
            }
        }

        if (detectados.size < 4) {
            val lineas = texto.textBlocks.sumOf { it.lines.size }
            return "No encontré héroes en la captura.\n" +
                "Se leyeron $lineas líneas de texto.\n\n" +
                "Usá la pantalla de carga del draft, donde aparecen los 10 nombres."
        }

        detectados.sortBy { it.y }
        val mitad = alto / 2

        val aliados: List<Hero>
        val enemigos: List<Hero>
        if (detectados.size >= 10) {
            aliados = detectados.take(5).map { it.heroe }
            enemigos = detectados.takeLast(5).map { it.heroe }
        } else {
            aliados = detectados.filter { it.y < mitad }.take(5).map { it.heroe }
            enemigos = detectados.filter { it.y >= mitad }.take(5).map { it.heroe }
        }

        return informe(aliados, enemigos, detectados.size)
    }

    private fun informe(aliados: List<Hero>, enemigos: List<Hero>, total: Int): String {
        fun ficha(heroe: Hero): String {
            val extra = listOf(heroe.carril, heroe.rol).filter { it.isNotBlank() }.joinToString(" · ")
            return if (extra.isBlank()) heroe.nombre else "${heroe.nombre} ($extra)"
        }

        val sb = StringBuilder()
        sb.append("MI EQUIPO\n")
        sb.append(if (aliados.isEmpty()) "— sin detectar —" else aliados.joinToString("\n") { "• ${ficha(it)}" })
        sb.append("\n\nENEMIGO\n")
        sb.append(if (enemigos.isEmpty()) "— sin detectar —" else enemigos.joinToString("\n") { "• ${ficha(it)}" })
        sb.append("\n\nCONTRAS\n")

        val elegidos = (aliados + enemigos).map { it.id }.toSet()
        var hayContras = false
        for (enemigo in enemigos) {
            val contras = enemigo.counters
                .filter { it !in elegidos }
                .map { Heroes.nombreDe(it) }
                .take(3)
            if (contras.isNotEmpty()) {
                hayContras = true
                sb.append("vs ${enemigo.nombre}: ${contras.joinToString(", ")}\n")
            }
        }
        if (!hayContras) sb.append("Sin counters cargados para este draft.\n")

        sb.append("\nDetectados: $total de 10")
        return sb.toString().trim()
    }
}
