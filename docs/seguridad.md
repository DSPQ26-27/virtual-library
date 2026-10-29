# Seguridad del backend — #24

La API utiliza la propuesta de sesión del servidor mediante cookie HttpOnly
de `contrato-autenticacion.md`. Se mantiene CSRF; únicamente POST
`/api/auth/register` está exento, como exige `contrato-registro.md`.
El registro no inicia sesión automáticamente ni admite un rol del cliente.

| Recurso | Acceso |
| --- | --- |
| GET /api/health y /api/auth/csrf | Público |
| POST /api/auth/register y /api/auth/login | Público; login requiere CSRF |
| GET /api/libros y /api/libros/** | STUDENT, LIBRARIAN o ADMIN |
| Otros métodos de /api/libros y /api/libros/** | LIBRARIAN o ADMIN; CSRF en métodos no seguros |
| /api/admin/** | ADMIN |
| Resto de rutas | STUDENT, LIBRARIAN o ADMIN |

Las reglas de escritura del catálogo y administración reservan permisos;
no implementan los endpoints de futuros sprints. Los roles de Spring llevan
el prefijo ROLE_ (por ejemplo ROLE_STUDENT).

Las peticiones sin autenticación reciben 401 JSON y las denegadas, 403 JSON,
sin redirecciones HTML. Una petición no segura sin CSRF válido recibe 403
antes de comprobar permisos. Se deshabilitan el formulario de login, HTTP
Basic, el logout predeterminado y el almacenamiento de peticiones para
redirección. Spring mantiene sus cabeceras de seguridad y política de caché.

Se proporciona un PasswordEncoder BCrypt para su uso por registro y login.
La migración actual usa ESTUDIANTE como rol por defecto; el responsable de
Usuario y la autenticación deben coordinar su equivalencia con STUDENT
antes de integrar. Esta tarea no modifica la entidad ni la migración ajenas.

## Pruebas

Desde backend, con JDK 21 y Maven:

```powershell
mvn -Dtest=SecurityConfigTests test
```

Las pruebas usan controladores y usuarios de prueba para verificar la cadena
de filtros real sin PostgreSQL. No prueban credenciales ni sesiones reales.

Verificación local del 29/10/2026: compilación correcta con JDK 21 y seis
pruebas superadas, sin fallos ni errores. No se ha ejecutado la prueba de
contexto completo ni el recorrido con PostgreSQL y frontend.

## Pendiente de #25 e integración

- Implementar csrf, login, me y logout según el contrato.
- Persistir la autenticación en la sesión y proteger contra fijación de sesión.
- Invalidar la sesión al salir y verificar cookies en desarrollo y producción.
- Probar el recorrido completo con Usuario, registro y frontend reales.
- Revisión por otro integrante e integración mediante PR antes de cerrar #24.
