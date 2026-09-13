import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import type { HostedApi, Plan } from "../api/hosted";
import { DocumentComparison } from "./Documents";

it("shows absent target as unavailable rather than unchanged and requires disclosure consent", async () => {
  const post = vi.fn().mockResolvedValue({
    revision: "8",
    documents: [
      {
        documentId: "invented.xml",
        currentDigest: "mock-digest",
        targetDigest: null,
        changed: null,
      },
    ],
  });
  render(
    <DocumentComparison
      api={{ post } as unknown as HostedApi}
      plan={
        {
          planId: "mock",
          revision: "8",
          currentCounts: { documents: 1 },
          targetComplete: false,
        } as Plan
      }
    />,
  );
  expect(await screen.findByText(/1 documents · 0 changed · 1 unknown/)).toBeVisible();
  expect(screen.getByRole("region", { name: "Target XML" })).toHaveTextContent(
    "Target unavailable",
  );
  expect(screen.getByRole("button", { name: "Load document comparison" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "Placeholders" })).toBeDisabled();
});
