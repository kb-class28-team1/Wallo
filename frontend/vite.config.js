import { fileURLToPath, URL } from "node:url"
import vue from "@vitejs/plugin-vue"
import { defineConfig } from "vite"

export default defineConfig(({ mode }) => ({
  plugins: [
    vue({
      template: {
        transformAssetUrls: mode === "test" ? false : undefined,
      },
    }),
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    proxy: {
      "/api": "http://localhost:8080",
      "/ws": {
        target: "ws://localhost:8080",
        ws: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: false,
  },
}))
