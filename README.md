# MLBB Draft Overlay

App Android nativa que se superpone al juego: tocás una burbuja, lee la pantalla
de draft, reconoce los 10 héroes y te muestra los counters. **Sin internet, sin
cuenta, sin permisos raros.**

```text
MI EQUIPO
• Masha (Exp · Fighter)
• Guinevere (Exp · Fighter)
• Cíclope (Medio · Mage)
• Melissa (Oro · Marksman)
• Hirara (Jungla · Assassin)

ENEMIGO
• Zhask (Medio · Mage)
• Hanabi (Oro · Marksman)
• Lesley (Oro · Marksman)
• Nolan (Jungla · Assassin)
• Hanzo (Jungla · Assassin)

CONTRAS
vs Zhask: fanny, ling, gusion
vs Hanabi: ...
```

---

## Cómo funciona

1. **Burbuja flotante** (`SYSTEM_ALERT_WINDOW`) siempre arriba del juego.
2. Tocás la burbuja → **MediaProjection** captura **un solo frame** y lo suelta.
3. **ML Kit** lee el texto de la captura (modelo offline, dentro del APK).
4. Se buscan los nombres de los héroes, se separan en dos filas por el salto
   vertical más grande y se arma el informe con los counters del dataset.

**Nada se cierra al tocar.** La burbuja nunca apaga el servicio: si tocás la
burbuja con el panel abierto, se vuelve a analizar en el mismo panel. El botón
**—** de la cabecera sólo lo minimiza (la burbuja sigue), y con un toque largo
en la burbuja lo mostrás de nuevo. Para apagar todo hay que usar *Detener* en la
app o en la notificación.

**Análisis automático** (opcional, apagado por defecto): en la pantalla
principal se activa y se elige cada cuántos segundos (2 a 60). En ese modo el
panel **sólo aparece cuando hay un draft**: si estás en otra pantalla no muestra
nada, no molesta.

Si la captura no es la pantalla de carga, el panel dice **"No hay pantalla de
carga detectada"** con lo que alcanzó a leer, en vez de cerrarse.

La captura **no es continua**: entre toques la app no consume CPU ni GPU. Lo
único que queda es la burbuja y una notificación, que es lo que Android exige
mientras la proyección está activa.

## Por qué OCR y no comparar imágenes

Se probó primero el matching por firma de imagen contra los íconos oficiales de
la CDN de Moonton: **33% de acierto** (7 de 21) por tres motivos acumulados:

- el juego aplica un tinte azul a los íconos del pool;
- hace zoom y desplaza el encuadre;
- las capturas compartidas por WhatsApp pierden resolución.

En la pantalla de carga, en cambio, **el juego imprime el nombre del héroe**
debajo del arte. Leer texto es órdenes de magnitud más confiable que comparar
píxeles, y funciona igual con cualquier skin.

## Instalación

APK firmado, Android 8 (API 26) o superior:

```bash
adb install -r mlbb-draft-overlay.apk
```

O copiás el APK al teléfono y lo abrís (hay que permitir "instalar apps
desconocidas" para el explorador de archivos).

### Permisos, en orden

1. **Dibujar sobre otras apps** (superposición). Se pide desde la app.
2. **Captura de pantalla**. Android muestra su propio diálogo: hay que aceptarlo.
3. **Notificaciones** (Android 13+), para el aviso de la captura activa.

No pide internet, ni ubicación, ni contactos, ni almacenamiento.

## Compilar

```bash
# JDK 17+ y Android SDK 35
./gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/`. Para firmar, copiá
`keystore.properties.example` a `keystore.properties` y apuntá a tu keystore
(ambos quedan fuera de git).

## Límites honestos

- **Solo Android.** iOS no permite superponer ni capturar otras apps.
- **Funciona en la pantalla de carga**, que es donde aparecen los 10 nombres.
  En la fase de picks/bans se ven menos.
- **Anti-cheat**: MLBB tiene anti-cheat. Grabar la pantalla está permitido (el
  juego trae grabador), pero una herramienta que lee el draft es asistencia de
  terceros. **Riesgo de sanción bajo, no cero.** Es para uso personal; no está
  pensada para publicarse en Play Store.
- **Los counters salen del dataset del sitio**, no de un modelo: si un héroe no
  tiene counters cargados, no inventa nada.
- **Roster de 133 héroes** (Hirara, junio 2026). Dori no está porque todavía no
  salió del servidor de pruebas.

## Privacidad

La imagen nunca sale del teléfono: no hay permiso de internet en el manifiesto y
no hay servidor. El OCR corre en el dispositivo con el modelo de ML Kit incluido
en el APK.

## Licencia

MIT. Ver [LICENSE](LICENSE).
