import { act, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import App from "../App";
import type { Documents, HostedApi, Plan } from "../api/hosted";
import { DocumentComparison } from "./Documents";

afterEach(() => vi.unstubAllGlobals());
it("does not substitute demo content when capabilities fail", async () => {
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("not exposed")));
  render(<App />);
  expect(await screen.findByRole("heading", { name: "Workspace unavailable" })).toBeVisible();
  expect(screen.queryByText("Synthetic example")).not.toBeInTheDocument();
});
it("rejects malformed capability booleans instead of enabling controls", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          mode: "hosted",
          inspectionEnabled: "true",
          inspectionUiEnabled: false,
          inspectionApiConfigured: true,
          definitionWorkspaceEnabled: true,
          exportEnabled: false,
          blockers: [],
        }),
      ),
    ),
  );
  render(<App />);
  expect(await screen.findByRole("heading", { name: "Workspace unavailable" })).toBeVisible();
});
it("an old inventory cannot replace the new authoritative revision", async () => {
  let resolveOld!: (value: Documents) => void;
  const old = new Promise<Documents>((resolve) => {
    resolveOld = resolve;
  });
  const post = vi
    .fn()
    .mockReturnValueOnce(old)
    .mockResolvedValueOnce({
      revision: "9",
      documents: [
        { documentId: "new.xml", currentDigest: "new", targetDigest: null, changed: null },
      ],
    });
  const api = { post } as unknown as HostedApi;
  const plan = {
    planId: "mock-plan",
    revision: "8",
    currentCounts: { documents: 1 },
    targetComplete: false,
  } as Plan;
  const view = render(<DocumentComparison api={api} plan={plan} />);
  view.rerender(<DocumentComparison api={api} plan={{ ...plan, revision: "9" }} />);
  expect(await screen.findByRole("option", { name: "new.xml · Unknown" })).toBeInTheDocument();
  await act(async () =>
    resolveOld({
      revision: "8",
      documents: [
        { documentId: "old.xml", currentDigest: "old", targetDigest: null, changed: null },
      ],
    }),
  );
  expect(screen.queryByRole("option", { name: /old.xml/ })).not.toBeInTheDocument();
  expect(screen.getByRole("option", { name: /new.xml/ })).toBeInTheDocument();
});
