import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { DefinitionInspector } from "./DefinitionInspector";

// Independently invented display-only cases; no private model or database provenance.
const result = {
  kind: "incomplete" as const,
  revision: "900719925474099312345" as const,
  source: '  {"invented": "<widget>&"}\n',
  diagnostics: [
    {
      phase: "publication" as const,
      code: "IDENTITY_REQUIRED",
      pointer: "",
      message: "Declare identity semantics.",
    },
    {
      phase: "publication" as const,
      code: "SENSITIVITY_REQUIRED",
      pointer: "/entityTypes/0",
      message: "Declare sensitivity.",
    },
  ],
  model: {
    entityTypes: [{ id: "glimmer", fields: [{ id: "shade", valueType: "text" }] }],
    relations: [{ id: "echo", fromType: "glimmer", toType: "glimmer" }],
  },
};

describe("definition inspection presentation", () => {
  it("retains exact revision and every blocker through keyboard tabs", async () => {
    const user = userEvent.setup();
    render(<DefinitionInspector state={result} />);
    expect(screen.getByRole("tabpanel", { name: "Model" })).toHaveTextContent("glimmer");
    const assertBlockers = () => {
      expect(screen.getByText(`Revision ${result.revision}`)).toBeVisible();
      const blockers = screen.getByRole("region", { name: "Compilation and publication blockers" });
      expect(within(blockers).getByText("IDENTITY_REQUIRED")).toBeVisible();
      expect(within(blockers).getByText("SENSITIVITY_REQUIRED")).toBeVisible();
    };
    assertBlockers();
    const model = screen.getByRole("tab", { name: "Model" });
    model.focus();
    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: "Source" })).toHaveFocus();
    expect(
      screen.getByRole("tabpanel", { name: "Source" }).querySelector("code")?.textContent,
    ).toBe(result.source);
    expect(screen.getByRole("tabpanel", { name: "Source" }).querySelector("widget")).toBeNull();
    assertBlockers();
    await user.tab();
    expect(screen.getByRole("tabpanel", { name: "Source" })).toHaveFocus();
    await user.tab({ shift: true });
    await user.keyboard("{End}");
    expect(screen.getByRole("tab", { name: "Diagnostics" })).toHaveFocus();
    expect(screen.getByRole("tabpanel", { name: "Diagnostics" })).toHaveTextContent(
      "2 diagnostics",
    );
    assertBlockers();
    await user.keyboard("{ArrowRight}");
    expect(model).toHaveFocus();
    await user.keyboard("{ArrowLeft}{Home}");
    expect(model).toHaveFocus();
    expect(screen.getByText(`Revision ${result.revision}`)).toBeVisible();
    const blockers = screen.getByRole("region", { name: "Compilation and publication blockers" });
    expect(within(blockers).getByText("IDENTITY_REQUIRED")).toBeVisible();
    expect(within(blockers).getByText("SENSITIVITY_REQUIRED")).toBeVisible();
  });

  it("rejected results never display a partial model and retain safe diagnostics", async () => {
    const user = userEvent.setup();
    const { rerender } = render(<DefinitionInspector state={result} />);
    rerender(
      <DefinitionInspector
        state={{
          kind: "rejected",
          revision: "2",
          source: "{}",
          diagnostics: [
            {
              phase: "shape",
              code: "SHAPE_INVALID",
              pointer: "",
              message: "Supply required properties.",
            },
          ],
        }}
      />,
    );
    expect(screen.getByRole("tabpanel", { name: "Model" })).toHaveTextContent(
      "No model is available for this rejected result.",
    );
    expect(screen.queryByText("glimmer")).not.toBeInTheDocument();
    expect(screen.getByText("SHAPE_INVALID")).toBeVisible();
    await user.click(screen.getByRole("tab", { name: "Source" }));
    expect(screen.getByRole("tabpanel", { name: "Source" })).toHaveTextContent("{}");
    expect(screen.getByText("SHAPE_INVALID")).toBeVisible();
  });

  it("distinguishes unavailable and loading and removes a previous result", () => {
    const { rerender } = render(<DefinitionInspector state={result} />);
    rerender(<DefinitionInspector state={{ kind: "loading" }} />);
    expect(screen.getByRole("status")).toHaveTextContent("Loading definition result");
    expect(screen.queryByText(/Revision/)).not.toBeInTheDocument();
    expect(screen.queryByRole("tab")).not.toBeInTheDocument();
    rerender(<DefinitionInspector state={{ kind: "unavailable" }} />);
    expect(screen.getByRole("status")).toHaveTextContent("Definition result unavailable");
  });
});
