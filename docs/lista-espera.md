# Lista de espera — tarea #66

## Qué hace

POST /api/espera recibe un JSON como {"libroId":25}.
El estudiante se obtiene de la sesión y solo pueden acceder estudiantes.
El identificador es obligatorio y positivo. POST requiere token CSRF.

El servicio comprueba que la cuenta está activa, que el libro existe y sigue en
el catálogo, que no hay una solicitud pendiente duplicada y que no quedan
ejemplares prestables. Después guarda una solicitud PENDIENTE.

GET /api/espera/mias devuelve las solicitudes propias con id, libroId, titulo,
fechaSolicitud y estado.

## Cómo está organizado

- Request contiene los datos de entrada.
- Controller recibe la petición y obtiene el usuario de la sesión.
- Service comprueba las reglas y guarda la solicitud.
- Repository consulta y guarda los datos.
- Response contiene los datos que se devuelven.
- ExceptionHandler convierte los errores en respuestas HTTP claras.

## FIFO y solicitudes simultáneas

Las solicitudes pendientes de un libro se consultan por fechaSolicitud y luego
por id. El identificador desempata cuando dos solicitudes tienen la misma fecha.

El alta se ejecuta en una transacción. Bloquea primero la fila del estudiante y
después la del libro, siguiendo el orden usado por los préstamos. Así dos altas
del mismo estudiante no superan a la vez la comprobación de duplicados.

## Errores

- 400 DATOS_NO_VALIDOS si faltan datos o el identificador es inválido.
- 401 si no hay sesión.
- 403 si falta rol de estudiante o el token CSRF.
- 404 LIBRO_NO_ENCONTRADO si el libro no existe o está retirado.
- 409 ESTUDIANTE_INACTIVO si la cuenta no está activa.
- 409 SOLICITUD_DUPLICADA si ya hay una solicitud pendiente.
- 409 EJEMPLARES_DISPONIBLES si se puede pedir un préstamo.

Decisión provisional: tener cinco préstamos o alguno vencido permite apuntarse.
La elegibilidad al asignar un ejemplar se acordará para #67.

## Pruebas

Desde backend, ejecutar .\mvnw.cmd test.
SolicitudEsperaIntegrationTests comprueba altas, duplicados, disponibilidad,
libros inexistentes y retirados, datos inválidos, permisos, CSRF, consultas
propias, orden FIFO y dos altas simultáneas del mismo estudiante.

Esta entrega corresponde a #66. La asignación de ejemplares (#67), las pantallas
React (#68) y la validación de esos flujos completos (#69) se harán por separado.
La tabla y la entidad de solicitudes ya existían desde la migración V12.
