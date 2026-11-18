# Validación completa de renovaciones (US09)

Comprobación realizada el **18/11/2026** sobre la implementación de US09.
El recorrido funcional usa React, Spring Boot y PostgreSQL reales, con sesiones
y CSRF. No intercepta ni simula las respuestas HTTP.

## Resultados

| Comprobación | Resultado |
|---|---|
| Suite completa del backend en H2 | 165 pruebas, 0 fallos, 0 errores y 1 omitida (exclusiva de PostgreSQL) |
| Préstamos, renovaciones, devoluciones y ejemplares en PostgreSQL | 88 pruebas, todas correctas |
| Servicios del frontend | 123 pruebas, todas correctas |
| Compilación del backend y del frontend | Correcta |
| Recorrido funcional en Chrome con React, Spring y PostgreSQL | 14 grupos de comprobaciones, todos correctos |

Las pruebas de PostgreSQL incluyen los 22 casos de `RenovacionIntegrationTests`,
el rollback conjunto del contador y el vencimiento y la renovación simultánea
con una devolución.

## Criterios comprobados en el recorrido real

| Criterio | Evidencia |
|---|---|
| Renovación de un préstamo activo | Login desde React, préstamo por la API y renovación desde «Mis préstamos»: HTTP 200 |
| Ampliación de 30 días en cada renovación | Consulta SQL: duración inicial 30 días; tras renovar, 60 y 90 días desde la fecha original |
| Primera y segunda permitidas; tercera rechazada | Contadores 1 y 2, botón desactivado y tercer POST rechazado con 409 `LIMITE_RENOVACIONES` |
| Préstamos vencidos | Mensaje en React y 409 `PRESTAMO_VENCIDO`, sin modificar la fila |
| Préstamos devueltos o cancelados | 409 `PRESTAMO_NO_ACTIVO`, sin modificar los datos |
| Estudiante bloqueado por otro préstamo vencido | Mensaje en React y 409 `ESTUDIANTE_BLOQUEADO` |
| Cuenta inactiva con sesión ya iniciada | 409 `ESTUDIANTE_BLOQUEADO` |
| Préstamo de otro estudiante | No aparece en el listado propio; POST rechazado con 404 `PRESTAMO_NO_ENCONTRADO` |
| Espera pendiente sin ejemplares libres | Mensaje en React y 409 `LISTA_ESPERA_PENDIENTE`; contador y vencimiento intactos |
| Espera pendiente con otra copia libre | Renovación desde React con HTTP 200, contador 1 y 30 días adicionales; la solicitud sigue pendiente |
| Solicitudes simultáneas | Tres POST concurrentes: dos respuestas 200 y una 409; PostgreSQL guarda contador 2 y duración de 90 días |
| Persistencia | Verificación SQL tras las operaciones y consulta desde una sesión nueva; React conserva el contador tras recargar |
| Confirmación y solicitudes duplicadas | Cancelar no envía el POST; dos clics en confirmar producen una única petición |

En cada rechazo se compara la fila de PostgreSQL antes y después. También se
comprueba que no aparecen errores de JavaScript durante el recorrido.

## Entorno y alcance

- Chrome sin interfaz, React servido por Vite en `127.0.0.1:5177` y Spring Boot
  en `127.0.0.1:8087`, conectados mediante el proxy de Vite.
- PostgreSQL **17.11**, contenedor temporal `biblioteca-us09-e2e`. Base funcional
  `biblioteca_us09_e2e` y otra base, `biblioteca_us09_tests`, para Maven.
- Flyway aplicó las migraciones hasta V12 y Hibernate validó el esquema.
- Las cuentas se registran por la API. Un usuario de prueba recibe el rol de
  administrador mediante SQL para crear libros y ejemplares por sus APIs.
- Las fechas vencidas, la cancelación, la cuenta inactiva y las solicitudes de
  espera se preparan con SQL en la base temporal. **US10 aún no tiene API de
  reservas**: se valida su registro mínimo y la consulta usada al renovar; el
  alta de reservas desde una pantalla y la asignación FIFO quedan fuera.
- El escenario elimina sus registros al terminar. Se comprobó que no quedaron
  préstamos, solicitudes ni cuentas de prueba. Los servidores y el contenedor
  temporales se detuvieron. La base habitual `biblioteca_db` no se modificó.

No se ha realizado una prueba de carga ni se afirma ausencia total de
interbloqueos. La persistencia se verificó desde conexiones y sesiones nuevas;
no se simuló una caída de PostgreSQL.

## Cómo repetirlo

Las suites habituales se ejecutan con `.\mvnw.cmd test` desde `backend` y
`node --test tests/*.test.js` desde `frontend`. Las compilaciones se ejecutan
con `.\mvnw.cmd package` y `npm.cmd run build`, respectivamente.

Para el recorrido real, el escenario es
`frontend/tests/renewLoan.postgres.mjs`. Requiere Docker, Chrome y Playwright
del entorno; no añade dependencias al proyecto.

1. Crear una PostgreSQL temporal:

   ```powershell
   docker run --detach --rm --name biblioteca-us09-e2e --env POSTGRES_DB=biblioteca_us09_e2e --env POSTGRES_USER=us09_e2e --env POSTGRES_PASSWORD=us09_e2e_local --publish 127.0.0.1:5433:5432 postgres:17
   ```

2. En un terminal de `backend`, configurar esa base y arrancar Spring Boot en
   el puerto habitual 8080:

   ```powershell
   $env:SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5433/biblioteca_us09_e2e"
   $env:SPRING_DATASOURCE_USERNAME="us09_e2e"
   $env:SPRING_DATASOURCE_PASSWORD="us09_e2e_local"
   .\mvnw.cmd spring-boot:run
   ```

3. Arrancar el frontend en otro terminal con
   `npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort`. El proxy
   existente conecta con Spring Boot en 8080.

4. En otro terminal de `frontend`, indicar la ubicación de Playwright del
   entorno y ejecutar:

   ```powershell
   $env:US09_BASE_URL="http://127.0.0.1:5173"
   $env:PLAYWRIGHT_MODULE="file:///C:/ruta/al/entorno/node_modules/playwright/index.mjs"
   $env:CHROMIUM_EXECUTABLE="C:/Program Files/Google/Chrome/Application/chrome.exe"
   $env:US09_REPORT="../backend/target/us09-validacion-real.json"
   node tests/renewLoan.postgres.mjs
   ```

   El script usa exclusivamente la base `biblioteca_us09_e2e` y muestra cada
   comprobación superada. `US09_REPORT` permite guardar el resumen JSON.

5. Al terminar, detener ambos servidores con Ctrl+C y ejecutar
   `docker stop biblioteca-us09-e2e`.
