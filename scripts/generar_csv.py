
import csv
import gzip
import json
import re
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
ENTRADA = RAIZ / "data/candidatos_1000_completos.jsonl.gz"
SALIDA = RAIZ / "data/export_1000"

SALIDA.mkdir(parents=True, exist_ok=True)


def obtener_anio(fecha):
    if not isinstance(fecha, str):
        return ""

    coincidencias = re.findall(r"\b\d{4}\b", fecha)

    for valor in coincidencias:
        anio = int(valor)
        if 1450 <= anio <= 2026:
            return anio

    return ""


def obtener_portada(libro):
    for identificador in libro.get("covers") or []:
        if type(identificador) is int and identificador > 0:
            return (
                f"https://covers.openlibrary.org"
                f"/b/id/{identificador}-L.jpg"
            )
    return ""


def obtener_categoria(materias):
    texto = " ".join(materias).casefold()

    reglas = [
        ("Informática", ["computer", "programming", "software"]),
        ("Derecho", ["law", "legal", "jurisprudence"]),
        ("Economía", ["economics", "finance", "business"]),
        ("Política", ["politics", "government"]),
        ("Historia", ["history", "historical"]),
        ("Ciencias", ["science", "physics", "chemistry", "biology"]),
        ("Literatura", ["fiction", "literature", "poetry"]),
    ]

    for categoria, palabras in reglas:
        if any(palabra in texto for palabra in palabras):
            return categoria

    return "Sin clasificar"


def main():
    materias_unicas = set()
    relaciones = set()
    libros_exportados = 0
    descartados = 0

    campos = [
        "isbn",
        "titulo",
        "autor",
        "editorial",
        "categoria",
        "anio_publicacion",
        "descripcion",
        "portada_url",
        "openlibrary_key",
        "openlibrary_work_key",
        "origen",
    ]

    with (
        gzip.open(ENTRADA, "rt", encoding="utf-8") as origen,
        open(SALIDA / "libros_import.csv", "w",
             newline="", encoding="utf-8") as destino
    ):
        escritor = csv.DictWriter(destino, fieldnames=campos)
        escritor.writeheader()

        for linea in origen:
            libro = json.loads(linea)

            autor = libro.get("autor")
            if not autor:
                descartados += 1
                continue

            materias = []
            for materia in libro.get("materias") or []:
                if not isinstance(materia, str):
                    continue

                materia = " ".join(materia.split())
                if 0 < len(materia) <= 300:
                    materias.append(materia)

            clave = libro["openlibrary_key"]

            escritor.writerow({
                "isbn": libro["isbn"],
                "titulo": libro["titulo"],
                "autor": autor,
                "editorial": libro["editorial"],
                "categoria": obtener_categoria(materias),
                "anio_publicacion": obtener_anio(
                    libro.get("fecha_publicacion")
                ),
                "descripcion": libro.get("descripcion") or "",
                "portada_url": obtener_portada(libro),
                "openlibrary_key": clave,
                "openlibrary_work_key": libro["openlibrary_work_key"],
                "origen": "OPEN_LIBRARY",
            })

            libros_exportados += 1

            for materia in materias:
                materias_unicas.add(materia)
                relaciones.add((clave, materia))

    with open(
        SALIDA / "materias_import.csv", "w",
        newline="", encoding="utf-8"
    ) as archivo:
        escritor = csv.writer(archivo)
        escritor.writerow(["nombre"])
        for materia in sorted(materias_unicas):
            escritor.writerow([materia])

    with open(
        SALIDA / "libro_materias_import.csv", "w",
        newline="", encoding="utf-8"
    ) as archivo:
        escritor = csv.writer(archivo)
        escritor.writerow(["openlibrary_key", "materia_nombre"])
        for relacion in sorted(relaciones):
            escritor.writerow(relacion)

    print("\nRESULTADOS")
    print("Libros exportados:", libros_exportados)
    print("Libros descartados:", descartados)
    print("Materias únicas:", len(materias_unicas))
    print("Relaciones libro-materia:", len(relaciones))
    print("Archivos guardados en:", SALIDA)


if __name__ == "__main__":
    main()
