
$ErrorActionPreference = "Stop"

$contenedor = "biblioteca-postgres"
$datos = Join-Path $PSScriptRoot "..\data\export_1000"

$archivos = @(
    "libros_import.csv",
    "materias_import.csv",
    "libro_materias_import.csv"
)

foreach ($archivo in $archivos) {
    $origen = Join-Path $datos $archivo

    if (-not (Test-Path $origen)) {
        throw "No existe el archivo: $origen"
    }

    docker cp $origen "${contenedor}:/tmp/$archivo"

    if ($LASTEXITCODE -ne 0) {
        throw "Error copiando $archivo"
    }
}

$sql = Join-Path $PSScriptRoot "importar_catalogo.sql"

docker cp $sql "${contenedor}:/tmp/importar_catalogo.sql"

if ($LASTEXITCODE -ne 0) {
    throw "Error copiando el script SQL"
}

Write-Host "Importando catálogo..."

docker exec $contenedor psql `
    -X `
    -v ON_ERROR_STOP=1 `
    -U biblioteca_user `
    -d biblioteca_db `
    -f /tmp/importar_catalogo.sql

if ($LASTEXITCODE -ne 0) {
    throw "La importación ha fallado"
}

Write-Host "Importación completada correctamente."
