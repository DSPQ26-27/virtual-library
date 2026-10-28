# BIBLIOTECA VIRTUAL
## Contexto y definición del proyecto

**Proyecto académico — Universidad de Deusto**  
**Metodología:** Agile / Scrum  
**Equipo:** 4 integrantes  
**Duración prevista:** 4 sprints de 2 semanas

### 1. Objetivo del proyecto

Desarrollar una aplicación web de gestión de una biblioteca universitaria simulada.

La aplicación permitirá que los estudiantes consulten libros, realicen préstamos automáticamente, devuelvan ejemplares, renueven préstamos y participen en listas de espera.

También habrá funcionalidades específicas para bibliotecarios y administradores.

No existe una biblioteca física real detrás del sistema. Los ejemplares, préstamos y devoluciones se gestionan digitalmente como una simulación.

### 2. Tipos de usuarios

**Estudiante**
- Registrarse e iniciar sesión.
- Consultar y buscar libros.
- Solicitar préstamos.
- Consultar y renovar sus préstamos.
- Devolver ejemplares.
- Inscribirse en listas de espera.
- Recibir notificaciones.

**Bibliotecario**
- Gestionar libros y ejemplares.
- Consultar préstamos, reservas y estudiantes.
- Supervisar la actividad de la biblioteca.
- Acceder a estadísticas básicas.

**Administrador**
- Gestionar usuarios y roles.
- Configurar parámetros de préstamo.
- Acceder a informes y registros de actividad.
- Supervisar el funcionamiento general.

### 3. Reglas de negocio

Estas son las principales reglas acordadas:

1. Cada préstamo dura **30 días**.
2. Un estudiante puede tener como máximo **5 préstamos activos**.
3. Cada préstamo puede renovarse **2 veces**, por 30 días cada renovación.
4. No se permite renovar si existe una lista de espera que necesita el ejemplar.
5. Cuando hay ejemplares disponibles, el préstamo se realiza automáticamente.
6. Cuando todos los ejemplares están prestados, el estudiante puede incorporarse a una lista de espera **FIFO**, por orden de solicitud.
7. Las devoluciones las realiza el propio estudiante desde la aplicación.
8. Los estudiantes con préstamos vencidos no pueden solicitar nuevos préstamos hasta regularizar su situación.
9. Un libro puede tener varios ejemplares, cada uno identificado individualmente.
10. El sistema debe mantener un historial de préstamos y devoluciones.

### 4. Funcionalidades principales

**Gestión de usuarios:** registro, autenticación, permisos y roles.

**Catálogo:** consulta de libros con título, autor, ISBN, editorial, categoría, año, descripción, portada y disponibilidad.

**Búsqueda:** búsqueda por título, autor e ISBN; filtros por categoría, editorial, año y disponibilidad.

**Préstamos:** asignación automática de ejemplares, renovaciones y devoluciones.

**Reservas:** listas de espera y asignación según disponibilidad.

**Notificaciones:** avisos internos sobre préstamos, devoluciones, vencimientos y disponibilidad. El correo electrónico queda previsto como ampliación.

**Administración:** gestión de usuarios, libros, ejemplares, configuración e informes.

### 5. Arquitectura tecnológica

La aplicación seguirá una arquitectura de tres capas:

**Frontend — React + Vite**
- Interfaz de usuario.
- Formularios y páginas.
- Comunicación con el backend mediante API REST.

**Backend — Java 21 + Spring Boot 4**
- API REST.
- Validación de datos.
- Lógica de negocio.
- Autenticación y autorización con Spring Security.
- Acceso a datos mediante Spring Data JPA y Hibernate.

**Base de datos — PostgreSQL 17**
- Almacenamiento de usuarios, libros, ejemplares y préstamos.
- Ejecución mediante Docker Compose.
- Gestión de cambios del esquema mediante Flyway.

**Herramientas de equipo**
- Git y GitHub para control de versiones.
- GitHub Issues y GitHub Projects para seguimiento Scrum.
- Maven para dependencias Java.
- npm para dependencias React.

### 6. Estado actual del desarrollo

La configuración inicial está preparada:

- Backend Spring Boot creado.
- Endpoint `/api/health` probado.
- PostgreSQL funcionando en Docker.
- Flyway configurado y primera migración ejecutada.
- Frontend React + Vite creado.
- Repositorio GitHub con ramas y Pull Requests.
- Backlog y Sprint 1 organizados en GitHub Projects.

Todavía no se han implementado las funcionalidades de registro, login y consulta de catálogo.

### 7. Plan de desarrollo

**Sprint 1 — MVP inicial**

Objetivo: permitir el registro, inicio de sesión y consulta básica del catálogo.

Historias:
- US01: Registro de estudiantes — 5 SP.
- US02: Inicio de sesión — 5 SP.
- US03: Consulta del catálogo — 3 SP.

**Sprint 2 — Gestión del catálogo**

Administración de libros y ejemplares, existencias y funciones de búsqueda más completas.

**Sprint 3 — Préstamos y reservas**

Préstamos automáticos, renovaciones, devoluciones y listas de espera.

**Sprint 4 — Gestión y cierre**

Funciones administrativas prioritarias, notificaciones internas, integración, pruebas y documentación final.

El alcance concreto de los sprints posteriores puede ajustarse durante la planificación.

### 8. Reparto de trabajo del Sprint 1

| Responsable | Issues | Trabajo |
|---|---|---|
| Product Owner / desarrollador | #21, #27 | Modelos Usuario y Libro |
| Desarrollador 2 | #22, #28 | APIs de registro y libros |
| Desarrollador 3 | #24, #25 | Seguridad y autenticación |
| Desarrollador 4 | #23, #26, #29 | Interfaces React |

Este reparto es una propuesta inicial y debe confirmarse entre los cuatro integrantes.

### 9. Metodología de trabajo

Utilizaremos Scrum adaptado al proyecto académico.

Cada funcionalidad se gestionará mediante una Issue de GitHub y una rama específica.

El proceso habitual será:

1. Seleccionar una Issue del Sprint Backlog.
2. Asignarla al desarrollador responsable.
3. Cambiar su estado a `In Progress`.
4. Crear una rama desde `main`.
5. Implementar la funcionalidad.
6. Realizar pruebas.
7. Crear los commits referenciando la Issue.
8. Subir la rama y abrir un Pull Request.
9. Revisar el código y fusionarlo en `main`.
10. Cerrar la Issue y actualizar su estado a `Done`.

### 10. Definition of Done

Una tarea se considera terminada cuando:

- Está implementada y funciona.
- Cumple sus criterios de aceptación.
- Ha superado las pruebas correspondientes.
- El código ha sido revisado.
- Se ha integrado correctamente en `main`.
- Se ha actualizado la documentación necesaria.

### 11. Decisiones todavía pendientes

No debemos considerar cerradas todas las decisiones funcionales. Entre otras, habrá que concretar:

- Cómo gestionar al primer estudiante de una lista de espera si no puede recibir un préstamo.
- Si un estudiante puede tener dos ejemplares del mismo título.
- Cómo cancelar reservas.
- La estrategia exacta de autenticación (sesiones o tokens).
- Las reglas de vencimiento y ejecución de notificaciones.

Estas decisiones deberán acordarse antes de implementar las funcionalidades afectadas.

### 12. Objetivo final

Entregar una aplicación web funcional que demuestre el desarrollo colaborativo, la arquitectura frontend/backend, el acceso a bases de datos y la aplicación práctica de una metodología Agile/Scrum.

El repositorio, las Issues, los Pull Requests, las pruebas y la documentación servirán como evidencia del proceso de desarrollo.
