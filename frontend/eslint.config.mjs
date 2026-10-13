import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // Third-party runtime files copied from node_modules by scripts/copy-wasm.js (gitignored).
    "public/mediapipe/**",
    "public/ort-wasm*.mjs",
  ]),
  {
    // Plain Node scripts run outside the bundle, so CommonJS require() is what they need.
    files: ["scripts/**/*.js"],
    rules: { "@typescript-eslint/no-require-imports": "off" },
  },
  {
    // Advisory from the React Compiler rules; 11 existing effects still set state synchronously.
    // Kept visible as a warning until they are reworked, so CI is not blocked on it.
    rules: { "react-hooks/set-state-in-effect": "warn" },
  },
]);

export default eslintConfig;
