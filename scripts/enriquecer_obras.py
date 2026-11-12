
import gzip
import json
from collections import Counter
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
ENTRADA = RAIZ / "data/candidatos_1000_diversos.jsonl.gz"
OBRAS = RAIZ / "data/ol_dump_works_2026-07-31.txt.gz"
SALIDA = RAIZ / "data/candidatos_1000_con_obras.jsonl.gz"


def main():
    with gzip.open(ENTRADA, "rt", encoding="utf-8") as archivo:
        libros = [json.loads(linea) for linea in archivo]

    claves = {libro["openlibrary_work_key"] for libro in libros}
    encontradas = {}

    print(f"Buscando {len(claves)} obras...")

    with gzip.open(OBRAS, "rb") as archivo:
        for numero, linea in enumerate(archivo, 1):
            columnas = linea.split(b"\t", 4)

            if len(columnas) != 5:
                continue

            clave = columnas[1].decode("utf-8", errors="replace")

            if clave not in claves:
                continue

            try:
                obra = json.loads(columnas[4])
            except (ValueError, UnicodeDecodeError):
                continue

            autores = []
            for entrada in obra.get("authors", []):
                if isinstance(entrada, dict):
                    autor = entrada.get("author", {})
                    if isinstance(autor, dict):
                        key = autor.get("key")
                        if isinstance(key, str):
                            autores.append(key)

            materias = obra.get("subjects") or []
            if not isinstance(materias, list):
                materias = []

            descripcion = obra.get("description", "")
            if isinstance(descripcion, dict):
                descripcion = descripcion.get("value", "")

            encontradas[clave] = {
                "autores_keys": autores,
                "materias": [
                    x.strip()
                    for x in materias
                    if isinstance(x, str) and x.strip()
                ][:30],
                "descripcion": descripcion
                if isinstance(descripcion, str) else "",
            }

            if numero % 1_000_000 == 0:
                print("Registros examinados:", numero)

            if len(encontradas) == len(claves):
                break

    con_autores = 0
    con_materias = 0
    frecuencias = Counter()

    with gzip.open(SALIDA, "wt", encoding="utf-8") as salida:
        for libro in libros:
            datos = encontradas.get(
                libro["openlibrary_work_key"], {}
            )
            libro.update(datos)

            if libro.get("autores_keys"):
                con_autores += 1

            if libro.get("materias"):
                con_materias += 1
                frecuencias.update(libro["materias"])

            salida.write(
                json.dumps(libro, ensure_ascii=False) + "\n"
            )

    print("\nRESULTADOS")
    print("Obras encontradas:", len(encontradas))
    print("Libros con referencias a autores:", con_autores)
    print("Libros con materias:", con_materias)
    print("Archivo generado:", SALIDA)

    print("\n10 MATERIAS MÁS FRECUENTES:")
    for materia, cantidad in frecuencias.most_common(10):
        print(cantidad, "-", materia)


if __name__ == "__main__":
    main()
