# Pruebas de integración con PostgreSQL

Desde el directorio `backend`, la suite habitual usa H2 en memoria y no incluye
las clases cuyo nombre termina en `IT`:

```powershell
.\mvnw.cmd test
```

Para ejecutar explícitamente las pruebas PostgreSQL (requiere Docker en marcha):

```powershell
.\mvnw.cmd '-Dtest=*Postgres*IT' test
```

La infraestructura actual está en `PostgresInfrastructureIT` y las restricciones
de préstamos en `PostgresPrestamoRestriccionesIT`. Los futuros tests PostgreSQL
deben seguir ese patrón de nombre y usar `@PostgresIntegrationTest`.
Si Docker no está disponible, la ejecución PostgreSQL falla; no se omite.

Spring Boot inicia PostgreSQL 17.11 con Testcontainers, una base y credenciales
exclusivas y un puerto dinámico. Flyway aplica las migraciones reales antes de
que Hibernate valide el esquema. El contenedor se elimina al cerrar el contexto.
Las imágenes descargadas pueden permanecer en la caché de Docker.

La anotación común fija URL de respaldo inaccesibles para JDBC y Flyway; las
conexiones efectivas se suministran mediante `@ServiceConnection`. La limpieza
comprueba la identidad de la conexión y afecta solo a los datos de cada test.
Las futuras clases deben conservar esa protección. No se monta el volumen de Compose ni se
utiliza la base de desarrollo `biblioteca_db`.
