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

## Gestión del catálogo (#52)

Solo para bibliotecarios y administradores (roles LIBRARIAN y ADMIN). Para los
estudiantes el catálogo no cambia. Usa la API de la Issue #51; contrato en
../docs/contrato-gestion-catalogo.md.

- Menú y cabecera del catálogo: enlace "Añadir libro" → /admin/libros/nuevo.
- Cada tarjeta del catálogo: "Editar" → /admin/libros/:id/editar y "Retirar".
- Retirar pide confirmación dentro de la tarjeta. Es una retirada lógica: el libro
  desaparece del catálogo, pero se conserva en la base de datos con sus ejemplares.
- El formulario valida en el navegador con las mismas reglas y mensajes que el
  backend y muestra los errores del servidor (400, 409 ISBN duplicado) junto a
  cada campo. Ante 401 expira la sesión.

Archivos:

- src/services/catalogAdmin.js: getBook, createBook, updateBook y retireBook;
  token CSRF en POST, PUT y DELETE; errores como CatalogAdminError.
- src/services/bookValidation.js: validación y conversión formulario ↔ API.
- src/auth/roles.js: canManageCatalog(user).
- src/components/StaffRoute.jsx: ProtectedRoute + rol de personal; si no, "No tienes permiso".
- src/components/BookForm.jsx y BookForm.css: formulario de crear y editar,
  reutilizando los estilos de RegisterForm.css.
- src/services/session.js exporta csrfToken() para reutilizarlo.

Ocultar los botones no es la seguridad: el backend responde 403 a los
estudiantes y exige el token CSRF (SecurityConfig).

Para probarlo, un usuario registrado (rol ESTUDIANTE) se convierte en bibliotecario con:

```powershell
docker exec biblioteca-postgres psql -U biblioteca_user -d biblioteca_db -c "UPDATE usuarios SET rol = 'BIBLIOTECARIO' WHERE email = 'correo@example.com';"
```

Después hay que cerrar sesión y volver a entrar para que la sesión tenga el rol nuevo.

Validación del 16/11/2026: 69 tests Node (19 nuevos en tests/catalogAdmin.test.js),
lint sin avisos y build correctos. Prueba manual con backend y PostgreSQL reales:
validación en el navegador, ISBN duplicado (409), alta (201), aparición en el
catálogo, edición (200), retirada con confirmación (204), libro conservado en la
base de datos con retirado = true y, como estudiante, sin botones de gestión y
"No tienes permiso" en /admin/libros/nuevo.

## Gestión de ejemplares (US06)

En cada tarjeta del catálogo, **Ejemplares** abre `/libros/:id/ejemplares`.
Los usuarios con sesión pueden ver el identificador, código y estado de cada
ejemplar, el total registrado (incluidos los retirados) y los disponibles.

Los bibliotecarios y administradores pueden registrar y editar con un único
formulario, y retirar después de confirmar. La retirada conserva el registro.
La API rechaza retirar ejemplares prestados o con préstamos activos, y marcar
como disponible un ejemplar con un préstamo activo. La pantalla muestra el
motivo y conserva los datos. Los préstamos activos no cuentan como disponibles.
La API exige esos roles y token CSRF para las escrituras. Tras cada cambio se
actualizan la lista y los contadores.

Implementación, reglas y endpoints: [gestión de ejemplares](../docs/gestion-ejemplares.md).
Pruebas completas con React, Spring y PostgreSQL:
[validación de ejemplares](../docs/validacion-gestion-ejemplares.md).

## Préstamos (#59)

Solo para estudiantes (rol STUDENT). Usa POST /api/prestamos de la Issue #58.

- Cada tarjeta de un libro disponible muestra "Pedir préstamo". Bibliotecarios y
  administradores no ven este botón (el backend también les responde 403).
- Pide confirmación antes de enviar. Mientras se envía, los botones se desactivan
  para no crear solicitudes duplicadas.
- El backend elige automáticamente un ejemplar libre. Al confirmarse se muestra el
  ejemplar y la fecha de devolución (30 días) y se recarga el catálogo para
  actualizar la disponibilidad.
- Los rechazos muestran el mensaje del backend: límite de 5 préstamos, préstamos
  vencidos sin devolver o sin ejemplares disponibles (en este caso también se
  recarga el catálogo). Ante 401 expira la sesión.

Archivos:

- src/services/loans.js: requestLoan, LoanError (con status y code) y formatDueDate.
- src/components/LoanButton.jsx: botón, confirmación y envío.
- src/components/CatalogPage.jsx: muestra el botón y el resultado.
- src/auth/roles.js: isStudent.
- tests/loans.test.js: 9 pruebas del servicio, de los roles y del formato de fecha.

Probado a mano con PostgreSQL: un estudiante pide «Don Quijote de la Mancha»,
recibe EJ-0001 con vencimiento a 30 días y el libro pasa a "Sin ejemplares
disponibles"; un bibliotecario no ve el botón.

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
