package com.lucabaello.mlbboverlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HeroesTest {

    @Before
    fun preparar() {
        Heroes.cargarDesdeLista(
            listOf(
                Hero("masha", "Masha", "Fighter", "Exp", "S", emptyList(), emptyList()),
                Hero("cyclops", "Cyclops", "Mage", "Medio", "B", emptyList(), emptyList()),
                Hero("hirara", "Hirara", "Assassin", "Jungla", "?", emptyList(), emptyList()),
                Hero("yi-sun-shin", "Yi Sun-shin", "Assassin", "Jungla", "?", emptyList(), emptyList()),
                Hero("chang-e", "Chang'e", "Mage", "Medio", "?", emptyList(), emptyList()),
                Hero("popol-and-kupa", "Popol and Kupa", "Marksman", "Oro", "?", emptyList(), emptyList())
            )
        )
    }

    @Test
    fun `encuentra el nombre exacto`() {
        assertEquals("masha", Heroes.buscar("Masha")?.id)
    }

    @Test
    fun `ignora acentos del cliente en espanol`() {
        assertEquals("cyclops", Heroes.buscar("Cíclope")?.id)
    }

    @Test
    fun `tolera ruido alrededor del nombre`() {
        assertEquals("masha", Heroes.buscar("Masha Lv.6")?.id)
        assertEquals("masha", Heroes.buscar("  MASHA  ")?.id)
    }

    @Test
    fun `tolera un error de tipeo del OCR`() {
        assertEquals("hirara", Heroes.buscar("Hirarra")?.id)
    }

    @Test
    fun `resuelve alias cortos`() {
        assertEquals("hirara", Heroes.buscar("Hira")?.id)
    }

    @Test
    fun `nombres con apostrofe y guion`() {
        assertEquals("chang-e", Heroes.buscar("Chang'e")?.id)
        assertEquals("chang-e", Heroes.buscar("Chang E")?.id)
        assertEquals("yi-sun-shin", Heroes.buscar("Yi Sun-shin")?.id)
        assertEquals("popol-and-kupa", Heroes.buscar("Popol and Kupa")?.id)
    }

    @Test
    fun `no inventa con texto que no es un heroe`() {
        assertNull(Heroes.buscar("GG WP"))
        assertNull(Heroes.buscar(""))
        assertNull(Heroes.buscar("12"))
    }
}
