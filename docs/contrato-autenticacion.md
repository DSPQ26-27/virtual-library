# Contrato de autenticación

Contrato del frontend #26, implementado en el backend de #24 y #25.
Login, recarga de sesión, logout y credenciales incorrectas comprobados
manualmente en React con PostgreSQL local. Pendiente de revisión del equipo
y del recorrido con registro y catálogo reales. Detalles en `autenticacion.md`.

## Mecanismo

Sesión del servidor mediante cookie HttpOnly.
El frontend envía credentials: include.
No se guardan credenciales ni tokens de sesión en localStorage.

La cookie debe utilizar Secure en producción con HTTPS y una
configuración SameSite apropiada. En desarrollo se utiliza el proxy
de Vite hacia http://localhost:8080.

Las respuestas de autenticación no deben almacenarse en caché.

## Usuario

Login y consulta de sesión devuelven directamente este formato:

{
  "id": 1,
  "nombre": "Santi Gaytan",
  "email": "santi@example.com",
  "roles": ["STUDENT"]
}

No devolver contraseñas ni hashes.
Los roles se asignan y validan en el servidor.

## GET /api/auth/csrf

Accesible sin autenticación.

HTTP 200:
{
  "headerName": "X-CSRF-TOKEN",
  "token": "token-generado-por-spring-security"
}

El frontend obtiene un token antes de cada login y logout.
El backend debe mantener el contexto de cookie necesario para validarlo.

## POST /api/auth/login

Content-Type: application/json
Cabecera X-CSRF-TOKEN con el token obtenido previamente.

Petición:
{
  "email": "santi@example.com",
  "password": "ClavePrueba123!"
}

- HTTP 200: usuario con el formato definido y sesión real establecida.
- HTTP 400: datos inválidos.
- HTTP 401: credenciales incorrectas; mensaje genérico.
- HTTP 403: solicitud rechazada por protección CSRF.
- HTTP 429: demasiados intentos, si se implementa esa limitación.

Devolver JSON no basta: la autenticación debe quedar persistida
en la sesión de Spring Security para las peticiones posteriores.

## GET /api/auth/me

- HTTP 200: usuario de la sesión, con el formato definido.
- HTTP 401: no hay sesión o está caducada.

No redirigir a una página HTML de login.

## POST /api/auth/logout

Cabecera X-CSRF-TOKEN y cookie de sesión.

- Invalidar la sesión y limpiar la autenticación.
- HTTP 204, sin cuerpo.
- El frontend también considera HTTP 401 como sesión ya inexistente.
- Los demás errores no se presentan como un cierre correcto.

## Protección

El backend debe proteger los endpoints del catálogo y demás recursos.
La protección de rutas de React solo controla la interfaz.

## Frontend

- /registro: registro de estudiantes.
- /login: formulario de acceso.
- /catalogo: ruta protegida.
- Al recargar, se consulta /api/auth/me.
- Sin sesión confirmada no se muestra contenido protegido.
- La sesión caducada se detecta al comprobarla de nuevo.
- El catálogo completo corresponde a #29.

## Estado

Frontend y endpoints de autenticación implementados.
La #25 añade pruebas del backend con base H2 aislada y sesiones entre peticiones:
18 pruebas del backend y 23 del cliente frontend superadas el 29/10/2026.
El usuario confirma además login, recarga, logout y rechazo de contraseña
incorrecta desde React con PostgreSQL local. Pendiente de revisión del código
y del recorrido con registro y catálogo reales.
