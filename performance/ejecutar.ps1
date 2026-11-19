param([switch]$Catalogo)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$env:BIBLIOTECA_PERF_PROJECT = 'biblioteca-perf-' + [guid]::NewGuid().ToString('N')
$env:PERF_DB_PASSWORD = [guid]::NewGuid().ToString('N')
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
try {
    $listener.Start()
    $env:PERF_HTTP_PORT = [string]$listener.LocalEndpoint.Port
} finally { $listener.Stop() }
$env:BIBLIOTECA_PERF_URL = "http://127.0.0.1:$env:PERF_HTTP_PORT"
$env:BIBLIOTECA_PERF_EMAIL = 'perf-' + [guid]::NewGuid().ToString('N') + '@example.test'
$env:BIBLIOTECA_PERF_PASSWORD = [guid]::NewGuid().ToString('N')
$project = $env:BIBLIOTECA_PERF_PROJECT
$gatlingArgs = @()
try {
    # Gatling no puede propagar rutas de propiedades con espacios en Windows.
    # Conservamos la carpeta real de JLine y usamos su ruta corta solo si existe.
    $mavenCmd = (Get-Command mvn -CommandType Application | Select-Object -First 1).Source
    $jline = Join-Path (Split-Path (Split-Path $mavenCmd)) 'lib\jline-native'
    if (Test-Path -LiteralPath $jline -PathType Container) {
        $shortPath = (New-Object -ComObject Scripting.FileSystemObject).GetFolder($jline).ShortPath
        if ($shortPath -and $shortPath -notmatch '\s' -and (Test-Path -LiteralPath $shortPath -PathType Container)) {
            $gatlingArgs += "-Dlibrary.jline.path=$shortPath"
        }
    }
} catch {
    Write-Warning 'No se pudo obtener la ruta corta de JLine; Maven usará su configuración habitual.'
}
try {
    Push-Location ../backend
    try { & mvn '-DskipTests' package; if ($LASTEXITCODE -ne 0) { throw 'No se pudo construir el backend.' } }
    finally { Pop-Location }
    & docker compose -p $project -f compose.yml up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo arrancar el entorno aislado.' }
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        $logs = & docker logs "$project-backend-1" 2>&1 | Out-String
        if ($logs -match 'Started BackendApplication') { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (!$ready) { throw 'El backend no terminó de arrancar.' }
    if ($Catalogo) {
        foreach ($count in @(1000, 5000, 10000)) {
            & powershell -NoProfile -ExecutionPolicy Bypass -File .\cargar-datos.ps1 -Libros $count
            if ($LASTEXITCODE -ne 0) { throw "Falló la preparación de $count libros." }
            $env:BIBLIOTECA_PERF_LIBROS = "$count"
            foreach ($users in @(1, 5, 10)) {
                $env:BIBLIOTECA_PERF_USUARIOS = "$users"
                Write-Host "Etapa: $count libros, $users usuarios."
                & mvn gatling:test '-Dgatling.simulationClass=com.biblioteca.performance.CatalogoSimulation' "-Dgatling.runDescription=libros-$count-usuarios-$users" @gatlingArgs
                if ($LASTEXITCODE -ne 0) { throw "Etapa fallida: $count libros, $users usuarios. Progresión detenida." }
            }
        }
    } else {
        & mvn gatling:test '-Dgatling.simulationClass=com.biblioteca.performance.SesionInicialSimulation' @gatlingArgs
        if ($LASTEXITCODE -ne 0) { throw 'La simulación o su verificación han fallado.' }
    }
} finally {
    & docker compose -p $project -f compose.yml down --rmi local
    if ($LASTEXITCODE -ne 0) { Write-Warning "No se pudo limpiar el proyecto exclusivo $project." }
    Remove-Item Env:BIBLIOTECA_PERF_PROJECT, Env:BIBLIOTECA_PERF_URL, Env:BIBLIOTECA_PERF_EMAIL, Env:BIBLIOTECA_PERF_PASSWORD, Env:BIBLIOTECA_PERF_LIBROS, Env:BIBLIOTECA_PERF_USUARIOS, Env:PERF_DB_PASSWORD, Env:PERF_HTTP_PORT -ErrorAction SilentlyContinue
}
