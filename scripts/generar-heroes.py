import io
import json

RUTA_WEB = "C:/Users/lucac/Documents/Aplicaciones/mlbb-draft/data/heroes.json"
RUTA_APP = "C:/Users/lucac/Documents/Aplicaciones/mlbb-overlay/app/src/main/assets/heroes.json"

# Dori existe solo en el servidor de pruebas: no es jugable, no debe estar en el
# roster. Hirara (junio 2026) es la 133 y sí está.
with io.open(RUTA_WEB, encoding="utf-8") as archivo:
    datos = json.load(archivo)

antes = len(datos["heroes"])
datos["heroes"] = [h for h in datos["heroes"] if h["id"] != "dori"]
despues = len(datos["heroes"])

with io.open(RUTA_WEB, "w", encoding="utf-8") as archivo:
    json.dump(datos, archivo, ensure_ascii=False, indent=2)

compactos = [
    {
        "id": h["id"],
        "nombre": h["nombre"],
        "rol": h.get("rol", ""),
        "carril": h.get("carril", ""),
        "tier": h.get("tier", "?"),
        "counters": h.get("counters", []),
        "sinergias": h.get("sinergias", []),
    }
    for h in datos["heroes"]
]

with io.open(RUTA_APP, "w", encoding="utf-8") as archivo:
    json.dump({"actualizado": datos.get("actualizado", ""), "heroes": compactos}, archivo, ensure_ascii=False, separators=(",", ":"))

import os

print(f"web: {antes} → {despues} héroes")
print(f"app: {len(compactos)} héroes · {round(os.path.getsize(RUTA_APP) / 1024, 1)} KB")
print("¿Hirara?", any(h["id"] == "hirara" for h in compactos))
print("¿Dori?", any(h["id"] == "dori" for h in compactos))
