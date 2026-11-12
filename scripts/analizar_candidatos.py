
import gzip
import json
import random
from collections import Counter
from pathlib import Path

ARCHIVO = (
    Path(__file__).resolve().parent.parent
    / "data"
    / "candidatos_1000_diversos.jsonl.gz"
)

with gzip.open(ARCHIVO, "rt", encoding="utf-8") as f:
    libros = [json.loads(linea) for linea in f]

print("TOTAL DE EDICIONES:", len(libros))

obras = {libro["openlibrary_work_key"] for libro in libros}
print("OBRAS DIFERENTES:", len(obras))

editoriales = Counter(
    libro["editorial"] for libro in libros
)

print("\n10 EDITORIALES MÁS FRECUENTES:")
for editorial, cantidad in editoriales.most_common(10):
    print(f"{cantidad:4} - {editorial}")

print("\n10 LIBROS DE MUESTRA:")
muestra = random.Random(42).sample(
    libros, min(10, len(libros))
)

for libro in muestra:
    print(f"\nTítulo: {libro['titulo']}")
    print(f"Editorial: {libro['editorial']}")
    print(f"ISBN: {libro['isbn']}")
    print(f"Obra: {libro['openlibrary_work_key']}")
