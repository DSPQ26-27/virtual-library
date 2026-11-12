
import gzip
import hashlib
import heapq
import io
import json
import re
from collections import Counter
from pathlib import Path
from urllib.request import Request, urlopen

URL = "https://openlibrary.org/data/ol_dump_editions_latest.txt.gz"

LIMITE = 1000
MAX_REVISADOS = 100_000
TAMANO_MUESTRA = 10_000
MAX_POR_EDITORIAL = 30

DESTINO = (
    Path(__file__).resolve().parent.parent
    / "data" / "candidatos_1000_diversos.jsonl.gz"
)


def isbn_valido(valores):
    if not isinstance(valores, list):
        return None

    for valor in valores:
        isbn = re.sub(r"\D", "", str(valor))

        if len(isbn) != 13:
            continue

        suma = sum(
            int(d) * (1 if i % 2 == 0 else 3)
            for i, d in enumerate(isbn[:12])
        )

        if (10 - suma % 10) % 10 == int(isbn[-1]):
            return isbn

    return None


def normalizar(registro):
    clave = registro.get("key")
    titulo = registro.get("title")
    obras = registro.get("works") or []
    editoriales = registro.get("publishers") or []

    if not isinstance(clave, str) or not clave.startswith("/books/"):
        return None

    if not isinstance(titulo, str):
        return None

    titulo = " ".join(titulo.split())

    if not titulo or len(titulo) > 255:
        return None

    if not obras or not isinstance(obras[0], dict):
        return None

    obra = obras[0].get("key")

    if not isinstance(obra, str) or not obra.startswith("/works/"):
        return None

    if not editoriales or not isinstance(editoriales[0], str):
        return None

    editorial = " ".join(editoriales[0].split())

    if not editorial or len(editorial) > 150:
        return None

    isbn = isbn_valido(registro.get("isbn_13", []))

    if not isbn:
        return None

    return {
        "openlibrary_key": clave,
        "openlibrary_work_key": obra,
        "titulo": titulo,
        "isbn": isbn,
        "editorial": editorial,
        "fecha_publicacion": registro.get("publish_date"),
        "covers": registro.get("covers", []),
    }


def puntuacion(clave):
    # Selección determinista independiente del orden de llegada
    resumen = hashlib.sha256(clave.encode("utf-8")).hexdigest()
    return int(resumen[:16], 16)


def main():
    DESTINO.parent.mkdir(parents=True, exist_ok=True)

    muestra = []
    revisados = 0
    validos = 0

    request = Request(
        URL,
        headers={"User-Agent": "BibliotecaVirtualDeusto/1.0"}
    )

    print("Leyendo ediciones de Open Library...")

    with urlopen(request, timeout=90) as respuesta:
        with gzip.GzipFile(fileobj=respuesta) as archivo:

            for linea in archivo:
                revisados += 1

                try:
                    columnas = linea.decode("utf-8").rstrip("\n").split("\t", 4)

                    if len(columnas) != 5 or columnas[0] != "/type/edition":
                        continue

                    registro = json.loads(columnas[4])
                    libro = normalizar(registro)

                    if libro is None:
                        continue

                    validos += 1
                    puntos = puntuacion(libro["openlibrary_key"])

                    elemento = (-puntos, libro["openlibrary_key"], libro)

                    if len(muestra) < TAMANO_MUESTRA:
                        heapq.heappush(muestra, elemento)
                    elif puntos < -muestra[0][0]:
                        heapq.heapreplace(muestra, elemento)

                except (ValueError, TypeError, AttributeError, UnicodeDecodeError):
                    continue

                if revisados % 10_000 == 0:
                    print(f"Revisados: {revisados:,} | Válidos: {validos:,}")

                if revisados >= MAX_REVISADOS:
                    break

    # Los candidatos con menor hash tienen prioridad.
    ordenados = sorted(
        muestra,
        key=lambda elemento: (-elemento[0], elemento[1])
    )

    elegidos = []
    isbns = set()
    obras = set()
    claves = set()
    por_editorial = Counter()

    for _, _, libro in ordenados:
        editorial = libro["editorial"].casefold()
        isbn = libro["isbn"]
        obra = libro["openlibrary_work_key"]
        clave = libro["openlibrary_key"]

        if isbn in isbns or obra in obras or clave in claves:
            continue

        if por_editorial[editorial] >= MAX_POR_EDITORIAL:
            continue

        elegidos.append(libro)
        isbns.add(isbn)
        obras.add(obra)
        claves.add(clave)
        por_editorial[editorial] += 1

        if len(elegidos) == LIMITE:
            break

    # Gzip reproducible para el mismo contenido de entrada.
    with open(DESTINO, "wb") as archivo:
        with gzip.GzipFile(
            fileobj=archivo, mode="wb", filename="", mtime=0
        ) as comprimido:
            with io.TextIOWrapper(
                comprimido, encoding="utf-8"
            ) as salida:
                for libro in elegidos:
                    salida.write(
                        json.dumps(libro, ensure_ascii=False) + "\n"
                    )

    print("\nPROCESO TERMINADO")
    print("Registros revisados:", revisados)
    print("Registros válidos:", validos)
    print("Libros seleccionados:", len(elegidos))
    print("Obras diferentes:", len(obras))
    print("Archivo:", DESTINO)

    print("\nEDITORIALES MÁS FRECUENTES:")
    for editorial, cantidad in por_editorial.most_common(10):
        print(cantidad, "-", editorial)


if __name__ == "__main__":
    main()
