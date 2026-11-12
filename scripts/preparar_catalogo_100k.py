r"""Crea un catálogo de 100 000 ediciones de Open Library en tres CSV.

Procesa datos publicados por Open Library (editions/works/authors), sin paquetes
externos y sin descargar el volcado completo de ediciones al disco.

Uso (desde la raiz del repositorio):
  py -3 .\scripts\preparar_catalogo_100k.py

Para recorrer todas las ediciones (en lugar de los 2 M registros iniciales):
  py -3 .\scripts\preparar_catalogo_100k.py --max-lineas 0
"""

import argparse
import csv
import gzip
import hashlib
import heapq
import json
import re
import sys
from collections import Counter
from pathlib import Path
from urllib.request import Request, urlopen

RAIZ = Path(__file__).resolve().parent.parent
DATA = RAIZ / "data"
FECHA = "2026-07-31"
URL_EDICIONES = (
    "https://archive.org/download/ol_dump_2026-07-31/"
    "ol_dump_editions_2026-07-31.txt.gz"
)
ARCHIVO_OBRAS = DATA / f"ol_dump_works_{FECHA}.txt.gz"
ARCHIVO_AUTORES = DATA / f"ol_dump_authors_{FECHA}.txt.gz"

RECHAZAR_TITULO = re.compile(
    r"\b(?:coloring book|colouring book|coloring pages|"
    r"notebook|notepad|blank journal|daily planner|word search|"
    r"house of lords|house of commons|parliamentary debates|"
    r"well records|drilling sequence|livre de coloriage|"
    r"company profile|corporate profile)\b",
    re.IGNORECASE,
)

# Clasificacion aproximada: las materias originales se guardan por separado.
CATEGORIAS = [
    ("Informática", r"\b(?:computer|computing|software|programming|algorithm|database)\b"),
    ("Derecho", r"\b(?:law|legal|jurisprudence|legislation|constitutional)\b"),
    ("Economía", r"\b(?:economics|finance|business|management|accounting|marketing)\b"),
    ("Política", r"\b(?:politics|government|diplomacy|political science|international relations)\b"),
    ("Medicina", r"\b(?:medicine|medical|health|clinical|nursing|pharmacy)\b"),
    ("Ingeniería", r"\b(?:engineering|mechanical|electrical engineering|electronics)\b"),
    ("Psicología", r"\b(?:psychology|psychiatry|psychoanalysis)\b"),
    ("Educación", r"\b(?:education|teaching|pedagogy|curriculum)\b"),
    ("Filosofía", r"\b(?:philosophy|ethics|metaphysics)\b"),
    ("Literatura", r"\b(?:fiction|novel|poetry|literature|drama|plays)\b"),
    ("Historia", r"\b(?:history|archaeology|historical studies)\b"),
    ("Ciencias", r"\b(?:science|physics|chemistry|biology|mathematics|geology|astronomy)\b"),
    ("Arte", r"\b(?:art|design|painting|architecture|photography|music)\b"),
    ("Infantil y juvenil", r"\b(?:children's fiction|juvenile literature|picture books)\b"),
]
CATEGORIAS_REGEX = [(n, re.compile(p)) for n, p in CATEGORIAS]


def libro_isbn13(valores):
    if not isinstance(valores, list):
        return None
    for valor in valores:
        if not isinstance(valor, (str, int)):
            continue
        isbn = re.sub(r"\D", "", str(valor))
        if len(isbn) != 13:
            continue
        total = sum(int(d) * (1 if i % 2 == 0 else 3) for i, d in enumerate(isbn[:12]))
        if (10 - total % 10) % 10 == int(isbn[-1]):
            return isbn
    return None


def limpiar(valor, limite=None):
    if not isinstance(valor, str):
        return ""
    texto = " ".join(valor.replace("\x00", "").split()).strip()
    return texto[:limite] if limite else texto


def clave_referencia(objeto, prefijo):
    if not isinstance(objeto, dict):
        return None
    clave = objeto.get("key")
    return clave if isinstance(clave, str) and clave.startswith(prefijo) else None


def keys_autores(entradas):
    resultado = []
    if not isinstance(entradas, list):
        return resultado
    for entrada in entradas:
        if not isinstance(entrada, dict):
            continue
        clave = clave_referencia(entrada.get("author"), "/authors/")
        if not clave:
            clave = clave_referencia(entrada, "/authors/")
        if clave and clave not in resultado:
            resultado.append(clave)
    return resultado


def normalizar_edicion(registro):
    if not isinstance(registro, dict):
        return None
    clave = registro.get("key")
    if not isinstance(clave, str) or not clave.startswith("/books/") or len(clave) > 50:
        return None
    titulo = limpiar(registro.get("title"))
    if not titulo or len(titulo) > 255 or RECHAZAR_TITULO.search(titulo):
        return None
    obras = registro.get("works")
    if not isinstance(obras, list) or not obras:
        return None
    obra = clave_referencia(obras[0], "/works/")
    if not obra or len(obra) > 50:
        return None
    isbn = libro_isbn13(registro.get("isbn_13"))
    if not isbn:
        return None
    editoriales = registro.get("publishers")
    editorial = limpiar(editoriales[0]) if isinstance(editoriales, list) and editoriales else ""
    if not editorial or len(editorial) > 150:
        return None
    portadas = registro.get("covers")
    portada = next((n for n in portadas if type(n) is int and n > 0), None) if isinstance(portadas, list) else None
    return {
        "openlibrary_key": clave,
        "openlibrary_work_key": obra,
        "isbn": isbn,
        "titulo": titulo,
        "editorial": editorial,
        "fecha_publicacion": registro.get("publish_date"),
        "cover_id": portada,
        "autores_keys_edicion": keys_autores(registro.get("authors")),
    }


def hash_estable(clave):
    return int.from_bytes(
        hashlib.blake2b(("Deusto2026:" + clave).encode("utf-8"), digest_size=8).digest(),
        "big",
    )


def elegir_candidatos(url, max_lineas, tamano_muestra):
    heap = []
    validos = 0
    vistos = 0
    request = Request(url, headers={"User-Agent": "BibliotecaVirtualDeusto/1.0 (academic data import)"})
    print(f"1/4. Seleccionando candidatos de ediciones ({url})", flush=True)
    with urlopen(request, timeout=120) as respuesta, gzip.GzipFile(fileobj=respuesta) as origen:
        for raw in origen:
            vistos += 1
            if max_lineas and vistos > max_lineas:
                break
            if vistos % 100_000 == 0:
                print(f"  Revisadas: {vistos:,} | válidas: {validos:,} | muestra: {len(heap):,}", flush=True)
            try:
                columnas = raw.split(b"\t", 4)
                if len(columnas) != 5 or columnas[0] != b"/type/edition":
                    continue
                libro = normalizar_edicion(json.loads(columnas[4]))
                if not libro:
                    continue
                validos += 1
                puntuacion = hash_estable(libro["openlibrary_key"])
                elemento = (-puntuacion, libro["openlibrary_key"], libro)
                if len(heap) < tamano_muestra:
                    heapq.heappush(heap, elemento)
                elif elemento > heap[0]:
                    heapq.heapreplace(heap, elemento)
            except (ValueError, TypeError, AttributeError, UnicodeDecodeError):
                continue
    candidatos = [e[2] for e in sorted(heap, reverse=True)]
    # Deduplicar la muestra antes de buscar en el volcado de obras.
    claves, isbns, obras = set(), set(), set()
    resultado = []
    for libro in candidatos:
        if libro["openlibrary_key"] in claves or libro["isbn"] in isbns or libro["openlibrary_work_key"] in obras:
            continue
        claves.add(libro["openlibrary_key"])
        isbns.add(libro["isbn"])
        obras.add(libro["openlibrary_work_key"])
        resultado.append(libro)
    print(f"  Candidatos distintos: {len(resultado):,}", flush=True)
    return resultado


def leer_json_por_claves(ruta, claves, tipo, callback):
    encontrados = 0
    print(f"  Buscando {len(claves):,} {tipo} en {ruta.name}", flush=True)
    pendientes = set(claves)
    with gzip.open(ruta, "rb") as origen:
        for numero, raw in enumerate(origen, 1):
            columnas = raw.split(b"\t", 4)
            if len(columnas) != 5:
                continue
            clave = columnas[1].decode("utf-8", errors="replace")
            if clave not in pendientes:
                continue
            try:
                registro = json.loads(columnas[4])
            except (ValueError, UnicodeDecodeError):
                continue
            callback(clave, registro)
            pendientes.remove(clave)
            encontrados += 1
            if not pendientes:
                break
            if numero % 3_000_000 == 0:
                print(f"    Revisados {numero:,} registros | encontrados {encontrados:,}", flush=True)
    print(f"  Encontrados {encontrados:,} / {len(claves):,}", flush=True)
    return encontrados


def obtener_materias(obra):
    materias = obra.get("subjects") or []
    if not isinstance(materias, list):
        return []
    resultado = []
    for elemento in materias[:35]:
        nombre = limpiar(elemento)
        if 0 < len(nombre) <= 300 and nombre not in resultado:
            resultado.append(nombre)
        if len(resultado) == 20:
            break
    return resultado


def obtener_descripcion(obra):
    desc = obra.get("description")
    if isinstance(desc, dict):
        desc = desc.get("value")
    return limpiar(desc, 6000)


def extraer_anio(fecha):
    if not isinstance(fecha, str):
        return ""
    for ano in re.findall(r"(?<!\d)\d{4}(?!\d)", fecha):
        if 1450 <= int(ano) <= 2026:
            return int(ano)
    return ""


def categoria_principal(materias):
    texto = " ".join(materias).casefold()
    for nombre, patron in CATEGORIAS_REGEX:
        if patron.search(texto):
            return nombre
    return "Sin clasificar"


def generar_csv(candidatos, obras, nombres_autores, objetivo, max_editorial, salida):
    salida.mkdir(parents=True, exist_ok=True)
    campos = [
        "isbn", "titulo", "autor", "editorial", "categoria",
        "anio_publicacion", "descripcion", "portada_url",
        "openlibrary_key", "openlibrary_work_key", "origen",
    ]
    # Primero los registros con materias; desempatamos con hash determinista.
    def orden(libro):
        m = obras.get(libro["openlibrary_work_key"], {})
        return (not bool(m.get("materias")), hash_estable(libro["openlibrary_key"]))

    contador_editoriales = Counter()
    materias_distintas = set()
    relaciones = set()
    seleccionados = 0
    categorias = Counter()
    rechazados_sin_autor = 0
    with (salida / "libros_import.csv").open("w", encoding="utf-8", newline="") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=campos)
        escritor.writeheader()
        for libro in sorted(candidatos, key=orden):
            obra = obras.get(libro["openlibrary_work_key"], {})
            claves_autores = obra.get("autores") or libro["autores_keys_edicion"]
            autor = next((nombres_autores.get(clave) for clave in claves_autores if nombres_autores.get(clave)), None)
            if not autor:
                rechazados_sin_autor += 1
                continue
            editorial_canonica = " ".join(libro["editorial"].casefold().split())
            if contador_editoriales[editorial_canonica] >= max_editorial:
                continue
            materias = obra.get("materias") or []
            categoria = categoria_principal(materias)
            portada = (
                f"https://covers.openlibrary.org/b/id/{libro['cover_id']}-L.jpg"
                if libro["cover_id"] else ""
            )
            escritor.writerow({
                "isbn": libro["isbn"],
                "titulo": libro["titulo"],
                "autor": autor[:255],
                "editorial": libro["editorial"],
                "categoria": categoria,
                "anio_publicacion": extraer_anio(libro.get("fecha_publicacion")),
                "descripcion": obra.get("descripcion") or "",
                "portada_url": portada,
                "openlibrary_key": libro["openlibrary_key"],
                "openlibrary_work_key": libro["openlibrary_work_key"],
                "origen": "OPEN_LIBRARY",
            })
            seleccionados += 1
            categorias[categoria] += 1
            contador_editoriales[editorial_canonica] += 1
            for materia in materias:
                materias_distintas.add(materia)
                relaciones.add((libro["openlibrary_key"], materia))
            if seleccionados == objetivo:
                break
    if seleccionados != objetivo:
        raise RuntimeError(
            f"Solo se consiguieron {seleccionados:,} de {objetivo:,} libros. "
            "Amplía --max-lineas y/o --muestra. No se deben importar CSV incompletos."
        )
    with (salida / "materias_import.csv").open("w", encoding="utf-8", newline="") as archivo:
        escritor = csv.writer(archivo)
        escritor.writerow(["nombre"])
        for materia in sorted(materias_distintas):
            escritor.writerow([materia])
    with (salida / "libro_materias_import.csv").open("w", encoding="utf-8", newline="") as archivo:
        escritor = csv.writer(archivo)
        escritor.writerow(["openlibrary_key", "materia_nombre"])
        for clave, materia in sorted(relaciones):
            escritor.writerow([clave, materia])
    print("\nRESULTADOS")
    print(f"Libros exportados: {seleccionados:,}")
    print(f"Materias únicas: {len(materias_distintas):,}")
    print(f"Relaciones libro-materia: {len(relaciones):,}")
    print(f"Candidatos sin autor identificable: {rechazados_sin_autor:,}")
    print("Categorías más habituales:")
    for categoria, n in categorias.most_common(10):
        print(f"  {n:>7,} {categoria}")
    print(f"CSV en: {salida}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--objetivo", type=int, default=100_000)
    parser.add_argument("--max-lineas", type=int, default=2_000_000,
                        help="Número de registros de ediciones a examinar; 0 = todo el volcado")
    parser.add_argument("--muestra", type=int, default=250_000,
                        help="Cantidad máxima de candidatos retenidos en memoria")
    parser.add_argument("--max-editorial", type=int, default=500)
    parser.add_argument("--url-ediciones", default=URL_EDICIONES)
    parser.add_argument("--obras", type=Path, default=ARCHIVO_OBRAS)
    parser.add_argument("--autores", type=Path, default=ARCHIVO_AUTORES)
    parser.add_argument("--salida", type=Path, default=DATA / "export_100000")
    args = parser.parse_args()
    if args.objetivo < 1 or args.muestra < args.objetivo or args.max_lineas < 0:
        parser.error("El objetivo debe ser positivo, muestra >= objetivo, max-lineas >= 0")
    if args.max_editorial < 1:
        parser.error("max-editorial debe ser positivo")
    for archivo in (args.obras, args.autores):
        if not archivo.is_file():
            parser.error(f"No existe {archivo}. Comprueba los volcados descargados.")
    candidatos = elegir_candidatos(args.url_ediciones, args.max_lineas, args.muestra)
    claves_obras = {libro["openlibrary_work_key"] for libro in candidatos}
    obras = {}
    print("2/4. Leyendo obras", flush=True)

    def guardar_obra(clave, registro):
        obras[clave] = {
            "materias": obtener_materias(registro),
            "descripcion": obtener_descripcion(registro),
            "autores": keys_autores(registro.get("authors")),
        }

    leer_json_por_claves(args.obras, claves_obras, "obras", guardar_obra)
    # No aceptar ediciones sin una obra presente en el volcado elegido.
    candidatos = [l for l in candidatos if l["openlibrary_work_key"] in obras]
    claves_autores = {
        clave for libro in candidatos
        for clave in (obras[libro["openlibrary_work_key"]]["autores"] or libro["autores_keys_edicion"])
    }
    nombres = {}
    print("3/4. Leyendo autores", flush=True)

    def guardar_autor(clave, registro):
        nombre = limpiar(registro.get("name"), 255)
        if nombre:
            nombres[clave] = nombre

    leer_json_por_claves(args.autores, claves_autores, "autores", guardar_autor)
    print("4/4. Generando CSV", flush=True)
    generar_csv(candidatos, obras, nombres, args.objetivo, args.max_editorial, args.salida)


if __name__ == "__main__":
    try:
        main()
    except (OSError, EOFError, RuntimeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
