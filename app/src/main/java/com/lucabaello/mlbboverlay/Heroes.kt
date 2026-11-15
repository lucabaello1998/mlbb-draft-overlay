package com.lucabaello.mlbboverlay

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer

/** Un héroe del dataset, con lo mínimo para el análisis en vivo. */
data class Hero(
    val id: String,
    val nombre: String,
    val rol: String,
    val carril: String,
    val tier: String,
    val counters: List<String>,
    val sinergias: List<String>
)

/**
 * Carga el roster una sola vez y resuelve nombres a héroes.
 *
 * El OCR devuelve texto con ruido, acentos y mayúsculas: se normaliza todo y se
 * compara contra el nombre normalizado, con tolerancia a un error de tipeo.
 */
object Heroes {

    private var porId: Map<String, Hero> = emptyMap()
    private var porNombre: Map<String, Hero> = emptyMap()

    val total: Int get() = porId.size

    fun cargar(context: Context) {
        if (porId.isNotEmpty()) return

        val texto = context.assets.open("heroes.json").bufferedReader().use { it.readText() }
        val lista = JSONObject(texto).getJSONArray("heroes")
        val heroes = ArrayList<Hero>(lista.length())

        for (i in 0 until lista.length()) {
            val o = lista.getJSONObject(i)
            heroes += Hero(
                id = o.getString("id"),
                nombre = o.getString("nombre"),
                rol = o.optString("rol"),
                carril = o.optString("carril"),
                tier = o.optString("tier", "?"),
                counters = o.optJSONArray("counters").let { a ->
                    if (a == null) emptyList() else List(a.length()) { a.getString(it) }
                },
                sinergias = o.optJSONArray("sinergias").let { a ->
                    if (a == null) emptyList() else List(a.length()) { a.getString(it) }
                }
            )
        }

        cargarDesdeLista(heroes)
    }

    /** Carga desde una lista ya armada: lo usan el asset y los tests. */
    fun cargarDesdeLista(heroes: List<Hero>) {
        porId = heroes.associateBy { it.id }
        val nombres = HashMap<String, Hero>(heroes.size * 2)
        for (heroe in heroes) nombres[normalizar(heroe.nombre)] = heroe

        // Nombres que el cliente en español muestra distinto al dataset.
        val alias = mapOf(
            "ciclope" to "cyclops",
            "hira" to "hirara",
            "lapulapu" to "lapu-lapu",
            "popolkupa" to "popol-and-kupa",
            "popolandkupa" to "popol-and-kupa",
            "yisunshin" to "yi-sun-shin",
            "chang" to "chang-e"
        )
        for ((nombre, id) in alias) {
            porId[id]?.let { nombres[nombre] = it }
        }
        porNombre = nombres
    }

    fun porId(id: String): Hero? = porId[id]

    fun nombreDe(id: String): String = porId[id]?.nombre ?: id

    /** Busca un héroe en una línea de OCR. */
    fun buscar(texto: String): Hero? {
        val limpio = normalizar(texto)
        if (limpio.length < 3) return null

        porNombre[limpio]?.let { return it }

        // "Masha Lv.6", "Hirara!" u otro ruido alrededor del nombre.
        for ((nombre, heroe) in porNombre) {
            if (nombre.length >= 4 && limpio.contains(nombre)) return heroe
        }

        // Un error de tipeo del OCR en nombres largos.
        if (limpio.length >= 5) {
            for ((nombre, heroe) in porNombre) {
                if (nombre.length >= 5 && distancia(limpio, nombre) <= 1) return heroe
            }
        }
        return null
    }

    fun normalizar(texto: String): String {
        val sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
        return sinAcentos.replace(Regex("[^a-z0-9]"), "")
    }

    /** Distancia de Levenshtein acotada: alcanza para tolerar un carácter. */
    private fun distancia(a: String, b: String): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > 1) return 2

        var anteriores = IntArray(b.length + 1) { it }
        var actuales = IntArray(b.length + 1)

        for (i in 1..a.length) {
            actuales[0] = i
            for (j in 1..b.length) {
                val costo = if (a[i - 1] == b[j - 1]) 0 else 1
                actuales[j] = minOf(
                    actuales[j - 1] + 1,
                    anteriores[j] + 1,
                    anteriores[j - 1] + costo
                )
            }
            val temporal = anteriores
            anteriores = actuales
            actuales = temporal
        }
        return anteriores[b.length]
    }
}
