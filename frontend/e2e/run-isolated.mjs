import { randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { createServer } from 'node:net';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';
import { stripVTControlCharacters } from 'node:util';
import path from 'node:path';

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const backend = path.resolve(frontend, '../backend');
const name = `biblioteca-e2e-${randomUUID().slice(0, 12)}`;
const children = [];
let containerId;
function docker(args) { return execFileSync('docker', args, { encoding: 'utf8', timeout: 120_000 }).trim(); }
async function port() {
  const server = createServer();
  await new Promise((resolve, reject) => server.once('error', reject).listen(0, '127.0.0.1', resolve));
  const value = server.address().port;
  await new Promise((resolve) => server.close(resolve));
  return value;
}
function start(command, args, options) {
  const child = spawn(command, args, { ...options, stdio: ['ignore', 'pipe', 'pipe'], shell: process.platform === 'win32' && command.endsWith('.cmd') });
  child.startupOutput = '';
  child.on('error', error => { child.startupError = error; });
  for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => {
    child.startupOutput = (child.startupOutput + chunk.toString()).slice(-200_000);
    (stream === child.stdout ? process.stdout : process.stderr).write(chunk);
  });
  children.push(child);
  return child;
}
async function ready(url, child, started) {
  const end = Date.now() + 150_000;
  let lastCheck = 'aún no se ha reconocido el mensaje de arranque';
  while (Date.now() < end) {
    if (child.startupError) throw child.startupError;
    if (child.exitCode !== null || child.signalCode !== null) {
      throw new Error(`El proceso terminó antes de arrancar: ${url} (código: ${child.exitCode}, señal: ${child.signalCode}; última comprobación: ${lastCheck})`);
    }
    if (started.test(stripVTControlCharacters(child.startupOutput))) {
      try {
        const response = await fetch(url, { signal: AbortSignal.timeout(2_000) });
        lastCheck = `HTTP ${response.status}`;
        if (response.ok) {
          console.log(`Servidor disponible: ${url} (${lastCheck})`);
          return;
        }
      } catch (error) {
        lastCheck = `error de conexión: ${error.cause?.message || error.message}`;
      }
    }
    await delay(500);
  }
  throw new Error(`No arrancó ${url}; última comprobación: ${lastCheck}`);
}
function stop(child) {
  if (!child.pid || child.exitCode !== null) return;
  if (process.platform === 'win32') {
    try { execFileSync('taskkill', ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore' }); } catch { /* ya terminó */ }
  } else child.kill('SIGTERM');
}

try {
  docker(['info', '--format', '{{.ServerVersion}}']);
  containerId = docker(['run', '-d', '--rm', '--name', name, '--label', `biblioteca.e2e=${name}`,
    '-e', 'POSTGRES_DB=biblioteca_e2e', '-e', 'POSTGRES_USER=e2e_user', '-e', 'POSTGRES_PASSWORD=e2e_password',
    '-p', '127.0.0.1::5432', 'postgres:17.11']);
  const mapping = docker(['port', containerId, '5432/tcp']);
  const dbPort = Number(mapping.match(/:(\d+)$/)?.[1]);
  if (!dbPort) throw new Error(`Puerto PostgreSQL inesperado: ${mapping}`);
  let dbReady = false;
  for (let i = 0; i < 60 && !dbReady; i++) {
    try { docker(['exec', containerId, 'pg_isready', '-U', 'e2e_user', '-d', 'biblioteca_e2e']); dbReady = true; }
    catch { await delay(500); }
  }
  if (!dbReady) throw new Error('PostgreSQL E2E no está listo');
  const backendPort = await port(); const frontendPort = await port();
  const env = { ...process.env, SPRING_DATASOURCE_URL: `jdbc:postgresql://127.0.0.1:${dbPort}/biblioteca_e2e`,
    SPRING_DATASOURCE_USERNAME: 'e2e_user', SPRING_DATASOURCE_PASSWORD: 'e2e_password',
    SPRING_DATASOURCE_DRIVER_CLASS_NAME: 'org.postgresql.Driver', SERVER_PORT: String(backendPort),
    DB_PASSWORD: 'e2e_password', E2E_BACKEND_PORT: String(backendPort), E2E_BASE_URL: `http://127.0.0.1:${frontendPort}`,
    E2E_CONTAINER_ID: containerId, E2E_CONTAINER_LABEL: name };
  for (const key of Object.keys(env)) if (/^SPRING_FLYWAY_(URL|USER|PASSWORD)$/i.test(key)) delete env[key];
  delete env.JAVA_TOOL_OPTIONS; delete env.JDK_JAVA_OPTIONS; delete env.SPRING_APPLICATION_JSON;
  const backendProcess = start(process.platform === 'win32' ? 'mvn.cmd' : 'mvn',
    [`-Dmaven.repo.local=${path.join(process.env.USERPROFILE || process.env.HOME, '.m2', 'repository')}`, 'spring-boot:run', '-q'], { cwd: backend, env });
  await ready(`http://127.0.0.1:${backendPort}/api/health`, backendProcess, /Started BackendApplication/);
  execFileSync('docker', ['exec', '-i', containerId, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', 'e2e_user', '-d', 'biblioteca_e2e'], {
    input: `INSERT INTO libros (titulo, autor, categoria) SELECT 'E2E Pagina ' || lpad(i::text, 2, '0'), 'Autor E2E', 'Categoria E2E' FROM generate_series(1, 25) i;
INSERT INTO libros (titulo, autor, categoria) VALUES ('E2E Prestamo', 'Autor E2E', 'Categoria E2E'), ('E2E FIFO', 'Autor E2E', 'Categoria E2E');
INSERT INTO ejemplares (libro_id, codigo) SELECT id, 'COPIA-' || id FROM libros WHERE titulo IN ('E2E Prestamo', 'E2E FIFO');`, stdio: ['pipe', 'inherit', 'inherit'] });
  const vite = start(process.execPath, ['node_modules/vite/bin/vite.js', '--config', 'e2e/vite.config.js', '--host', '127.0.0.1', '--port', String(frontendPort), '--strictPort'], { cwd: frontend, env });
  await ready(env.E2E_BASE_URL, vite, /VITE v.*ready in/);
  const tests = start(process.execPath, ['node_modules/@playwright/test/cli.js', 'test'], { cwd: frontend, env });
  const result = await new Promise((resolve, reject) => { tests.once('error', reject); tests.once('exit', resolve); });
  if (result !== 0) process.exitCode = result || 1;
} catch (error) {
  console.error(error);
  process.exitCode = 1;
} finally {
  for (const child of children.reverse()) stop(child);
  if (containerId) {
    try {
      const label = docker(['inspect', '--format', '{{index .Config.Labels "biblioteca.e2e"}}', containerId]);
      if (label === name) docker(['rm', '-f', containerId]);
      else { console.error('La etiqueta del contenedor no coincide; no se ha eliminado'); process.exitCode = 1; }
    } catch (error) { console.error('No se pudo limpiar el contenedor E2E:', error.message); process.exitCode = 1; }
  }
}
