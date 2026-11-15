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
 * y compresión falla). Los nombres se separan en dos filas por el salto vertical
 * más grande, no por la mitad de la pantalla: el layout no siempre está centrado.
 */
object OcrAnalisis {

    private val reconocedor by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    data class Resultado(val informe: String, val estado: String, val heroes: List<String>)

    fun analizar(bitmap: Bitmap, alto: Int, onListo: (Resultado) -> Unit) {
        val imagen = InputImage.fromBitmap(bitmap, 0)
        reconocedor.process(imagen)
            .addOnSuccessListener { texto -> onListo(construir(texto, alto)) }
            .addOnFailureListener { error ->
                onListo(
                    Resultado(
                        "No pude leer la pantalla.\n${error.message ?: "error desconocido"}",
                        "Error de lectura",
                        emptyList()
                    )
                )
            }
    }

    private data class Detectado(val heroe: Hero, val y: Int)

    private fun construir(texto: Text, alto: Int): Resultado {
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
            val leido = texto.textBlocks
                .flatMap { it.lines }
                .map { it.text.trim() }
                .filter { it.isNotEmpty() }
                .take(3)
                .joinToString(" · ")

            return Resultado(
                informe = buildString {
                    append("No hay pantalla de carga detectada.\n\n")
                    append("Abrí el draft en la pantalla de carga (donde dice «La partida empezará pronto») ")
                    append("y volvé a tocar la burbuja.")
                    if (leido.isNotEmpty()) append("\n\nLo que leí: $leido")
                },
                estado = "Sin draft en pantalla",
                heroes = emptyList()
            )
        }

        val (aliados, enemigos) = dividir(detectados, alto)
        return Resultado(
            informe = informe(aliados, enemigos),
            estado = "Detectados ${detectados.size} de 10",
            heroes = detectados.map { it.heroe.id }
        )
    }

    /**
     * Separa las dos filas. Con 6 o más nombres se corta por el salto vertical
     * más grande; con menos, por la mitad de la pantalla.
     */
    private fun dividir(detectados: List<Detectado>, alto: Int): Pair<List<Hero>, List<Hero>> {
        val ordenados = detectados.sortedBy { it.y }

        if (ordenados.size < 6) {
            val mitad = alto / 2
            val arriba = ordenados.filter { it.y < mitad }.take(5).map { it.heroe }
            val abajo = ordenados.filter { it.y >= mitad }.take(5).map { it.heroe }
            return arriba to abajo
        }

        var corte = 1
        var mayor = -1
        for (i in 0 until ordenados.size - 1) {
            val salto = ordenados[i + 1].y - ordenados[i].y
            if (salto > mayor) {
                mayor = salto
                corte = i + 1
            }
        }

        return ordenados.take(corte).take(5).map { it.heroe } to
            ordenados.drop(corte).take(5).map { it.heroe }
    }

    private fun informe(aliados: List<Hero>, enemigos: List<Hero>): String {
        fun ficha(heroe: Hero): String {
            val extra = listOf(heroe.carril, heroe.rol).filter { it.isNotBlank() }.joinToString(" · ")
            return if (extra.isBlank()) heroe.nombre else "${heroe.nombre} ($extra)"
        }

        val sb = StringBuilder()
        sb.append("MI EQUIPO\n")
        sb.append(
            if (aliados.isEmpty()) "— no encontré nombres de este equipo —"
            else aliados.joinToString("\n") { "• ${ficha(it)}" }
        )

        sb.append("\n\nENEMIGO\n")
        sb.append(
            if (enemigos.isEmpty()) "— no encontré nombres de este equipo —"
            else enemigos.joinToString("\n") { "• ${ficha(it)}" }
        )

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

        return sb.toString().trim()
    }
}
