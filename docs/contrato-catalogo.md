# Contrato del catálogo

Propuesta del frontend #29. Implementado en el backend en #28 y ampliado
en #47 (US04) con búsqueda, filtros y paginación.

## Petición

GET /api/libros
Accept: application/json

El endpoint requiere una sesión autenticada (STUDENT, LIBRARIAN o ADMIN).
El frontend envía la cookie de sesión.

Parámetros opcionales, combinables entre sí:

- q: texto que debe contener el título, el autor o el ISBN
  (sin distinguir mayúsculas). Los caracteres % y _ se tratan como texto.
- categoria: categoría exacta, sin distinguir mayúsculas.
- vista: TODOS (por defecto), BIBLIOTECA o DISPONIBLES.
  - TODOS: todo el catálogo, tenga o no ejemplares la biblioteca.
  - BIBLIOTECA: libros con al menos un ejemplar activo.
  - DISPONIBLES: libros con al menos un ejemplar activo y DISPONIBLE.
- page: número de página, empezando en 0 (por defecto 0).
- size: libros por página (por defecto 20, máximo 100).
- sort: campo y dirección, por ejemplo titulo,asc o anioPublicacion,desc.
  Campos admitidos: titulo, autor, anioPublicacion, categoria, editorial.
  Un campo no admitido se ignora. Por defecto, titulo,asc.
  Siempre se desempata por id para que la paginación sea estable.

Ejemplo:

GET /api/libros?q=cervantes&categoria=Novela&vista=DISPONIBLES&page=0&size=20&sort=titulo,asc

## Respuesta correcta

HTTP 200 con una página del catálogo:

```json
{
  "content": [
    {
      "id": 1,
      "titulo": "Don Quijote de la Mancha",
      "autor": "Miguel de Cervantes",
      "disponible": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Campos de cada libro:
- id: entero positivo, único dentro del listado.
- titulo: texto no vacío.
- autor: texto no vacío.
- disponible: booleano.

Campos de la página:
- content: libros de la página solicitada.
- page: número de página devuelta (empezando en 0).
- size: tamaño de página aplicado (como máximo 100).
- totalElements: total de libros que cumplen los filtros.
- totalPages: total de páginas con ese tamaño.

disponible será true si existe al menos un ejemplar activo
que pueda prestarse. Si no existe ninguno, será false.
Se calcula con los ejemplares reales de cada libro de la página.

## Catálogo vacío o sin resultados

HTTP 200 con content vacío, totalElements 0 y totalPages 0.

No utilizar HTTP 204 para representar el catálogo vacío.

## Errores

- HTTP 400: parámetro no válido, por ejemplo vista=OTRA.
  {"message":"Parámetro de búsqueda no válido."}
- HTTP 401: no hay sesión o ha caducado.
- HTTP 403: usuario autenticado sin permiso.
- HTTP 5xx: fallo del servidor.

No redirigir a una página HTML de login.
Los errores no deben devolverse como HTTP 200 ni como una página vacía.

## Rendimiento

- PostgreSQL devuelve solo la página solicitada (LIMIT/OFFSET) y el total
  (COUNT); nunca se carga el catálogo completo en memoria.
- La disponibilidad se calcula con una sola consulta para los libros de la página.
- La migración V6 (solo PostgreSQL, db/vendor/postgresql) crea índices
  pg_trgm para la búsqueda por texto y los índices de categoría, orden
  y ejemplares. Ver modelo-catalogo.md.

## Frontend

- Muestra carga durante la petición.
- Muestra título, autor y disponibilidad.
- Gestiona catálogo vacío y errores.
- Permite actualizar o reintentar.
- Ante HTTP 401, elimina el estado de sesión y vuelve al login.
- El backend sigue siendo responsable de proteger los datos.
- Debe adaptarse al formato paginado, con buscador, filtros y
  navegación entre páginas, en la #48.

## Estado

Backend implementado y probado en #47 (LibroControllerIntegrationTests).
Pendiente: pantalla del frontend (#48) y validación con 100.000 libros (#49).
