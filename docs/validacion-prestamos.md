# Validación de préstamos

Issue #60 (US07 #7). Comprueba que los préstamos funcionan entre React, Spring
Boot y PostgreSQL: modelo de datos (#57), endpoint y reglas (#58) e interfaz (#59).

## Resultado de los tests

| Parte | Comando | Resultado |
|---|---|---|
| Backend | `.\mvnw.cmd test` | 127 tests, 0 fallos |
| Frontend | `node --test tests/*.test.js` | 105 tests, 0 fallos |
| Frontend | `npm.cmd run lint` | 0 avisos, 0 errores |
| Frontend | `npm.cmd run build` | Correcto |

Tests de préstamos:

- `backend/src/test/java/com/biblioteca/backend/prestamo/PrestamoPersistenceTests.java` (9 tests)
- `backend/src/test/java/com/biblioteca/backend/prestamo/PrestamoIntegrationTests.java` (13 tests, 14 ejecuciones)
- `frontend/tests/loans.test.js` (9 tests)

## Criterios comprobados

| Criterio | Cómo se comprueba | Resultado |
|---|---|---|
| Préstamo válido a un estudiante sin bloqueos | Test `unEstudiantePideUnLibroYSeLePrestaUnEjemplarLibre` y prueba manual desde la web | ✅ |
| El backend asigna un ejemplar libre distinto en cada préstamo | Test `cadaPrestamoUsaOtroEjemplarYCuandoNoQuedanDevuelve409` | ✅ |
| Se rechazan ejemplares prestados o retirados | Test `noSePrestanEjemplaresRetiradosNiPrestados`; libro retirado en `unLibroInexistenteORetiradoDevuelve404` | ✅ |
| Se rechazan estudiantes bloqueados y el bloqueo se quita al devolver | Test `unPrestamoVencidoBloqueaYAlDevolverloSeDesbloquea` y prueba manual desde la web | ✅ |
| Hasta 5 préstamos activos; el sexto se rechaza | Test `permiteCincoPrestamosYRechazaElSexto` | ✅ |
| El vencimiento es a 30 días | Tests `unEstudiantePideUnLibroYSeLePrestaUnEjemplarLibre` y `unPrestamoNuevoEstaActivoYVenceA30Dias`; prueba manual (17/11 → 17/12) | ✅ |
| El ejemplar pasa a PRESTADO | Test `unEstudiantePideUnLibroYSeLePrestaUnEjemplarLibre` y prueba manual en PostgreSQL | ✅ |
| El libro deja de aparecer como disponible | Tests `trasPrestarLaUnicaCopiaElLibroDejaDeEstarDisponible` y `conOtraCopiaLibreElLibroSigueDisponible`; prueba manual desde la web | ✅ |
| Dos solicitudes simultáneas no prestan el mismo ejemplar | Test `dosEstudiantesALaVezPorElUltimoEjemplarSoloUnoLoConsigue` y prueba del índice V10 en PostgreSQL | ✅ |
| Los fallos no dejan préstamos a medias | En los rechazos de límite y bloqueo el ejemplar sigue DISPONIBLE (`permiteCincoPrestamosYRechazaElSexto`, `unPrestamoVencidoBloqueaYAlDevolverloSeDesbloquea`); dos INSERT en PostgreSQL con el segundo rechazado no dejan ninguna fila | ✅ |
| La base de datos solo admite préstamos coherentes | Tests de reglas de `PrestamoPersistenceTests` (estado, devolución, vencimiento) | ✅ |
| Permisos del endpoint | Tests `sinSesionDevuelve401`, `sinTokenCsrfDevuelve403` y `bibliotecariosYAdministradoresNoPuedenPedirPrestamos` | ✅ |
| Datos no válidos y libro inexistente | Tests `losDatosNoValidosDevuelven400` y `unLibroInexistenteORetiradoDevuelve404` | ✅ |
| La interfaz llama a la API con CSRF y muestra los errores del backend | Tests de `loans.test.js` | ✅ |

## Pruebas manuales

Hechas con la aplicación completa en local (PostgreSQL en Docker, backend en el
puerto 8080 y frontend en http://localhost:5173).

| Prueba | Resultado |
|---|---|
| `\d prestamos` muestra las columnas, los 3 CHECK y los índices de la V9 y la V10 | ✅ |
| Dos préstamos activos del mismo ejemplar en un solo comando: el segundo falla con `uq_prestamos_ejemplar_activo` y no queda ninguna fila | ✅ |
| Un estudiante ve "Pedir préstamo", la confirmación y "Cancelar" | ✅ |
| Al pedir «Don Quijote de la Mancha» se muestra el ejemplar EJ-0001 y la fecha de devolución, y el libro pasa a "Sin ejemplares disponibles" | ✅ |
| En PostgreSQL el préstamo queda ACTIVO, con vencimiento 30 días después, y EJ-0001 queda PRESTADO | ✅ |
| Un bibliotecario no ve el botón "Pedir préstamo" | ✅ |
| Con un préstamo vencido sin devolver se muestra el mensaje de bloqueo y el libro sigue disponible | ✅ |
| Al devolver el préstamo vencido el estudiante puede volver a pedir | ✅ |

Después de las pruebas se borraron los préstamos de prueba y EJ-0001 volvió a
DISPONIBLE.

## Fuera de esta historia

Devolver préstamos desde la aplicación, ver el historial y que el estudiante
consulte su bloqueo forman parte de otras historias (por ejemplo, la US12). La
consulta del bloqueo (`PrestamoRepository.tienePrestamosVencidos`) ya queda
preparada para reutilizarla.
