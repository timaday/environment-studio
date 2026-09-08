import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import App from "./App";

describe("synthetic comparison workbench", () => {
  it("clearly labels the demo and cannot export", () => {
    render(<App />);
    expect(screen.getByText(/Synthetic example/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Export SQL" })).toBeDisabled();
    expect(
      screen.getByText(/Database inspection and SQL export are not implemented/),
    ).toBeInTheDocument();
  });

  it("navigates across CLOB documents without hiding the total scope", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: /workloads.xml/ }));
    expect(screen.getByText("3 documents · 3 changed")).toBeInTheDocument();
    const current = screen.getByRole("region", { name: "Current XML" });
    const target = screen.getByRole("region", { name: "Target XML" });
    expect(current.textContent).toContain('node="old-01"');
    expect(target.textContent).toContain('node="qa-02"');
  });

  it("placeholder mode retains concrete current and target mapping values", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole("button", { name: "Placeholders" }));
    expect(screen.getByRole("region", { name: "Current XML" }).textContent).toContain(
      "${node-a.server-id}",
    );
    const bindings = screen.getByRole("table", { name: "Environment value mapping" });
    expect(within(bindings).getByText("old-01")).toBeInTheDocument();
    expect(within(bindings).getByText("qa-01")).toBeInTheDocument();
    expect(within(bindings).getByText("qa-02")).toBeInTheDocument();
  });
});
