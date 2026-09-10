import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import native from "../../../fixtures/native-v3/definition.json";
import { HostedApi } from "../api/hosted";
import { useV3Definitions } from "./useV3Definitions";

// Existing independently invented native-v3 fixture; these are transport tests only.
const source = JSON.stringify(native);
const model = JSON.parse(JSON.stringify(native), (_key, value) =>
  typeof value === "number" ? String(value) : value,
);
const owners: HostedApi[] = [];
afterEach(() => {
  for (const owner of owners) owner.clear();
  owners.length = 0;
});
const json = (body: unknown) =>
  new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } });
function document(objectId: string) {
  return {
    objectId,
    workspaceRevision: "1",
    sourceDigest: "c".repeat(64),
    source,
    format: "JSON",
    schemaVersion: "3",
    compilerVersion: "native-compiler-v3",
    state: "draft",
    projection: {
      kind: "incomplete",
      model,
      logicalDigest: "a".repeat(64),
      bindingDigests: Object.fromEntries(
        model.bindings.map((b: { id: string }) => [b.id, "b".repeat(64)]),
      ),
      mechanisms: {
        "xml-path-v1": "1",
        "xml-span-v1": "1",
        "generic-graph-v1": "1",
        "native-compiler-v3": "1",
        "derived-graph-v1": "1",
      },
      diagnostics: [
        {
          phase: "publication",
          code: "MECHANISM_NOT_QUALIFIED",
          pointer: "",
          message: "Invented incomplete transport fixture.",
        },
      ],
    },
  };
}
async function setup() {
  const fetcher = vi
    .fn<typeof fetch>()
    .mockResolvedValueOnce(
      json({
        authenticated: true,
        csrfHeaderName: "X-CSRF-TOKEN",
        csrfToken: "mock-csrf",
        idleTimeoutSeconds: 900,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      }),
    )
    .mockResolvedValueOnce(json({ definitions: [] }));
  const api = new HostedApi(fetcher);
  owners.push(api);
  await api.session();
  const hook = renderHook(() => useV3Definitions(api, true));
  await waitFor(() => expect(hook.result.current.inventory).toEqual({ definitions: [] }));
  act(() => hook.result.current.setSource(source));
  return { ...hook, api, fetcher };
}
it("retries an uncertain save with the original destination and exact command", async () => {
  const { result, fetcher } = await setup();
  fetcher.mockRejectedValueOnce(new Error("invented response loss"));
  await act(() => result.current.save());
  const originalPath = fetcher.mock.calls[2][0];
  const originalBody = fetcher.mock.calls[2][1]?.body;
  fetcher.mockImplementationOnce(async (path) =>
    json(document(String(path).split("/").at(-1) ?? "")),
  );
  await act(() => result.current.retry());
  expect(fetcher.mock.calls[3][0]).toBe(originalPath);
  expect(fetcher.mock.calls[3][1]?.body).toBe(originalBody);
});
it("does not issue a second save before the first operation settles", async () => {
  const { result, fetcher } = await setup();
  let settle: ((value: Response) => void) | undefined;
  fetcher.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        settle = resolve;
      }),
  );
  let pending: Promise<void> | undefined;
  act(() => {
    pending = result.current.save();
  });
  await act(() => result.current.save());
  const path = String(fetcher.mock.calls[2][0]);
  await act(async () => {
    settle?.(json(document(path.split("/").at(-1) ?? "")));
    await pending;
  });
  expect(fetcher.mock.calls.filter((call) => call[1]?.method === "PUT")).toHaveLength(1);
});
it("keeps acknowledged save separate from a failed inventory refresh", async () => {
  const { result, fetcher } = await setup();
  fetcher.mockImplementationOnce(async (path) =>
    json(document(String(path).split("/").at(-1) ?? "")),
  );
  fetcher.mockRejectedValueOnce(new Error("invented list failure"));
  await act(() => result.current.save());
  expect(result.current.selected?.workspaceRevision).toBe("1");
  expect(result.current.source).toBe(source);
  expect(result.current.pending).toBe(false);
  expect(result.current.error).not.toBe("");
  const calls = fetcher.mock.calls.length;
  await act(() => result.current.retry());
  expect(fetcher).toHaveBeenCalledTimes(calls);
});
it("keeps exact source and diagnostics after a definite refusal without an uncertain retry", async () => {
  const { result, fetcher } = await setup();
  fetcher.mockResolvedValueOnce(
    new Response(
      JSON.stringify({
        kind: "rejected",
        diagnostics: [
          {
            phase: "publication",
            code: "INVALID_SOURCE",
            pointer: "/source",
            message: "Invented source refusal.",
          },
        ],
      }),
      { status: 422, headers: { "Content-Type": "application/json" } },
    ),
  );
  await act(() => result.current.save());
  expect(result.current.pending).toBe(false);
  expect(result.current.selected).toBeNull();
  expect(result.current.source).toBe(source);
  const before = fetcher.mock.calls.length;
  await act(() => result.current.retry());
  expect(fetcher).toHaveBeenCalledTimes(before);
  act(() => result.current.setSource("corrected"));
  expect(result.current.source).toBe("corrected");
  expect(result.current.diagnostics).toEqual([
    {
      phase: "publication",
      code: "INVALID_SOURCE",
      pointer: "/source",
      message: "Invented source refusal.",
    },
  ]);
});
it("locks conflicting edits, selection, reset and new saves while delivery is uncertain", async () => {
  const { result, fetcher } = await setup();
  fetcher.mockRejectedValueOnce(new Error("invented loss"));
  await act(() => result.current.save());
  expect(result.current.pending).toBe(true);
  act(() => {
    result.current.setSource("changed");
    result.current.setFormat("YAML");
    result.current.newDefinition();
  });
  await act(async () => {
    await result.current.load("50000000-0000-0000-0000-000000000002");
    await result.current.refreshList();
    await result.current.save();
  });
  expect(result.current.source).toBe(source);
  expect(result.current.format).toBe("JSON");
  expect(fetcher).toHaveBeenCalledTimes(3);
});
it("distinguishes unavailable inventory from a successfully empty one", async () => {
  const fetcher = vi
    .fn<typeof fetch>()
    .mockResolvedValueOnce(
      json({
        authenticated: true,
        csrfHeaderName: "X-CSRF-TOKEN",
        csrfToken: "mock-csrf",
        idleTimeoutSeconds: 900,
        absoluteExpiresAt: "2099-01-01T00:00:00Z",
      }),
    )
    .mockRejectedValueOnce(new Error("invented list failure"));
  const api = new HostedApi(fetcher);
  owners.push(api);
  await api.session();
  const { result } = renderHook(() => useV3Definitions(api, true));
  await waitFor(() => expect(result.current.busy).toBe(false));
  expect(result.current.inventory).toBeNull();
  expect(result.current.error).not.toBe("");
  fetcher.mockResolvedValueOnce(json({ definitions: [] }));
  await act(() => result.current.refreshList());
  expect(result.current.inventory).toEqual({ definitions: [] });
  expect(result.current.error).toBe("");
});
it("keeps saved source immutable while the editor has an unsaved change", async () => {
  const { result, fetcher } = await setup();
  const id = "50000000-0000-0000-0000-000000000002";
  fetcher.mockResolvedValueOnce(json(document(id)));
  await act(() => result.current.load(id));
  act(() => result.current.setSource("unsaved"));
  expect(result.current.selected?.source).toBe(source);
  expect(result.current.source).toBe("unsaved");
  expect(fetcher.mock.calls.at(-1)?.[0]).toBe(`/api/v3/definitions/${id}`);
});
it("does not restore a late result after disabling the original workspace", async () => {
  const { api, fetcher, unmount } = await setup();
  unmount();
  let settle: ((value: Response) => void) | undefined;
  fetcher.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        settle = resolve;
      }),
  );
  const { result, rerender } = renderHook(({ enabled }) => useV3Definitions(api, enabled), {
    initialProps: { enabled: true },
  });
  rerender({ enabled: false });
  await act(async () => {
    settle?.(json({ definitions: [] }));
  });
  expect(result.current.inventory).toBeNull();
  expect(result.current.selected).toBeNull();
  expect(result.current.busy).toBe(false);
});
it("clears retained editor and retry state when the original API session is revoked", async () => {
  const { result, api, fetcher } = await setup();
  let settle: ((value: Response) => void) | undefined;
  fetcher.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        settle = resolve;
      }),
  );
  let pending: Promise<void> | undefined;
  act(() => {
    pending = result.current.save();
  });
  const id = String(fetcher.mock.calls[2][0]).split("/").at(-1) ?? "";
  api.clear();
  await act(async () => {
    settle?.(json(document(id)));
    await pending;
  });
  expect(result.current.source).toBe("");
  expect(result.current.selected).toBeNull();
  expect(result.current.pending).toBe(false);
});

it.each([
  [403, "FORBIDDEN"],
  [413, "TOO_LARGE"],
] as const)(
  "retains exact replay after possibly committed %s %s output refusal",
  async (status, code) => {
    const { result, fetcher } = await setup();
    fetcher.mockResolvedValueOnce(
      new Response(JSON.stringify({ code }), {
        status,
        headers: { "Content-Type": "application/json" },
      }),
    );
    await act(() => result.current.save());
    expect(result.current.pending).toBe(true);
    const [path, options] = fetcher.mock.calls[2];
    fetcher.mockImplementationOnce(async () =>
      json(document(String(path).split("/").at(-1) ?? "")),
    );
    fetcher.mockResolvedValueOnce(json({ definitions: [] }));
    await act(() => result.current.retry());
    expect(fetcher.mock.calls[3][0]).toBe(path);
    expect(fetcher.mock.calls[3][1]?.body).toBe(options?.body);
    expect(result.current.selected?.workspaceRevision).toBe("1");
    expect(result.current.pending).toBe(false);
  },
);

it("preserves exact UTF-8 BOM, Unicode and line endings from an uploaded file", async () => {
  const { result, fetcher } = await setup();
  const exact = "\ufeff# Invented\r\nlabel: café 😀\r\n";
  act(() => result.current.upload(new File([exact], "invented.YAML")));
  await waitFor(() => expect(result.current.reading).toBe(false));
  expect(result.current.source).toBe(exact);
  expect(result.current.format).toBe("YAML");
  expect(result.current.fileError).toBe("");
  expect(fetcher).toHaveBeenCalledTimes(2);
});
it.each([
  ["malformed", () => new File([new Uint8Array([0xc3, 0x28])], "invented.json")],
  ["oversized", () => new File([new Uint8Array(1_048_577)], "invented.json")],
  ["unsupported", () => new File(["{}"], "invented.xml")],
] as const)("keeps the editor after a %s file", async (_name, file) => {
  const { result } = await setup();
  act(() => result.current.upload(file()));
  await waitFor(() => expect(result.current.reading).toBe(false));
  expect(result.current.source).toBe(source);
  expect(result.current.format).toBe("JSON");
  expect(result.current.fileError).not.toBe("");
});
it("settles an aborted file read without replacing or locking the editor", async () => {
  const { result } = await setup();
  let reader: FileReader | undefined;
  const read = vi.spyOn(FileReader.prototype, "readAsArrayBuffer").mockImplementation(function (
    this: FileReader,
  ) {
    reader = this;
  });
  try {
    act(() => result.current.upload(new File(["{}"], "invented.json")));
    expect(result.current.reading).toBe(true);
    act(() => {
      reader?.dispatchEvent(new ProgressEvent("abort"));
    });
    expect(result.current.reading).toBe(false);
    expect(result.current.source).toBe(source);
    expect(result.current.fileError).not.toBe("");
  } finally {
    read.mockRestore();
  }
});
it.each(["current", "edit", "new", "version", "unmount"] as const)(
  "ignores late file completion after %s",
  async (action) => {
    const { result, unmount } = await setup();
    let reader: FileReader | undefined;
    const read = vi.spyOn(FileReader.prototype, "readAsArrayBuffer").mockImplementation(function (
      this: FileReader,
    ) {
      reader = this;
    });
    try {
      act(() => result.current.upload(new File(["{}"], "invented.json")));
      const late = reader?.onload;
      act(() => {
        if (action === "edit") result.current.setSource("newer edit");
        if (action === "new") result.current.newDefinition();
        if (action === "version") result.current.cancelFileRead();
        if (action === "unmount") unmount();
      });
      const expected = action === "current" ? "{}" : result.current.source;
      if (!reader) throw new Error("Missing fixture reader");
      const finishedReader = reader;
      const bytes = new ArrayBuffer(2);
      new Uint8Array(bytes).set([123, 125]);
      Object.defineProperty(finishedReader, "result", { value: bytes });
      act(() => {
        late?.call(finishedReader, new ProgressEvent("load") as ProgressEvent<FileReader>);
      });
      expect(result.current.source).toBe(expected);
    } finally {
      read.mockRestore();
    }
  },
);
it("refuses a file while an uncertain save is awaiting exact replay", async () => {
  const { result, fetcher } = await setup();
  fetcher.mockRejectedValueOnce(new Error("invented loss"));
  await act(() => result.current.save());
  const read = vi.spyOn(FileReader.prototype, "readAsArrayBuffer");
  try {
    act(() => result.current.upload(new File(["{}"], "invented.json")));
    expect(read).not.toHaveBeenCalled();
    expect(result.current.pending).toBe(true);
    expect(result.current.source).toBe(source);
  } finally {
    read.mockRestore();
  }
});

it("refuses an oversized file before allocating a reader or starting IO", async () => {
  const { result } = await setup();
  const read = vi.spyOn(FileReader.prototype, "readAsArrayBuffer");
  try {
    act(() => result.current.upload(new File([new Uint8Array(1_048_577)], "invented.json")));
    expect(read).not.toHaveBeenCalled();
    expect(result.current.reading).toBe(false);
  } finally {
    read.mockRestore();
  }
});

it.each([
  { code: "REJECTED" },
  { kind: "rejected", diagnostics: [] },
  {
    kind: "rejected",
    diagnostics: [
      {
        phase: "parse",
        code: "INVALID_SOURCE",
        pointer: "",
        message: "Invented refusal.",
        extra: true,
      },
    ],
  },
  {
    kind: "rejected",
    diagnostics: [
      { phase: "parse", code: "INVALID_SOURCE", pointer: "", message: "Invented refusal." },
    ],
    code: "REJECTED",
  },
])("retains exact uncertain replay for an unrecognized semantic422 envelope %#", async (body) => {
  const { result, fetcher } = await setup();
  fetcher.mockResolvedValueOnce(new Response(JSON.stringify(body), { status: 422 }));
  await act(() => result.current.save());
  expect(result.current.pending).toBe(true);
  act(() => result.current.setSource("must not replace an uncertain command"));
  expect(result.current.source).toBe(source);
  const sent = fetcher.mock.calls[2];
  fetcher.mockRejectedValueOnce(new Error("invented replay response loss"));
  await act(() => result.current.retry());
  expect(fetcher.mock.calls[3][0]).toBe(sent[0]);
  expect(fetcher.mock.calls[3][1]?.body).toBe(sent[1]?.body);
});
