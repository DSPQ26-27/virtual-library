
import gzip
import json
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent

ENTRADA = RAIZ / "data/candidatos_1000_con_obras.jsonl.gz"
AUTORES = RAIZ / "data/ol_dump_authors_2026-07-31.txt.gz"
SALIDA = RAIZ / "data/candidatos_1000_completos.jsonl.gz"


def main():
    with gzip.open(ENTRADA, "rt", encoding="utf-8") as archivo:
        libros = [json.loads(linea) for linea in archivo]

    claves_necesarias = {
        clave
        for libro in libros
        for clave in libro.get("autores_keys", [])
        if isinstance(clave, str)
    }

    print("Autores diferentes que buscamos:", len(claves_necesarias))

    nombres = {}

    with gzip.open(AUTORES, "rb") as archivo:
        for numero, linea in enumerate(archivo, 1):
            columnas = linea.split(b"\t", 4)

            if len(columnas) != 5:
                continue

            clave = columnas[1].decode("utf-8", errors="replace")

            if clave not in claves_necesarias:
                continue

            try:
                autor = json.loads(columnas[4])
            except (ValueError, UnicodeDecodeError):
                continue

            nombre = autor.get("name")

            if isinstance(nombre, str) and nombre.strip():
                nombres[clave] = nombre.strip()

            if len(nombres) == len(claves_necesarias):
                break

    completos = 0
    sin_autor = 0
    con_materias = 0

    with gzip.open(SALIDA, "wt", encoding="utf-8") as salida:
        for libro in libros:
            autores = []

            for clave in libro.get("autores_keys", []):
                nombre = nombres.get(clave)

                if nombre and nombre not in autores:
                    autores.append(nombre)

            # Respetamos el límite VARCHAR(255) de PostgreSQL.
            autor_principal = autores[0][:255] if autores else None

            libro["autor"] = autor_principal
            libro["autores_nombres"] = autores

            if autor_principal:
                completos += 1
            else:
                sin_autor += 1

            if libro.get("materias"):
                con_materias += 1

            salida.write(
                json.dumps(libro, ensure_ascii=False) + "\n"
            )

    print("\nRESULTADOS")
    print("Autores encontrados:", len(nombres))
    print("Libros con autor:", completos)
    print("Libros sin autor:", sin_autor)
    print("Libros con materias:", con_materias)
    print("Archivo generado:", SALIDA)

    print("\nEJEMPLOS:")
    for libro in libros[:10]:
        print(
            f"{libro['titulo'][:55]} | "
            f"Autor: {libro['autor'] or 'DESCONOCIDO'}"
        )


if __name__ == "__main__":
    main()
