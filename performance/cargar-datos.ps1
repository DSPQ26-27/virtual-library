param([Parameter(Mandatory=$true)][ValidateSet(1000,5000,10000)][int]$Libros)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
& powershell -NoProfile -NonInteractive -File .\verificar-entorno.ps1
if ($LASTEXITCODE -ne 0) { throw 'Verificación fallida; no se cargarán datos.' }
$project = $env:BIBLIOTECA_PERF_PROJECT
if ($project -notmatch '^biblioteca-perf-[a-f0-9]{32}$') { throw 'Proyecto no válido.' }
$sql = @"
BEGIN;
DELETE FROM ejemplares WHERE libro_id IN (SELECT id FROM libros WHERE isbn IN
 ('978-84-376-0494-7','978-84-9759-381-9','978-84-206-5121-6','978-01-3235-088-4'));
DELETE FROM libros WHERE isbn IN
 ('978-84-376-0494-7','978-84-9759-381-9','978-84-206-5121-6','978-01-3235-088-4');
DO `$`$ BEGIN
  IF (SELECT count(*) FROM libros WHERE isbn IS NULL OR isbn !~ '^978[0-9]{10}$') <> 0
     OR (SELECT count(*) FROM libros) > $Libros THEN
    RAISE EXCEPTION 'El catálogo no coincide con el entorno de prueba esperado';
  END IF;
END `$`$;
WITH datos AS (
 SELECT n, '978' || lpad(n::text,9,'0') AS base,
   md5('biblioteca-semilla-2026-' || n::text) AS h
 FROM generate_series(1,$Libros) n
), entradas AS (
 SELECT n, base || ((10 - (SELECT sum(substring(base,i,1)::int * CASE WHEN i % 2 = 0 THEN 3 ELSE 1 END) FROM generate_series(1,12) i) % 10) % 10)::text AS isbn,
 h FROM datos
)
INSERT INTO libros (isbn,titulo,autor,editorial,categoria,anio_publicacion)
SELECT isbn,
 (ARRAY['Atlas de viajes','Historias del mar','Ciencia y futuro','Memorias de ciudad','Jardines del mundo'])[1+(get_byte(decode(h,'hex'),0)%5)] || ' ' || lpad(n::text,5,'0'),
 (ARRAY['Alvarez','Benitez','Carmona','Delgado','Estevez','Fernandez'])[1+(get_byte(decode(h,'hex'),1)%6)] || ', Autor ' || (n%250),
 'Editorial ' || (n%20),
 (ARRAY['Novela','Historia','Ciencia','Viajes','Arte'])[1+(get_byte(decode(h,'hex'),2)%5)],
 1950+(n%75)
FROM entradas ON CONFLICT (isbn) DO NOTHING;
INSERT INTO ejemplares (libro_id,codigo,estado,activo)
SELECT l.id, 'PERF-' || l.isbn, CASE WHEN (substring(l.isbn,4,9)::int % 3)=0 THEN 'PRESTADO' ELSE 'DISPONIBLE' END,
 (substring(l.isbn,4,9)::int % 7)<>0
FROM libros l WHERE l.isbn LIKE '978%' ON CONFLICT (codigo) DO NOTHING;
ANALYZE libros;
ANALYZE ejemplares;
COMMIT;
SELECT count(*) AS libros FROM libros;
"@
$sql | & docker exec -i "$project-db-1" psql -U perf_user -d biblioteca_performance -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron preparar los datos.' }
