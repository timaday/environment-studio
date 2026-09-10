import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./styles.css";
import "./hosted/V3Definitions.css";
import "./hosted/V3PlanInspection.css";

const root = document.getElementById("root");
if (!root) throw new Error("MISSING_APP_ROOT");
createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
