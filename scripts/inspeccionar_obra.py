
import json
from urllib.request import Request, urlopen

BASE_URL = "https://openlibrary.org"

def obtener_json(clave):
    request = Request(
        BASE_URL + clave + ".json",
        headers={"User-Agent": "BibliotecaVirtualDeusto/1.0"}
    )

    with urlopen(request, timeout=30) as response:
        return json.load(response)


def main():
    # Obra vinculada a la primera edición consultada
    obra = obtener_json("/works/OL14903346W")

    print("TÍTULO:", obra.get("title"))

    print("\nMATERIAS:")
    for materia in obra.get("subjects", [])[:10]:
        print("-", materia)

    print("\nAUTORES:")
    for entrada in obra.get("authors", []):
        clave_autor = entrada.get("author", {}).get("key")

        if clave_autor:
            autor = obtener_json(clave_autor)
            print("-", autor.get("name", "Desconocido"))

    descripcion = obra.get("description", "")

    if isinstance(descripcion, dict):
        descripcion = descripcion.get("value", "")

    print("\nDESCRIPCIÓN:")
    print(descripcion[:500] if descripcion else "No disponible")


if __name__ == "__main__":
    main()
