
$ErrorActionPreference = "Stop"

$contenedor = "biblioteca-postgres"
$datos = Join-Path $PSScriptRoot "..\data\export_100000"
$sql = Join-Path $PSScriptRoot "importar_catalogo_100k.sql"

if (-not (Test-Path $sql)) {
    throw "Falta el script SQL: $sql"
}

foreach ($archivo in @("libros_import.csv", "materias_import.csv", "libro_materias_import.csv")) {
    $ruta = Join-Path $datos $archivo

    if (-not (Test-Path $ruta)) {
        throw "Falta el CSV: $ruta. Ejecuta antes preparar_catalogo_100k.py"
    }
}

Write-Host "Verificando que el contenedor PostgreSQL esta activo..."

docker inspect -f "{{.State.Running}}" $contenedor

if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL no esta arrancado"
}

# Comprobar que la migracion V5 esta aplicada.
$flyway = docker exec $contenedor psql `
    -X -A -t `
    -U biblioteca_user `
    -d biblioteca_db `
    -c "SELECT COUNT(*) FROM flyway_schema_history WHERE version='5' AND success=true"

if ($LASTEXITCODE -ne 0 -or ($flyway -join "").Trim() -ne "1") {
    throw "Falta la migracion V5. Inicia Spring Boot para aplicar Flyway."
}

# Copiar los tres archivos CSV a PostgreSQL.
foreach ($archivo in @(
    "libros_import.csv",
    "materias_import.csv",
    "libro_materias_import.csv"
)) {
    $ruta = Join-Path $datos $archivo

    Write-Host "Copiando $archivo..."

    docker cp $ruta "${contenedor}:/tmp/$archivo"

    if ($LASTEXITCODE -ne 0) {
        throw "Error al copiar $archivo"
    }
}

# Copiar el SQL de importacion.
docker cp $sql "${contenedor}:/tmp/importar_catalogo_100k.sql"

if ($LASTEXITCODE -ne 0) {
    throw "Error al copiar el SQL"
}

# Ejecutar la importacion.
Write-Host "Importando 100.000 libros en una transaccion..."

docker exec $contenedor psql `
    -X `
    -v ON_ERROR_STOP=1 `
    -U biblioteca_user `
    -d biblioteca_db `
    -f /tmp/importar_catalogo_100k.sql

if ($LASTEXITCODE -ne 0) {
    throw "IMPORTACION FALLIDA: se ha revertido la transaccion"
}

Write-Host "Importacion completada. Puedes ejecutar el script otra vez sin duplicar libros."
