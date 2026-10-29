# Contrato de registro de estudiantes

Propuesto desde el frontend de la Issue #23.
Implementado en el backend en la Issue #22 (RegistrationController).

## Petición

POST /api/auth/register
Content-Type: application/json
Accept: application/json

Campos:
- nombre: texto obligatorio; se recortan los espacios exteriores;
  entre 2 y 100 caracteres después del recorte.
- email: texto obligatorio; formato de correo válido; máximo 254
  caracteres; se recortan los espacios exteriores. Se guarda en
  minúsculas. La columna usuarios.email admite 254 caracteres (migración V2).
- password: texto obligatorio; entre 8 y 72 caracteres (72 es el
  límite de BCrypt);
  no puede contener únicamente espacios y no se recorta.

El backend debe repetir las validaciones.
El correo debe comprobarse como único sin distinguir mayúsculas.
El backend asigna el rol STUDENT; no lo recibe del cliente.
La contraseña debe almacenarse mediante un hash seguro.

## Éxito

HTTP 201, con JSON:
{"id":1,"nombre":"Santi Gaytan","email":"santi@example.com"}

No devolver password ni su hash.
No iniciar sesión automáticamente.

## Errores

HTTP 400 para validación:
{"message":"Revisa los datos del formulario.","errors":{"nombre":"El nombre debe tener entre 2 y 100 caracteres.","email":"Correo inválido.","password":"Mínimo 8 caracteres."}}

Solo incluir en errors los campos con errores.
Cada valor de errors debe ser un texto.

HTTP 409 para correo duplicado:
{"message":"Ya existe una cuenta con ese correo electrónico.","errors":{"email":"Este correo ya está registrado."}}

Los errores internos no deben exponer detalles técnicos ni contraseñas.

## Seguridad y conexión

El registro debe aceptar peticiones JSON anónimas,
sin cookies ni token previo.

La tarea #24 debe configurar los permisos y el tratamiento de CSRF
necesarios para este endpoint, manteniendo protegidas las demás rutas
según sus requisitos.

En desarrollo, Vite reenvía /api al backend http://localhost:8080.
La configuración del despliegue se resolverá por separado.

## Verificación

Cubierto por backend/src/test/java/com/biblioteca/backend/auth/RegistrationIntegrationTests.java:

- [x] Registro real y persistencia del usuario
  (validRegistrationCreatesStudentWithHashedPassword).
- [x] Contraseña guardada como hash BCrypt, nunca en claro (mismo test).
- [x] Rol STUDENT asignado por el servidor (clientCannotChooseItsRole).
- [x] Validaciones del backend (invalidDataReturnsOneMessagePerField,
  onlyInvalidFieldsAreReported, passwordLongerThanBcryptLimitIsRejected,
  emailOf254CharactersIsAccepted, malformedOrEmptyBodyReturns400).
- [x] Correo duplicado sin distinguir mayúsculas (duplicateEmailIsRejectedIgnoringCase).
- [x] Acceso anónimo al endpoint: todas las pruebas se hacen sin sesión ni CSRF.
- [ ] Revisión e integración mediante Pull Request.
