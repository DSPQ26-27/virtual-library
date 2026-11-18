# Asignación FIFO al devolver — tarea #67

## Funcionamiento

PrestamoService.devolver mantiene el contrato HTTP existente. Dentro de una
transacción realiza los siguientes pasos.

1. Comprueba que el préstamo pertenece al estudiante y sigue activo.
2. Marca el préstamo DEVUELTO y libera el ejemplar.
3. Guarda esos cambios en SQL antes de crear un nuevo préstamo, para respetar
   el índice de un solo préstamo activo por ejemplar.
4. Consulta las solicitudes PENDIENTES del libro por fecha e identificador.
5. Busca al primer estudiante elegible y crea un préstamo de 30 días.
6. Marca la solicitud ASIGNADA y el ejemplar PRESTADO.

Si el libro está retirado o no hay estudiantes elegibles, la devolución deja
el ejemplar disponible. Si falla la asignación, se revierte toda la transacción.

## Elegibilidad

El estudiante debe tener cuenta activa, rol ESTUDIANTE, menos de cinco préstamos
activos y ningún préstamo vencido. Se reutilizan las consultas de préstamos.

Decisión provisional que debe revisar el equipo: los no elegibles conservan
su solicitud PENDIENTE y su antigüedad, y se busca al siguiente elegible. No hay
un proceso automático que vuelva a asignar ejemplares al recuperar elegibilidad.
La asignación se desencadena al devolver un ejemplar.

## Concurrencia

V13 crea la tabla auxiliar coordinacion_prestamos, con una sola fila. Los métodos
de alta en espera, préstamo, devolución y renovación bloquean primero esa fila
con SELECT FOR UPDATE. Después mantienen los bloqueos existentes de usuario y
libro. El bloqueo se libera al confirmar o revertir la transacción.

Esto evita bloqueos cruzados entre quien devuelve y quien recibe el ejemplar,
y protege el límite de cinco cuando dos libros se devuelven simultáneamente.
Se elige una solución sencilla que serializa las operaciones de préstamo de toda
la biblioteca. Esta decisión limita la capacidad ante muchas operaciones a la vez.

## Archivos para explicar

- SolicitudEspera.asignar cambia el estado de la solicitud.
- PrestamoService.asignarSiguiente recorre la cola y crea el nuevo préstamo.
- PrestamoService.esElegible comprueba las condiciones del estudiante.
- SolicitudEsperaRepository.bloquearOperaciones coordina las transacciones.
- V13 crea la fila de coordinación.

## Validación

Desde backend, ejecutar .\mvnw.cmd test.
AsignacionFifoTests comprueba el orden FIFO, el historial y el plazo de 30 días,
estudiantes inactivos, vencimientos, límite de cinco, cambios de rol, libros
retirados y dos devoluciones simultáneas hacia un estudiante con cuatro préstamos.

Esta rama parte de feature/66-lista-espera-api. Hasta integrar #66 en main,
un Pull Request hacia la rama de #66 permite revisar solo el cambio de #67.
Después de integrar #66, el destino puede cambiarse a main.
