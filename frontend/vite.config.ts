import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  server: {
    fs: { allow: [".."] },
    proxy: { "^/api(?:/|$)": "http://127.0.0.1:18080" },
  },
  // Preview must not inherit the local development API proxy.
  preview: { proxy: {} },
  test: {
    include: ["src/**/*.test.{ts,tsx}"],
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    coverage: { provider: "v8", reporter: ["text", "lcov"] },
  },
});
