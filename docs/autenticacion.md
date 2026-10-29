# API de autenticación y cierre de sesión — #25

## Alcance

Implementa `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me`
y `POST /api/auth/logout` conforme a `contrato-autenticacion.md`.
Usa sesiones del servidor; no emite JWT ni almacena tokens en localStorage.
No implementa registro (#22), entidad Usuario (#21) ni catálogo (#28).

## Componentes

- `auth/AuthUserRepository`: consulta parametrizada de lectura sobre la tabla
  `usuarios` existente. Se usa JDBC para no crear una entidad JPA que duplique
  el trabajo de #21. Cuando se integre aquella tarea, el equipo puede sustituir
  esta lectura por su repositorio sin alterar los endpoints.
- `auth/LibraryUserDetailsService`: carga el usuario por correo sin distinguir
  mayúsculas, recortando solo espacios exteriores del correo.
- `auth/LibraryUserDetails`: principal de sesión con id, nombre, correo, estado
  y autoridades. Extiende `User` de Spring; el proveedor borra el hash al
  autenticar para no mantenerlo en la sesión. Nunca se serializa como respuesta.
- `config/AuthenticationConfig`: `DaoAuthenticationProvider` y `ProviderManager`
  verifican BCrypt y rechazan cuentas desactivadas.
- `auth/AuthController`: endpoints, estrategia de sesión y guardado explícito
  del contexto de Spring Security en `HttpSessionSecurityContextRepository`.
- `auth/AuthExceptionHandler`: respuestas JSON genéricas de validación y
  credenciales, sin revelar si una cuenta existe o está desactivada.

No se modifica V1 ni se crean usuarios en producción. El login necesita una
fila existente con `password_hash` BCrypt y `activo=true`. Se rechazan correos
ambiguos si hubiera varias filas que solo difieren en mayúsculas.

## Roles

| Valor de usuarios.rol | Rol enviado al frontend | Autoridad Spring |
| --- | --- | --- |
| ESTUDIANTE o STUDENT | STUDENT | ROLE_STUDENT |
| BIBLIOTECARIO o LIBRARIAN | LIBRARIAN | ROLE_LIBRARIAN |
| ADMINISTRADOR o ADMIN | ADMIN | ROLE_ADMIN |

Se rechazan roles desconocidos. No se toma ningún rol enviado por el cliente.
Esta equivalencia resuelve la diferencia entre la migración actual y el
contrato del frontend; no altera datos de los compañeros.

## Flujo de sesión

1. `GET csrf` crea el contexto de sesión si hace falta y devuelve el token.
   El navegador conserva la cookie HttpOnly; React usa `credentials: include`.
2. `POST login` envía JSON y la cabecera `X-CSRF-TOKEN`. Se comprueban datos,
   credenciales, cuenta activa y rol. La contraseña no se recorta.
3. Al autenticar se cambia el id de sesión para evitar fijación de sesión y
   se invalida el token CSRF anterior. Se guarda explícitamente la identidad
   en la sesión para que otras peticiones no dependan del hilo del login.
4. `GET me` devuelve `{id,nombre,email,roles}` sin contraseña ni hash.
5. Antes de salir, React vuelve a obtener CSRF. `POST logout` invalida la
   sesión, limpia el contexto y expira la cookie JSESSIONID. Devuelve 204.
6. Después del logout o la caducidad, `GET me` devuelve 401. Reutilizar una
   cookie invalidada no restaura la autenticación.

400: JSON o datos inválidos. 401: credenciales incorrectas o ausencia de
sesión. 403: CSRF inválido o permisos insuficientes. CSRF se comprueba antes
de llegar al controlador: un POST anónimo sin token también recibe 403.
El logout sin autenticación y con CSRF válido devuelve 401, admitido por React.

## Cookie y ejecución

Se configura HttpOnly, SameSite=Lax y caducidad de sesión por 30 minutos de
inactividad. En desarrollo se admite HTTP y el proxy Vite reenvía `/api` a
localhost:8080. En producción con HTTPS hay que definir:

```text
SESSION_COOKIE_SECURE=true
```

El despliegue entre sitios distintos necesita revisar proxy, CORS y SameSite;
esa topología no está configurada aquí. Las sesiones se mantienen en memoria
del servidor, no en PostgreSQL; un reinicio obliga a iniciar sesión otra vez.
Los cambios de rol o estado posteriores al login no invalidan inmediatamente
sesiones ya abiertas; la revocación administrativa queda fuera de esta tarea.
No se ha implementado limitación de intentos (429 era opcional en el contrato).

## Pruebas reproducibles

Con JDK 21 y Maven, desde backend:

```powershell
mvn test
```

O solo la autenticación y regresión de permisos:

```powershell
mvn '-Dtest=AuthIntegrationTests,SecurityConfigTests' test
```

`application-test.properties` usa H2 en memoria con compatibilidad PostgreSQL,
activado únicamente por `@ActiveProfiles("test")`. H2 tiene scope test y no
sustituye PostgreSQL al ejecutar normalmente la aplicación. Flyway aplica la
migración V1 real y las pruebas insertan cuentas ficticias con hashes BCrypt.
La prueba de contexto existente también utiliza este perfil aislado.

Las pruebas verifican credenciales válidas e inválidas, cuenta inactiva,
validación JSON, roles en ambos idiomas, correos ambiguos, persistencia de
sesión, ausencia de contraseñas en respuestas y sesión, CSRF real obtenido
del endpoint, rotación y logout. Una prueba levanta el servidor HTTP en un
puerto aleatorio y usa un cliente con cookies para comprobar HttpOnly,
SameSite, cambio de id y rechazo de cookies antiguas tras el logout.

Las pruebas de #24 continúan comprobando los permisos mediante controladores
aislados de prueba. Estos componentes se marcan como exclusivos de pruebas
para que no se mezclen con el contexto completo de la aplicación.

### Resultados del 29 de octubre de 2026

- `mvn test`: BUILD SUCCESS. 18 pruebas, cero fallos, cero errores y ninguna
  omitida: 11 de autenticación (incluida HTTP con cookies reales), 6 de permisos
  y 1 de arranque del contexto completo.
- `node --test tests/auth.test.js tests/session.test.js tests/books.test.js`
  desde frontend: 23 pruebas superadas. Estas pruebas existentes simulan fetch;
  no demuestran el recorrido entre React y el backend real.
- Base H2 aislada con la migración V1 real aplicada por Flyway. No se ha probado
  PostgreSQL mediante esa suite automática; la prueba manual se registra aparte.

### Verificación manual local confirmada el 29 de octubre de 2026

- PostgreSQL 17 en Docker y backend conectados correctamente.
- Cuenta ficticia local insertada en `usuarios`, con hash BCrypt y rol
  ESTUDIANTE; no se añadió como semilla ni migración al repositorio.
- El usuario confirma en React: login correcto, sesión conservada al recargar,
  cierre de sesión correcto y rechazo de contraseña incorrecta.
- La consulta completa del catálogo y el registro de nuevas cuentas siguen
  pendientes de sus endpoints (#28 y #22). Esta comprobación no los valida.

## Pasos para repetir la verificación con el equipo

1. Arrancar PostgreSQL y backend siguiendo el manual de Documentacion.
2. Usar un usuario real creado por #22 o una cuenta local coordinada, cuyo
   hash sea BCrypt. No guardar credenciales de desarrollo en commits.
3. Arrancar React: `npm.cmd ci` y `npm.cmd run dev` desde frontend.
4. En la pestaña Network del navegador, comprobar csrf 200 y login 200 con
   correo y contraseña válidos. Recargar: me debe seguir devolviendo 200.
5. Comprobar credenciales incorrectas y los errores de validación.
6. Consultar el catálogo cuando #28 esté integrado; React #29 ya existe,
   pero esta tarea no crea ni simula un endpoint de catálogo en producción.
7. Cerrar sesión: logout 204 y me 401; no debe poder accederse al catálogo.
8. Registrar resultados, revisión de otro integrante y PR con `Closes #25`.

No afirmar que se ha verificado PostgreSQL o el recorrido React completo
basándose únicamente en las pruebas H2.
