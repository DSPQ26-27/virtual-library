# Consulta de préstamos y reservas propios — #70

Se reutilizan las APIs existentes, ambas restringidas a estudiantes autenticados.

| Petición | Datos devueltos |
|---|---|
| GET /api/prestamos/mios | Préstamos activos e historial, con título, fechas, estado y renovaciones |
| GET /api/espera/mias | Reservas propias con título, fecha y estado |

Los controladores obtienen el id del estudiante de la sesión. Las consultas
filtran por ese id. Un parámetro usuarioId enviado por el navegador no permite
consultar datos de otra persona. ConsultasEstudianteTests comprueba esta propiedad
con dos estudiantes que tienen préstamos y reservas diferentes.

Las pruebas existentes ya cubren autenticación, permisos y formato de respuesta.
Para ejecutar el backend, usar .\mvnw.cmd test desde backend.
