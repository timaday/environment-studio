import { defineConfig, devices } from "@playwright/test";

const hosted = process.env.ES_HOSTED_BROWSER === "1";
const planJourney = process.env.ES_HOSTED_BROWSER_MODE === "plans-v3";
const definitionJourney = process.env.ES_HOSTED_BROWSER_MODE === "definitions-v3";
const hostedOutput = process.env.ES_HOSTED_BROWSER_OUTPUT_DIR;
if (
  hosted &&
  (!hostedOutput || !/^\/dev\/shm\/es-browser-results-[A-Za-z0-9_-]+$/.test(hostedOutput))
)
  throw new Error("HOSTED_BROWSER_PRIVATE_OUTPUT_REQUIRED");
// Suppress automatic failure DOM snapshots in the credential/raw-document harness.
if (hosted) process.env.PLAYWRIGHT_NO_COPY_PROMPT = "1";
export default defineConfig({
  testDir: "./e2e",
  outputDir: hosted ? hostedOutput : "test-results",
  testMatch: hosted
    ? planJourney
      ? "v3-plan-inspection.spec.ts"
      : definitionJourney
        ? "v3-definitions.spec.ts"
        : "hosted-workflow.spec.ts"
    : "definition-review.spec.ts",
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  reporter: "list",
  use: {
    baseURL: hosted ? "https://localhost:18443" : "http://127.0.0.1:4173",
    trace: "off",
    screenshot: "off",
    video: "off",
    ignoreHTTPSErrors: hosted,
  },
  projects: [
    {
      name: "desktop",
      use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 1000 } },
    },
    {
      name: "narrow",
      use: { ...devices["Desktop Chrome"], viewport: { width: 390, height: 844 } },
    },
  ],
  webServer: hosted
    ? undefined
    : {
        command:
          "npm run build && npm exec vite preview -- --host 127.0.0.1 --port 4173 --strictPort",
        url: "http://127.0.0.1:4173",
        reuseExistingServer: false,
        timeout: 60_000,
      },
});
