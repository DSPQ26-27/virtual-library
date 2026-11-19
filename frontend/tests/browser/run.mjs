import { spawn, execFileSync } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';

const vite = spawn(process.execPath, ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '5179', '--strictPort'], { stdio: 'inherit' });
let tests;
try {
  let ready = false;
  for (let i = 0; i < 60 && !ready; i++) {
    if (vite.exitCode !== null) throw new Error('No se pudo iniciar Vite en el puerto 5179');
    try { ready = (await fetch('http://127.0.0.1:5179')).ok; } catch { await delay(250); }
  }
  if (!ready) throw new Error('Vite no está listo');
  tests = spawn(process.execPath, ['node_modules/@playwright/test/cli.js', 'test', '--config=playwright.mock.config.js'], { stdio: 'inherit' });
  const result = await new Promise((resolve, reject) => { tests.once('error', reject); tests.once('exit', resolve); });
  if (result !== 0) process.exitCode = result || 1;
} catch (error) {
  console.error(error); process.exitCode = 1;
} finally {
  for (const child of [tests, vite]) {
    if (!child?.pid || child.exitCode !== null) continue;
    if (process.platform === 'win32') {
      try { execFileSync('taskkill', ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore' }); } catch { /* ya terminó */ }
    } else child.kill('SIGTERM');
  }
}
