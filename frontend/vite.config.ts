import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Сборка кладёт результат в статику Spring-приложения, поэтому фронт и бэк
 * живут на одном origin: сессионная cookie и CSRF работают без CORS.
 *
 * В dev-режиме Vite проксирует /api и /login на запущенное приложение (:9091),
 * так что разработка идёт с горячей перезагрузкой против реальных данных.
 */
export default defineConfig({
  plugins: [react()],
  base: '/app-next/',
  build: {
    outDir: '../demo/src/main/resources/static/app-next',
    emptyOutDir: true,
    sourcemap: false
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:9091',
      '/login': 'http://localhost:9091',
      '/risk': 'http://localhost:9091',
      '/app': 'http://localhost:9091'
    }
  }
});
