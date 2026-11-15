# Frontend de Biblioteca Virtual

React + Vite. El catálogo US04 consume la API paginada de Spring Boot.

## Uso del catálogo

- Escribe título, autor o ISBN y pulsa Buscar o Enter. Escribir no hace peticiones.
- Categoría y vista se aplican inmediatamente y conservan la búsqueda confirmada.
- TODOS muestra registros bibliográficos, aunque no existan copias.
- BIBLIOTECA exige algún ejemplar activo, aunque esté prestado.
- DISPONIBLES exige algún ejemplar activo en estado DISPONIBLE.
- Cambiar filtros vuelve a la primera página. Limpiar filtros restaura TODOS.
- Anterior/Siguiente solicitan páginas de 20 libros al servidor, ordenadas por
  título e id. Actualizar/Reintentar conserva los filtros y la página.
- Un fallo de categorías no bloquea las demás búsquedas.

Endpoints autenticados: GET /api/libros y GET /api/libros/categorias.
Contrato, ejemplos, mediciones y límites: ../docs/contrato-catalogo.md.

## Desarrollo y pruebas

Desde frontend, usando las dependencias existentes:

```powershell
npm.cmd run dev
node --test tests/*.test.js
npm.cmd run lint
npm.cmd run build
```

En otros sistemas puede usarse npm en lugar de npm.cmd. Vite redirige /api al
backend en localhost:8080; para el uso real debe existir una sesión autenticada.
Los tests Node simulan fetch y no requieren Spring ni PostgreSQL.

### Escenario automatizado de navegador

El archivo tests/catalog.browser.mjs exporta una función que recibe una Page
compatible con Playwright. Utilizar una página/contexto dedicado en el entorno
que ya disponga de Playwright; no se ha añadido esta dependencia al proyecto.

Arrancar Vite en un terminal:

```powershell
npm.cmd run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Desde ese entorno de automatización, con page disponible:

```javascript
import validateCatalog from './tests/catalog.browser.mjs';
const resultado = await validateCatalog(page, 'http://127.0.0.1:5174');
console.log(resultado);
```

El escenario intercepta /api, simula la sesión y las respuestas paginadas y no
usa la base de datos. Falla mediante excepciones si una comprobación no se
cumple. Retira las rutas y termina en about:blank. No se ejecuta con node --test;
requiere el navegador y la Page proporcionados por el entorno externo.

Cubre filtros combinados, borrador sin aplicar, Enter/Buscar, limpieza, páginas,
reintentos, fallo independiente de categorías, carga, respuesta obsoleta,
401/403, foco con teclado y anchos 1280/768/320 px.

## Backend

Desde backend:

```powershell
mvn test
```

En Windows puede usarse mvnw.cmd test; en Unix, ./mvnw test. Las clases de prueba
usan @ActiveProfiles("test") y application-test.properties: H2 en memoria,
independiente de PostgreSQL. Flyway prepara ese esquema temporal de pruebas.
No ejecutar spring-boot:run para probar migraciones sobre la base local.

## Estado de validación y pendientes

El 16/11/2026 pasan 50 tests Node y 58 tests Maven; build y lint correctos.
El escenario de navegador pasó con HTTP simulado. Se midieron SELECT en la
PostgreSQL de pruebas autorizada con 100.980 libros, sin modificar datos.
Esto no acredita el recorrido completo con una sesión real de Spring.

Antes de cerrar US04: revisar el flujo autenticado con backend real, lector de
pantalla, zoom nativo al 200 %, objetivos de latencia y carga concurrente.
El catálogo devuelve disponibilidad, no una garantía de préstamo posterior.

### Intento de integración real #49

El 16/11/2026 se arrancó una instancia de validación contra PostgreSQL real con
100.980 libros: Spring en 8081 (Flyway desactivado, esquema en validate,
conexiones Hikari de lectura) y Vite 5174 con proxy temporal, sin cambiar archivos
de configuración. Sin mocks, se verificó health 200, catálogo/categorías 401 sin
sesión y login 403 sin CSRF. Este 403 no prueba la denegación de catálogo por rol.

El usuario confirmó login manual, pero la automatización no pudo acceder a su
Chrome: la herramienta rechazó el acceso y el host de pestañas no estaba
disponible. No se copiaron credenciales o cookies. Las búsquedas autenticadas y
las siete series de tiempos HTTP están pendientes; no se sustituyen por tests
simulados ni por los tiempos SQL anteriores. Matriz completa y procedimiento de
medición pendiente en ../docs/contrato-catalogo.md. #49 no se considera completada.
