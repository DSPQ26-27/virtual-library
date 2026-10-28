# Contrato del catálogo

Propuesta del frontend #29.
Pendiente de coordinación e implementación con #28.

## Petición

GET /api/libros
Accept: application/json

El endpoint requiere una sesión autenticada.
El frontend envía la cookie de sesión.

## Respuesta correcta

HTTP 200 con un array JSON, sin envoltorio de paginación.

Ejemplo:

```json
[
  {
    "id": 1,
    "titulo": "Don Quijote de la Mancha",
    "autor": "Miguel de Cervantes",
    "disponible": true
  }
]
```

Campos:
- id: entero positivo, único dentro del listado.
- titulo: texto no vacío.
- autor: texto no vacío.
- disponible: booleano.

disponible será true si existe al menos un ejemplar activo
que pueda prestarse. Si no existe ninguno, será false.

Los campos adicionales no son obligatorios para este frontend.

## Catálogo vacío

HTTP 200 con [].

No utilizar HTTP 204 para representar el catálogo vacío.

## Errores

- HTTP 401: no hay sesión o ha caducado.
- HTTP 403: usuario autenticado sin permiso.
- HTTP 5xx: fallo del servidor.

No redirigir a una página HTML de login.
Los errores no deben devolverse como HTTP 200 ni como una lista vacía.

## Frontend

- Muestra carga durante la petición.
- Muestra título, autor y disponibilidad.
- Gestiona catálogo vacío y errores.
- Permite actualizar o reintentar.
- Ante HTTP 401, elimina el estado de sesión y vuelve al login.
- El backend sigue siendo responsable de proteger los datos.

## Alcance

Listado inicial del Sprint 1.
No incluye búsqueda, filtros, préstamos ni administración.

## Estado

Pendiente de prueba integrada con la API y revisión del equipo.