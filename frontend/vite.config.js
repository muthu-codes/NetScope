import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev:   npm run dev   -> http://localhost:5173 (API + WebSocket are proxied to Spring Boot on 8080)
// Build: npm run build -> writes into ../src/main/resources/static so one Spring Boot jar serves everything.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: false },
      '/ws': { target: 'http://127.0.0.1:8080', ws: true, changeOrigin: false }
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 900
  }
});
