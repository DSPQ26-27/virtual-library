# Pruebas del frontend

## Inventario y auditoría

La aplicación ofrece registro, sesión, catálogo con búsqueda, categorías y vistas,
ejemplares, préstamos, renovaciones, devoluciones, reservas y gestión de libros y
ejemplares para personal. La ordenación del catálogo se solicita por título de
forma fija: actualmente no hay selector de ordenación. La ficha de ejemplares es
la vista de detalle disponible. El hook propio `useAuth` expone el contexto de
sesión; los demás efectos y estados viven en los componentes.

Antes de estos cambios había 128 pruebas Node, sin script npm. Todas simulan
`fetch` y comprueban lógica de servicios, no un backend real:

| Archivo en `tests/` | Cobertura principal |
| --- | --- |
| `auth.test.js` | Registro, validación, duplicado y red. |
| `session.test.js` | Sesión, login, logout, cookies y CSRF. |
| `books.test.js` | Consulta de catálogo, filtros, paginación, errores y cancelación. |
| `catalogAdmin.test.js` | Gestión de libros, validación y roles. |
| `ejemplares.test.js` | Gestión de ejemplares, estados y errores. |
| `loans.test.js` | Peticiones de préstamo, permisos y errores. |
| `myLoans.test.js` | Listado, historial y errores. |
| `renewLoan.test.js` | Renovaciones, CSRF, errores y respuestas inválidas. |
| `returnLoan.test.js` | Devolución, CSRF y resultado incierto. |
| `waitlist.test.js` | Alta, consulta y cancelación de reservas. |

Los módulos `catalog.browser.mjs`, `ejemplares.browser.mjs`,
`renewLoan.browser.mjs` y `waitlist.browser.mjs` son pruebas de interfaz en
Playwright con respuestas HTTP simuladas; ahora se ejecutan con `test:browser`.
`renewLoan.postgres.mjs` es un recorrido real anterior de renovación, pensado
para un entorno PostgreSQL preparado manualmente. No se ejecuta automáticamente
ni se confunde con las pruebas simuladas o con la nueva suite aislada.

Se conserva Node Test Runner para servicios. Vitest + Testing Library cubre
componentes React y el contexto de autenticación con jsdom y servicios simulados.
Playwright cubre tanto los escenarios simulados existentes como, por separado,
los recorridos reales. No se incorporó Jest, que duplicaría herramientas.

## Ejecución

Requisitos: Node.js compatible con las versiones del `package-lock.json`, Java
21, Maven, Docker en ejecución y acceso a la imagen `postgres:17.11`. Desde
`frontend`:

```powershell
npm ci
npx playwright install chromium
npm run test:unit
npm run test:components
npm run test:browser
npm run test:e2e
```

`test:browser` utiliza el puerto local 5179 y falla si está ocupado. Sus cuatro
escenarios interceptan la API y no son E2E. `test:e2e` crea un contenedor Docker
PostgreSQL nuevo con nombre, etiqueta y puerto aleatorios, sin volúmenes de
Compose; arranca Spring Boot y Vite en puertos locales, aplica Flyway y crea
datos reproducibles. Los tests usan Chromium, API y sesión reales sin interceptar
respuestas. El contenedor se elimina al finalizar; las imágenes quedan en caché.
El usuario administrador se crea mediante registro y se promueve solo dentro del
contenedor identificado y etiquetado por el ejecutor. No usa `biblioteca_db`.

Las trazas y capturas de fallos están bajo `test-results/e2e` o
`test-results/browser`. Cada ejecución de un grupo sustituye los resultados
anteriores de ese grupo. En Windows, Maven debe estar disponible en `PATH`.

## Límites conocidos

La interfaz deshabilita la tercera renovación y oculta el botón de préstamo
sin ejemplares; no expone una acción con la que provocar esos rechazos desde
la interfaz. La suite comprueba esos controles y las pruebas de servicios y
backend cubren respuestas de rechazo. No hay selector de orden para probarlo.
Las pruebas de componentes cubren una selección priorizada, no todas las
páginas ni todos los estados de red. Las pruebas E2E no demuestran concurrencia,
rendimiento ni ausencia de errores en otros navegadores.

## Sincronización corregida al editar libros

En una repetición, el selector parcial de «Autor» coincidió con el buscador
«Buscar por título o autor» antes de completar la navegación al formulario.
La prueba espera ahora la URL y el encabezado de edición y usa una etiqueta
que empieza por «Autor»; comprueba el valor, el JSON enviado y el catálogo. Tampoco empieza a
rellenar el alta antes de confirmar la navegación. No se modificó producción.
