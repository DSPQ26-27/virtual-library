import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

if (!/^\d+$/.test(process.env.E2E_BACKEND_PORT || '')) throw new Error('Falta el puerto del backend aislado');
export default defineConfig({
  plugins: [react()],
  server: { proxy: { '/api': `http://127.0.0.1:${process.env.E2E_BACKEND_PORT}` } },
});
