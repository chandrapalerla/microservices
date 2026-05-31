import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    tailwindcss(),
    react(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    // Proxy API calls to the gateway during development to avoid CORS issues
    proxy: {
      '/api': {
        target: 'http://localhost:2027',
        changeOrigin: true,
        secure: false,
      },
      '/auth': {
        target: 'http://localhost:2027',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
