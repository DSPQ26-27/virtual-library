$ErrorActionPreference = 'Stop'
try {
    $project = $env:BIBLIOTECA_PERF_PROJECT
    $url = $env:BIBLIOTECA_PERF_URL
    $email = $env:BIBLIOTECA_PERF_EMAIL
    $password = $env:BIBLIOTECA_PERF_PASSWORD
    if ($project -notmatch '^biblioteca-perf-[a-f0-9]{32}$' -or !$email -or !$password) { throw 'Faltan el proyecto exclusivo o las credenciales de prueba.' }
    $uri = [uri]$url
    if ($uri.Scheme -ne 'http' -or $uri.Host -cne '127.0.0.1' -or $uri.Port -eq 8080 -or $uri.Port -lt 1 -or $uri.AbsolutePath -ne '/' -or $uri.Query -or $uri.Fragment -or $uri.UserInfo) { throw 'URL no permitida.' }

    function Docker-Json([string[]]$arguments) {
        $result = & docker @arguments 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'Falló la consulta a Docker.' }
        return ($result | ConvertFrom-Json)
    }
    $db = @(Docker-Json @('inspect', "$project-db-1"))[0]
    $app = @(Docker-Json @('inspect', "$project-backend-1"))[0]
    foreach ($item in @($db, $app)) {
        if (!$item.State.Running -or $item.Config.Labels.'com.docker.compose.project' -cne $project) { throw 'Contenedor ajeno al proyecto o detenido.' }
    }
    if ($db.Config.Labels.'com.docker.compose.service' -cne 'db' -or $app.Config.Labels.'com.docker.compose.service' -cne 'backend' -or $db.Config.Image -cne 'postgres:17') { throw 'Servicios o imagen inesperados.' }
    if ($app.Config.Image -cne "$project-backend" -or (($app.Config.Entrypoint -join '|') -cne 'java|-jar|/app/app.jar') -or ($null -ne $app.Config.Cmd -and @($app.Config.Cmd).Count -ne 0)) { throw 'Imagen o comando del backend inesperados.' }
    if (@($db.Mounts).Count -ne 0 -or @($app.Mounts).Count -ne 0 -or !$db.HostConfig.Tmpfs.'/var/lib/postgresql/data' -and $null -eq $db.HostConfig.Tmpfs.'/var/lib/postgresql/data') { throw 'Almacenamiento no temporal o montajes inesperados.' }
    if ($db.HostConfig.NetworkMode -eq 'host' -or $app.HostConfig.NetworkMode -eq 'host' -or $db.HostConfig.Privileged -or $app.HostConfig.Privileged) { throw 'Modo de red o privilegios inseguros.' }
    if ($db.NetworkSettings.Ports.'5432/tcp' -or $db.HostConfig.PortBindings.PSObject.Properties.Count -gt 0) { throw 'PostgreSQL tiene puertos publicados.' }
    $binding = @($app.NetworkSettings.Ports.'8080/tcp')
    if ($binding.Count -ne 1 -or $binding[0].HostIp -cne '127.0.0.1' -or [int]$binding[0].HostPort -ne $uri.Port -or @($app.HostConfig.PortBindings.PSObject.Properties).Count -ne 1) { throw 'El puerto HTTP no coincide con el contenedor esperado.' }
    $networkName = "$project`_perf"
    if (@($db.NetworkSettings.Networks.PSObject.Properties).Count -ne 1 -or @($app.NetworkSettings.Networks.PSObject.Properties).Count -ne 1 -or !$db.NetworkSettings.Networks.$networkName -or !$app.NetworkSettings.Networks.$networkName) { throw 'Redes inesperadas.' }
    if ('db' -cnotin @($db.NetworkSettings.Networks.$networkName.Aliases) -or 'db' -cin @($app.NetworkSettings.Networks.$networkName.Aliases)) { throw 'Alias de base de datos inesperado.' }
    $network = @(Docker-Json @('network', 'inspect', $networkName))[0]
    if ($network.Labels.'com.docker.compose.project' -cne $project -or @($network.Containers.PSObject.Properties).Count -ne 2 -or !$network.Containers.($db.Id) -or !$network.Containers.($app.Id)) { throw 'La red no es exclusiva del proyecto.' }
    $dbEnv = @($db.Config.Env)
    $appEnv = @($app.Config.Env)
    foreach ($required in @('POSTGRES_DB=biblioteca_performance', 'POSTGRES_USER=perf_user')) { if ($required -cnotin $dbEnv) { throw 'Configuración PostgreSQL inesperada.' } }
    foreach ($required in @('SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/biblioteca_performance', 'SPRING_DATASOURCE_USERNAME=perf_user')) { if ($required -cnotin $appEnv) { throw 'Conexión configurada del backend inesperada.' } }
    if (@($appEnv | Where-Object { $_ -match '^(SPRING_|JAVA_TOOL_OPTIONS=|JDK_JAVA_OPTIONS=|_JAVA_OPTIONS=)' -and $_ -notmatch '^SPRING_DATASOURCE_(URL|USERNAME|PASSWORD)=' }).Count -gt 0) { throw 'Configuración adicional de Spring o Java no permitida.' }

    # El UUID solo contiene caracteres hexadecimales; no se interpolan datos del usuario en SQL.
    $marker = 'perf-' + [guid]::NewGuid().ToString('N')
    $count = & docker exec $db.Id psql -U perf_user -d biblioteca_performance -At -c 'SELECT count(*) FROM libros;'
    if ($LASTEXITCODE -ne 0 -or $count -notmatch '^\d+$' -or [int]$count -gt 10000) { throw 'Número de libros no permitido.' }
    $restoreId = $null
    if ([int]$count -eq 10000) {
        $original = & docker exec $db.Id psql -U perf_user -d biblioteca_performance -At -c "SELECT id || ':' || encode(convert_to(titulo, 'UTF8'), 'base64') FROM libros ORDER BY id LIMIT 1;"
        if ($LASTEXITCODE -ne 0 -or $original -notmatch '^(\d+):([A-Za-z0-9+/=]+)$') { throw 'No se puede guardar el título temporal.' }
        $restoreId = $Matches[1]; $restoreTitle = $Matches[2]
        & docker exec $db.Id psql -U perf_user -d biblioteca_performance -v ON_ERROR_STOP=1 -c "UPDATE libros SET titulo = '$marker' WHERE id = $restoreId;" | Out-Null
    } else {
        & docker exec $db.Id psql -U perf_user -d biblioteca_performance -v ON_ERROR_STOP=1 -c "INSERT INTO libros (titulo, autor) VALUES ('$marker', 'Marcador de rendimiento');" | Out-Null
    }
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo insertar el marcador en la base validada.' }

    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $registration = @{ nombre='Estudiante rendimiento'; email=$email; password=$password } | ConvertTo-Json -Compress
    try { Invoke-RestMethod -Method Post -Uri "$url/api/auth/register" -WebSession $session -ContentType 'application/json' -Body $registration | Out-Null }
    catch { if ([int]$_.Exception.Response.StatusCode -ne 409) { throw } }
    $csrf = Invoke-RestMethod -Uri "$url/api/auth/csrf" -WebSession $session
    $oldCookie = ($session.Cookies.GetCookies($url) | Where-Object Name -eq 'JSESSIONID').Value
    $login = @{ email=$email; password=$password } | ConvertTo-Json -Compress
    $user = Invoke-RestMethod -Method Post -Uri "$url/api/auth/login" -WebSession $session -Headers @{'X-CSRF-TOKEN'=$csrf.token} -ContentType 'application/json' -Body $login
    $newCookie = ($session.Cookies.GetCookies($url) | Where-Object Name -eq 'JSESSIONID').Value
    $csrf2 = Invoke-RestMethod -Uri "$url/api/auth/csrf" -WebSession $session
    $catalog = Invoke-RestMethod -Uri "$url/api/libros?q=$marker" -WebSession $session
    if ($user.email -cne $email -or !$oldCookie -or !$newCookie -or $oldCookie -ceq $newCookie -or !$csrf.token -or $csrf.token -ceq $csrf2.token -or @($catalog.content).Count -ne 1 -or $catalog.content[0].titulo -cne $marker) { throw 'La identidad del catálogo o la sesión no coinciden.' }
    $oldTokenRejected = $false
    try { Invoke-WebRequest -Method Post -Uri "$url/api/auth/logout" -WebSession $session -Headers @{'X-CSRF-TOKEN'=$csrf.token} -UseBasicParsing | Out-Null }
    catch { if ([int]$_.Exception.Response.StatusCode -eq 403) { $oldTokenRejected = $true } else { throw } }
    if (!$oldTokenRejected) { throw 'El token anterior no fue invalidado tras el login.' }
    $logout = Invoke-WebRequest -Method Post -Uri "$url/api/auth/logout" -WebSession $session -Headers @{'X-CSRF-TOKEN'=$csrf2.token} -UseBasicParsing
    if ($logout.StatusCode -ne 204) { throw 'No se pudo cerrar la sesión de verificación.' }
    if ($restoreId) {
        & docker exec $db.Id psql -U perf_user -d biblioteca_performance -v ON_ERROR_STOP=1 -c "UPDATE libros SET titulo = convert_from(decode('$restoreTitle','base64'),'UTF8') WHERE id = $restoreId AND titulo = '$marker';" | Out-Null
    } else {
        & docker exec $db.Id psql -U perf_user -d biblioteca_performance -v ON_ERROR_STOP=1 -c "DELETE FROM libros WHERE titulo = '$marker' AND autor = 'Marcador de rendimiento';" | Out-Null
    }
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo retirar el marcador temporal.' }
    Write-Host 'Entorno verificado: contenedores, base temporal, conexión HTTP, sesión y marcador.'
    exit 0
} catch {
    Write-Error "Verificación de seguridad fallida: $($_.Exception.Message)" -ErrorAction Continue
    exit 1
}
