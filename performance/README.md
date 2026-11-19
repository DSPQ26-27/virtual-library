# Gatling: sesión inicial y entorno aislado

Módulo Maven independiente con Java 21, Gatling Java DSL 3.16.1 y plugin 4.22.3. No se ejecuta con `mvn test` del backend. La simulación usa **un solo usuario**; no mide la capacidad del sistema.

## Ejecución en Windows con Docker Desktop

Desde PowerShell en este directorio:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\ejecutar.ps1
```

El lanzador compila el backend, crea un proyecto Compose con nombre y credenciales aleatorios, arranca PostgreSQL 17 y el backend, ejecuta Gatling y finalmente elimina únicamente los contenedores, la red y la imagen de ese proyecto. Selecciona un puerto local libre; si deja de estar libre antes del arranque, la ejecución falla sin usar el servidor existente. PostgreSQL no publica puertos y utiliza tmpfs; al detener el entorno se pierden sus datos. No se reutilizan `biblioteca_db` ni `biblioteca_pgdata`. La contraseña y el usuario de la simulación son temporales. Flyway crea el esquema al arrancar el backend.

En Windows, el lanzador usa la ruta corta de la carpeta de JLine de Maven cuando
está disponible. Así evita que el plugin Gatling informe de espacios al
propagar esa propiedad. Si Windows no dispone de una ruta corta sin espacios,
se conserva la configuración habitual de Maven y el aviso podría aparecer de
nuevo. La ejecución directa de `mvn gatling:test` no aplica este ajuste.

## Verificación obligatoria

`EntornoSeguro` ejecuta `verificar-entorno.ps1` antes de cada simulación, también si se invoca Maven directamente. Si la comprobación falla, Gatling no inicia su recorrido. El verificador comprueba etiquetas del proyecto, contenedores, imagen, comando, red, puertos, almacenamiento temporal y conexión configurada. Inserta un marcador aleatorio mediante `docker exec` en el PostgreSQL previamente identificado y lo busca por la API autenticada del backend. Además registra un estudiante de prueba y verifica cambio de cookie, token CSRF y logout. No hay un interruptor para omitir la comprobación. Son necesarias la CLI de Docker y PowerShell; los mensajes indican si el entorno es aceptado o rechazado.

La verificación envía peticiones HTTP de preparación y deja el marcador y el usuario en la base efímera; no forman parte de las seis peticiones del informe Gatling. El entorno se dedica exclusivamente a pruebas de rendimiento, no es una barrera frente a un administrador local malintencionado ni garantiza el aislamiento si otro proceso con acceso a Docker cambia la configuración durante la ejecución. No use este procedimiento con servidores remotos o bases reales.

## Informe

Gatling indica la ruta `target/gatling/<ejecución>/index.html`. Para abrir el informe más reciente desde este directorio:

```powershell
$informe = Get-ChildItem .\target\gatling -Filter index.html -Recurse | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Invoke-Item $informe.FullName
```

El informe muestra peticiones correctas y fallidas, tiempos medios y percentiles, y solicitudes por segundo. Con un usuario y seis peticiones estos datos sirven para comprobar el recorrido, no para extraer conclusiones de rendimiento.

Para ejecutar solo la suite habitual del backend: `cd ../backend; mvn test`. Si el lanzador se interrumpe de forma abrupta y no puede limpiar, use el nombre de proyecto mostrado por Docker en `docker compose -p <nombre> -f compose.yml down --rmi local`. No ejecute comandos de limpieza globales de Docker.

## Catálogo: progresión hasta 10.000 libros

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\ejecutar.ps1 -Catalogo
```

El mismo entorno efímero realiza en orden 1.000, 5.000 y 10.000 libros y 1, 5 y 10 usuarios. Una verificación obligatoria precede cada carga de datos y cada simulación. Cualquier fallo detiene la progresión; el proyecto propio se limpia al terminar. Los cuatro libros incluidos por Flyway V4 se retiran solo de la base efímera antes de generar datos. Se generan datos deterministas mediante la semilla `biblioteca-semilla-2026`, ISBN con dígito de control, categorías y ejemplares variados; los conjuntos mayores conservan los menores. No se generan 100.000 libros. Al llegar a 10.000 se usa temporalmente un título existente para verificar el marcador y se restaura, para no superar el límite.

Cada ejecución incluye una rampa de 2 segundos, 18 segundos de concurrencia configurada, un calentamiento identificado durante 5 segundos por usuario y hasta 12 segundos de consultas con pausa de un segundo. El límite global es de 22 segundos y 10 usuarios simultáneos. El informe global incluye login y calentamiento; los informes por operación distinguen las consultas medidas. Las ventanas son breves y se solapan durante la rampa; no representan una capacidad máxima ni un período estable prolongado. Los informes HTML quedan en `target/gatling`, y el orden y las rutas se registran en la salida del comando. El log de esta ejecución se puede conservar mediante redirección a un archivo local.
