# Validación de la lista de espera FIFO — tarea #69

## Resultado de la validación

Validación ejecutada el 18 de noviembre de 2026.

| Comprobación | Resultado |
|---|---|
| Suite del backend en H2 | 186 pruebas, cero fallos y errores, una omitida |
| Lista de espera en PostgreSQL | 21 pruebas, cero fallos, errores y omisiones |
| Servicios del frontend | 128 pruebas superadas |
| Lint y compilación de Vite | Correctos |
| Interfaz en Edge headless con API simulada | Correcta, sin errores JavaScript |

La omisión en H2 corresponde a una prueba existente de DevolucionServiceTests
que solo se ejecuta si detecta PostgreSQL. Las 21 pruebas ejecutadas en PostgreSQL
son AsignacionFifoTests, ConcurrenciaEsperaTests y SolicitudEsperaIntegrationTests.

Se creó una base temporal independiente para la validación PostgreSQL. Flyway
aplicó las migraciones reales, incluida la restricción de un préstamo activo por
ejemplar y la coordinación V13. La base temporal se eliminó al terminar.

## Escenarios y evidencia

| Escenario | Prueba |
|---|---|
| El primer estudiante recibe el ejemplar y el segundo espera | asignaPorOrdenYConservaHistorialConPrestamoDe30Dias |
| La fecha tiene prioridad aunque el id sea mayor | laFechaTienePrioridadSobreElIdentificador |
| Misma fecha, desempate por id y omisión de canceladas | desempataPorIdYOmiteSolicitudesCanceladas |
| Cuenta inactiva, rol cambiado, vencimientos o cinco préstamos | saltaNoElegiblesConservandoSuSolicitud |
| Nadie elegible, ejemplar disponible y solicitudes pendientes | siNadieEsElegibleLiberaElEjemplarYConservaLaEspera |
| Libro retirado | libroRetiradoNoGeneraAsignaciones |
| Dos libros devueltos hacia un estudiante con cuatro préstamos | dosDevolucionesSimultaneasNoSuperanCincoPrestamos |
| Dos ejemplares devueltos, dos estudiantes distintos | dosEjemplaresDevueltosSeAsignanAEstudiantesDistintos |
| La misma devolución enviada dos veces | dosPeticionesDeLaMismaDevolucionSoloAsignanUnaVez |
| Cancelación al mismo tiempo que devolución | cancelacionYDevolucionMantienenUnResultadoConsistente |
| Dos altas simultáneas del mismo estudiante | dosPeticionesSimultaneasSoloCreanUnaSolicitud |

SolicitudEsperaIntegrationTests cubre también validación de datos, autenticación,
roles, CSRF, privacidad de las consultas, cancelación propia, historial y rechazo
de cancelaciones de solicitudes ya asignadas.

## Cómo funcionan las pruebas de concurrencia

Cada prueba crea datos propios y confirmados antes de iniciar los hilos. Dos
peticiones llaman a los servicios transaccionales desde hilos distintos. Una
barrera espera a que ambos estén preparados y después los deja comenzar.

Se espera a ambos resultados con un tiempo máximo. Se consultan los estados
guardados y se comprueba que un ejemplar no tenga dos préstamos activos. Los
datos propios se eliminan en finally, incluso si falla una comprobación.

Si cancelación y devolución coinciden, se aceptan los dos órdenes posibles.
Si gana la cancelación, el siguiente estudiante recibe el préstamo. Si gana la
devolución, la primera solicitud queda ASIGNADA y cancelar devuelve conflicto.
Se verifica que las solicitudes y los préstamos coincidan con el resultado.

## Cómo repetir las comprobaciones

Desde backend, ejecutar `.\mvnw.cmd test` para la suite completa en H2.
Para ejecutar solo la lista de espera se puede usar
`.\mvnw.cmd "-Dtest=AsignacionFifoTests,ConcurrenciaEsperaTests,SolicitudEsperaIntegrationTests" test`.

Para repetirlas en PostgreSQL, crear una base vacía de pruebas y configurar
SPRING_DATASOURCE_URL con su URL JDBC, SPRING_DATASOURCE_USERNAME,
SPRING_DATASOURCE_PASSWORD y SPRING_DATASOURCE_DRIVER_CLASS_NAME con
org.postgresql.Driver, antes de ejecutar las tres clases anteriores.
Usar una base independiente de biblioteca_db para que Flyway y los datos de
prueba se mantengan aislados del catálogo local.

Desde frontend, ejecutar `node --test tests/*.test.js`,
`node node_modules/oxlint/bin/oxlint` y `node node_modules/vite/bin/vite.js build`.
waitlist.browser.mjs comprueba alta, duplicado, cancelación, enlace de asignadas,
recuperación de errores y lista vacía con respuestas HTTP simuladas.

## Decisiones y límites

Se valida la regla implementada en #67: saltar estudiantes no elegibles
conservando su solicitud y su antigüedad. El equipo debe confirmar esa decisión.
La coordinación serializa las operaciones de préstamo. Estas pruebas verifican
consistencia en escenarios concretos, y no miden la capacidad bajo carga.
La prueba de navegador usa API simulada. PostgreSQL se valida por separado a
través de los servicios y la API de Spring en las pruebas de integración.

Esta rama parte de feature/68-lista-espera-react. Hasta integrar #68, el Pull
Request puede apuntar a esa rama para mostrar únicamente la validación de #69.
