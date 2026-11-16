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
- categoria: categoría exacta, sin distinguir mayúsculas ni espacios exteriores
  en el parámetro o en el valor almacenado.
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

### Categorías disponibles (US04)

`GET /api/libros/categorias` devuelve un array JSON de categorías de
`libros.categoria`, sin consultar materias ni cargar entidades Libro.
Requiere la misma sesión y roles que el listado de libros.

Se excluyen nulos, cadenas vacías y valores compuestos solo por espacios.
Se recortan espacios exteriores y se agrupa sin distinguir mayúsculas.
Por cada grupo se devuelve la menor grafía existente según la ordenación
de la base de datos; no se fuerza una capitalización nueva. La lista se
ordena por el nombre en minúsculas, según la colación de la base de datos.

Ejemplo: `["Ciencias", "Derecho", "Economía", "Historia", "Informática", "Literatura"]`.
Sin categorías válidas se devuelve `[]` con HTTP 200. La lista es global,
independiente de los filtros y de la página de libros consultada.

### Página de libros

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

Los libros retirados (#51) no aparecen en el listado, en las vistas, en los
totales ni en GET /api/libros/categorias. Ver contrato-gestion-catalogo.md.

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
- El texto escrito se aplica solo al pulsar Buscar o Enter. Cambiar categoría
  o vista conserva el texto aplicado, aunque haya otro texto aún sin confirmar.
- Los filtros se combinan con AND; q coincide con título, autor o ISBN mediante OR.
- Cualquier cambio de filtro vuelve a page=0. Limpiar filtros restablece texto,
  categoría y vista TODOS. Actualizar/reintentar conserva la consulta y la página.
- Solicita size=20 y sort=titulo,asc. El servidor desempata por id; la pantalla
  muestra las páginas desde 1. No descarga el catálogo para filtrar en memoria.
- Las categorías son globales. Su fallo tiene un reintento separado y no impide
  buscar por texto o cambiar de vista.
- Cancela peticiones obsoletas y evita aplicar sus resultados o errores.
- 401 vigente expira la sesión; 403 muestra el error sin cerrar sesión.
- Las tarjetas solo muestran id como clave, título, autor y disponibilidad;
  LibroResponse no incluye categoría, portada ni número de ejemplares.

## Estado

Implementación local de US04 preparada para revisión, sin commit ni cierre de la historia.
Validación del 16/11/2026:

- 50 tests Node del frontend pasan; build y lint correctos.
- 58 tests Maven pasan, con Spring/JPA y H2 en memoria. Flyway prepara únicamente
  esa base efímera; las pruebas no conectan con PostgreSQL local.
- Escenario de navegador reutilizable: frontend/tests/catalog.browser.mjs.
  Comprueba HTTP simulado, filtros, páginas, errores, carrera con 401 obsoleto,
  teclado y ausencia de desbordamiento a 1280/768/320 px. No es una prueba
  completa React–Spring–PostgreSQL. Ver frontend/README.md para ejecutarlo.
- Regresión corregida: categorías publicadas sin espacios ahora encuentran
  libros cuya categoría almacenada tiene espacios exteriores.
- Pruebas añadidas para títulos iguales, varios ejemplares y ausencia de N+1:
  cinco libros se consultan con dos o tres sentencias SQL como máximo.

### Mediciones PostgreSQL de solo lectura

Base local de desarrollo/pruebas autorizada, 100.980 libros, V1–V6 aplicadas.
Consultas SELECT representativas con EXPLAIN (ANALYZE, BUFFERS, TIMING OFF),
dentro de BEGIN READ ONLY, con statement_timeout=10s y sin cambios de esquema.
Una ejecución local por consulta; tiempos de ejecución del motor, sin sumar
planificación, HTTP, serialización o renderizado. Las páginas medidas proyectan
id/título/autor; el repositorio JPA carga además otros campos de Libro.

| Consulta | Tiempo observado | Plan relevante |
|---|---:|---|
| Primera página, 20 libros | 0,124 ms | Índice titulo,id |
| Texto python en título/autor/ISBN | 5,015 ms | BitmapOr con los tres índices GIN |
| Categoría normalizada Informática, 20 libros | 27,441 ms | Índice de título y filtro adicional |
| Conteo de libros disponibles | 0,054 ms | EXISTS; solo cuatro ejemplares en este escenario |
| Página con OFFSET 100000 | 272,695 ms | Recorrido secuencial y ordenación en disco |
| Categorías agrupadas | 41,641 ms | Agregación e índice de categoría |

### Limitaciones y validación pendiente

- lower(trim(categoria)) corrige la coherencia funcional, pero no coincide con
  el índice V6 de lower(categoria). No se han modificado migraciones ni índices.
  Evaluar normalización de datos o un nuevo índice en una tarea posterior si
  las mediciones del entorno objetivo lo requieren.
- OFFSET profundo y COUNT con filtros amplios pueden ser costosos. No existe
  todavía un objetivo de latencia acordado ni una prueba de concurrencia.
- La disponibilidad medida con cuatro ejemplares no representa una biblioteca
  con cientos de miles de copias. Disponible es una observación, no una reserva.
- El desempate por id no impide que inserciones o borrados cambien las páginas.
- La búsqueda no divide q en palabras y no elimina diferencias de acentos.
- En desarrollo, StrictMode puede iniciar y cancelar una petición inicial adicional.
- Pendientes: recorrido autenticado real React–Spring–PostgreSQL, lector de
  pantalla y zoom nativo al 200 %. La fase visual comprobó ampliación emulada.
- No declarar US04 terminada hasta revisar estos puntos y los criterios de la
  Issue #4 con el equipo. No se ha validado su contenido remoto en esta sesión.

## Intento de validación real de la Issue #49 (16/11/2026)

Se comprobó main y el estado de Git antes de comenzar; se conservaron los cambios
locales. La API de GitHub devolvió 404 al consultar #49. La matriz siguiente usa
los criterios enumerados por el solicitante; no acredita el texto remoto de la issue.

### Entorno y medidas de protección

- PostgreSQL local: biblioteca_db, contenedor biblioteca-postgres, 100.980 libros.
  Verificación mediante BEGIN READ ONLY; no se cambiaron datos ni esquema.
- Hay una instancia ajena en 8080: no se detuvo ni modificó.
- Backend de validación propio en 8081, conectado a PostgreSQL real, con
  spring.flyway.enabled=false, ddl-auto=validate y Hikari read-only=true.
  Se utilizó DB_PASSWORD ya configurado, sin mostrarlo ni documentar su valor.
- Vite en 127.0.0.1:5174, con proxy temporal en memoria hacia 8081; no se
  modificó vite.config.js. No se instalaron interceptores ni respuestas simuladas.
- No se crearon usuarios y no se alteraron usuarios, libros o ejemplares.

### Evidencia obtenida y bloqueo

| Comprobación real | Resultado |
|---|---|
| Arranque de Spring contra PostgreSQL | Correcto; conexión JDBC PostgreSQL y aplicación iniciada |
| GET /api/health mediante proxy Vite | HTTP 200 |
| GET /api/libros?page=0&size=20 sin sesión | HTTP 401 |
| GET /api/libros/categorias sin sesión | HTTP 401 |
| POST /api/auth/login sin CSRF | HTTP 403; comprueba CSRF, no permisos de catálogo |

No se encontraron credenciales de aplicación configuradas: .env solo contenía
DB_PASSWORD. El usuario confirmó haber iniciado sesión manualmente en su Chrome.
El navegador de automatización independiente seguía recibiendo 401 en /api/auth/me.
Al intentar acceder al Chrome autenticado, la herramienta devolvió
"Computer Use was not approved to use Google Chrome", incluso después de que
el usuario confirmase haber habilitado el permiso y solicitase otro intento.
El inventario de navegadores también indicó que el host de acceso no estaba disponible.
No se copiaron cookies, tokens ni contraseñas y no se eludió ese bloqueo.

### Matriz de criterios de integración real

| Criterio solicitado para #49 | Estado | Evidencia o comprobación restante |
|---|---|---|
| Inicio de sesión real | Parcialmente cumplido | Confirmado por el usuario; no verificado por la automatización |
| Búsqueda por título | Pendiente | Pruebas previas H2/simuladas; falta recorrido autenticado real |
| Búsqueda por autor | Pendiente | Igual limitación |
| Filtro de categoría | Pendiente | Igual limitación |
| Vistas TODOS, BIBLIOTECA y DISPONIBLES | Pendiente | Igual limitación |
| Filtros combinados | Pendiente | Igual limitación |
| Navegación entre páginas | Pendiente | Igual limitación |
| Limpieza de filtros | Pendiente | Igual limitación |
| Búsqueda sin resultados | Pendiente | Igual limitación |
| Control 401/403 y permisos | Parcialmente cumplido | 401 anónimo y 403 CSRF reales; falta 403 por rol y sesión vigente |
| Solicitar 20 libros sin descargar el catálogo | Parcialmente cumplido | Código y pruebas previas; falta respuesta autenticada real en navegador |
| Filtros/paginación SQL y orden estable | Parcialmente cumplido | SQL real previo e implementación verificada; falta recorrido completo |
| Reinicio de página al cambiar filtros | Parcialmente cumplido | Navegador simulado previo; falta comprobación real |
| Rendimiento HTTP repetido | Pendiente | Sin sesión accesible no pueden medirse respuestas 200 del catálogo |
| Documentación y limitaciones | Cumplido | Este registro distingue pruebas reales, simuladas y bloqueadas |

### Mediciones HTTP solicitadas

| Caso | Primera ejecución | Ejecuciones posteriores |
|---|---|---|
| Primera página | No medida | No medidas |
| Título | No medida | No medidas |
| Autor | No medida | No medidas |
| Categoría | No medida | No medidas |
| Combinada | No medida | No medidas |
| DISPONIBLES | No medida | No medidas |
| Página avanzada | No medida | No medidas |

No se midió la latencia de respuestas 401 como sustituto de búsquedas correctas.
Los tiempos de la sección PostgreSQL siguen siendo exclusivamente tiempos SQL.
Para completar: habilitar una sesión real accesible al navegador de validación,
realizar cada interacción y medir, por ejemplo, cinco GET secuenciales por caso
con performance.now() desde antes de fetch hasta terminar response.text().
Registrar la primera muestra separada y las cuatro posteriores, con estados HTTP,
tamaño de respuesta y resultado; una primera muestra no equivale a caché fría.

Conclusión: #49 no está completada. US04 puede revisarse o presentarse como PR
en borrador, pero no debe cerrarse ni declararse lista para fusionar con estas
comprobaciones pendientes. No se cambió código funcional en este intento.
