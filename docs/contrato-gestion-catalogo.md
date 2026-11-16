# Contrato de gestión del catálogo

Implementado en el backend en la Issue #51 (US05 #5).
Lo usará la interfaz de administración de la Issue #52.

## Seguridad

- Requiere sesión. Sin sesión: HTTP 401.
- POST, PUT y DELETE: solo LIBRARIAN y ADMIN. Un estudiante recibe HTTP 403.
- GET /api/libros/{id}: cualquier usuario con sesión (STUDENT, LIBRARIAN o ADMIN).
- POST, PUT y DELETE exigen el token CSRF (GET /api/auth/csrf) en la
  cabecera X-CSRF-TOKEN. Sin token válido: HTTP 403.

## Endpoints

| Petición | Qué hace | Éxito |
|---|---|---|
| GET /api/libros/{id} | Ficha completa de un libro, también si está retirado | 200 |
| POST /api/libros | Crear un libro | 201 + cabecera Location |
| PUT /api/libros/{id} | Editar todos los datos de un libro | 200 |
| DELETE /api/libros/{id} | Retirar un libro (retirada lógica) | 204 sin cuerpo |

## Datos del libro (POST y PUT)

```json
{
  "isbn": "978-84-376-0494-7",
  "titulo": "Don Quijote de la Mancha",
  "autor": "Miguel de Cervantes",
  "editorial": "Cátedra",
  "categoria": "Clásicos",
  "anioPublicacion": 1605,
  "descripcion": "Texto libre.",
  "portadaUrl": "https://covers.example.com/quijote.jpg"
}
```

| Campo | Obligatorio | Reglas |
|---|---|---|
| titulo | Sí | Máximo 255 caracteres |
| autor | Sí | Máximo 255 caracteres |
| isbn | No | 10 o 13 dígitos, con guiones o espacios opcionales; el ISBN-10 puede acabar en X. Único |
| editorial | No | Máximo 150 caracteres |
| categoria | No | Máximo 100 caracteres |
| anioPublicacion | No | Entre 0 y 2100 |
| descripcion | No | Máximo 5000 caracteres |
| portadaUrl | No | URL http o https, máximo 1000 caracteres |

- Se recortan los espacios exteriores. Un campo opcional vacío se guarda como null.
- PUT sustituye todos los datos editables: hay que enviar el libro completo.
- No se pueden modificar id, origen (MANUAL u OPEN_LIBRARY), las claves de
  Open Library ni el estado de retirada.
- El ISBN se compara solo por sus dígitos: 978-84-376-0494-7 y 9788437604947
  son el mismo libro. Al editar, el propio ISBN del libro no cuenta como duplicado.
- Los libros creados por esta API tienen origen MANUAL. Los importados de
  Open Library también se pueden editar y conservan su origen.

## Respuesta (GET, POST y PUT)

```json
{
  "id": 1,
  "isbn": "978-84-376-0494-7",
  "titulo": "Don Quijote de la Mancha",
  "autor": "Miguel de Cervantes",
  "editorial": "Cátedra",
  "categoria": "Clásicos",
  "anioPublicacion": 1605,
  "descripcion": "Texto libre.",
  "portadaUrl": "https://covers.example.com/quijote.jpg",
  "origen": "MANUAL",
  "retirado": false
}
```

POST responde además con la cabecera Location: /api/libros/{id}.

## Errores

Mismo formato que el registro: message y, si procede, un mensaje por campo.

HTTP 400, datos no válidos:
{"message":"Revisa los datos del libro.","errors":{"titulo":"El título es obligatorio.","isbn":"ISBN no válido: debe tener 10 o 13 dígitos."}}

HTTP 400, JSON mal formado o id no numérico:
{"message":"Revisa los datos del libro.","errors":{}}

HTTP 404, el libro no existe:
{"message":"El libro no existe.","errors":{}}

HTTP 409, ISBN ya usado por otro libro:
{"message":"Ya existe un libro con ese ISBN.","errors":{"isbn":"Este ISBN ya está registrado en otro libro."}}

Además: 401 sin sesión y 403 sin permiso o sin token CSRF, como en el resto de la API.

## Retirada lógica

- DELETE /api/libros/{id} no borra nada de la base de datos: marca el libro
  con retirado = true y guarda fecha_retirada (migración V7).
- Se conservan el libro, sus ejemplares y, cuando exista (Sprint 3), su
  historial de préstamos: ninguna operación de gestión ejecuta DELETE en SQL.
- Un libro retirado deja de aparecer en GET /api/libros (todas las vistas y
  totales) y en GET /api/libros/categorias.
- GET /api/libros/{id} lo sigue devolviendo con "retirado": true.
- Retirar un libro ya retirado responde 204 y conserva la fecha original.
- La reactivación de libros retirados queda fuera del alcance de #51.

## Pruebas

LibroGestionIntegrationTests (16 tests): creación por bibliotecario y
administrador, permisos (403 estudiante, 401 sin sesión, 403 sin CSRF),
validaciones por campo, opcionales vacíos, ISBN duplicado con otro formato,
edición, ISBN propio e ISBN de otro libro, 404, id no numérico, retirada
lógica con conservación de ejemplares, desaparición del catálogo y de las
categorías, y retirada repetida.

mvnw test: 74 tests, 0 fallos.

## Estado

Backend implementado y probado en #51.
Pendiente: interfaz de administración (#52) y validación completa (#53).
