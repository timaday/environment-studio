import { useEffect, useMemo, useState } from "react";
import { ApiFailure, type Diagnostic, failureMessage, type HostedApi } from "../api/hosted";
import {
  type DefinitionList,
  type DefinitionRevision,
  HostedV3Definitions,
  type PreparedDefinitionSave,
  prepareDefinitionSave,
} from "../api/hostedV3Definitions";

/** State for real v3 draft operations; rendering does not own request identity. */
export function useV3Definitions(api: HostedApi, enabled: boolean) {
  const client = useMemo(() => new HostedV3Definitions(api), [api]);
  const scope = useMemo(
    () => ({
      client,
      enabled,
      active: false,
      locked: false,
      epoch: 0,
      pending: null as PreparedDefinitionSave | null,
      fileEpoch: 0,
      reader: null as FileReader | null,
    }),
    [client, enabled],
  );
  const [inventory, setInventory] = useState<DefinitionList | null>(null);
  const [selected, setSelected] = useState<DefinitionRevision | null>(null);
  const [source, setSource] = useState("");
  const [format, setFormat] = useState<"JSON" | "YAML">("JSON");
  const [diagnostics, setDiagnostics] = useState<readonly Diagnostic[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState(false);
  const [reading, setReading] = useState(false);
  const [fileError, setFileError] = useState("");
  const current = (token: number) => scope.active && token === scope.epoch;
  const available = () => scope.enabled && scope.active && !scope.locked;
  useEffect(() => {
    scope.active = true;
    scope.locked = scope.enabled;
    const token = ++scope.epoch;
    setInventory(null);
    setSelected(null);
    setSource("");
    setFormat("JSON");
    setError("");
    setDiagnostics([]);
    setPending(false);
    setReading(false);
    setFileError("");
    setBusy(scope.enabled);
    if (scope.enabled)
      void scope.client
        .definitions()
        .then((value) => {
          if (scope.active && token === scope.epoch) setInventory(value);
        })
        .catch((e) => {
          if (scope.active && token === scope.epoch) setError(failureMessage(e));
        })
        .finally(() => {
          if (scope.active && token === scope.epoch) {
            scope.locked = false;
            setBusy(false);
          }
        });
    return () => {
      scope.active = false;
      scope.epoch++;
      scope.pending = null;
      scope.fileEpoch++;
      scope.reader?.abort();
      scope.reader = null;
    };
  }, [scope]);
  function acknowledged(value: DefinitionRevision) {
    setSelected(value);
    setSource(value.source);
    setFormat(value.format);
    setDiagnostics(value.projection.diagnostics);
  }
  async function refreshList() {
    if (!available() || scope.pending) return;
    const token = ++scope.epoch;
    scope.locked = true;
    setBusy(true);
    setError("");
    try {
      const value = await scope.client.definitions();
      if (current(token)) setInventory(value);
    } catch (e) {
      if (current(token)) setError(failureMessage(e));
    } finally {
      if (current(token)) {
        scope.locked = false;
        setBusy(false);
      }
    }
  }
  async function load(objectId: string) {
    if (!available() || scope.pending) return;
    cancelFileRead();
    const token = ++scope.epoch;
    scope.locked = true;
    setBusy(true);
    setError("");
    try {
      const value = await scope.client.definition(objectId);
      if (current(token)) acknowledged(value);
    } catch (e) {
      if (current(token)) setError(failureMessage(e));
    } finally {
      if (current(token)) {
        scope.locked = false;
        setBusy(false);
      }
    }
  }
  async function execute(command: PreparedDefinitionSave) {
    if (!available()) return;
    // Once sent, the old inventory cannot establish absence, even after response loss.
    setInventory(null);
    const token = ++scope.epoch;
    scope.locked = true;
    setBusy(true);
    setError("");
    try {
      const value = await scope.client.saveDefinition(command);
      if (!current(token)) return;
      acknowledged(value);
      scope.pending = null;
      setPending(false);
      // A list-refresh failure does not make an acknowledged save uncertain.
      try {
        const list = await scope.client.definitions();
        if (current(token)) setInventory(list);
      } catch (e) {
        if (current(token)) setError(failureMessage(e));
      }
    } catch (e) {
      if (!current(token)) return;
      setError(failureMessage(e));
      setDiagnostics(e instanceof ApiFailure ? e.diagnostics : []);
      if (e instanceof ApiFailure && e.code === "SESSION_REQUIRED") {
        scope.pending = null;
        setPending(false);
        setSelected(null);
        setSource("");
        setInventory(null);
      } else {
        // FORBIDDEN/TOO_LARGE can occur while encoding an already committed save.
        // Only closed pre-commit outcomes permit a different command.
        const refused =
          e instanceof ApiFailure &&
          [
            "400:INVALID_REQUEST",
            "404:NOT_FOUND",
            "409:CONFLICT",
            "422:REJECTED",
            "429:CAPACITY",
          ].includes(`${e.status}:${e.code}`);
        scope.pending = refused ? null : command;
        setPending(!refused);
      }
    } finally {
      if (current(token)) {
        scope.locked = false;
        setBusy(false);
      }
    }
  }
  async function save() {
    if (!available() || scope.pending) return;
    cancelFileRead();
    let command: PreparedDefinitionSave;
    try {
      command = prepareDefinitionSave(selected?.objectId ?? crypto.randomUUID(), {
        expectedRevision: selected?.workspaceRevision ?? "0",
        requestId: crypto.randomUUID(),
        format,
        source,
      });
    } catch (e) {
      setError(failureMessage(e));
      return;
    }
    await execute(command);
  }
  async function retry() {
    if (scope.pending) await execute(scope.pending);
  }
  function editSource(value: string) {
    if (available() && !scope.pending) {
      cancelFileRead();
      setSource(value);
    }
  }
  function editFormat(value: "JSON" | "YAML") {
    if (available() && !scope.pending) {
      cancelFileRead();
      setFormat(value);
    }
  }
  function newDefinition() {
    if (!available() || scope.pending) return;
    cancelFileRead();
    setSelected(null);
    setSource("");
    setFormat("JSON");
    setDiagnostics([]);
    setError("");
    setFileError("");
  }
  function cancelFileRead() {
    scope.fileEpoch++;
    scope.reader?.abort();
    scope.reader = null;
    if (scope.active) setReading(false);
  }
  function upload(file: File) {
    if (!available() || scope.pending) return;
    cancelFileRead();
    const nextFormat = /\.json$/i.test(file.name)
      ? "JSON"
      : /\.ya?ml$/i.test(file.name)
        ? "YAML"
        : null;
    if (!nextFormat) {
      setFileError("Choose a JSON or YAML definition file.");
      return;
    }
    if (file.size > 1_048_576) {
      setFileError("Source exceeds 1 MiB.");
      return;
    }
    const token = scope.fileEpoch;
    const reader = new FileReader();
    scope.reader = reader;
    setReading(true);
    setFileError("");
    const ownsRead = () => scope.active && scope.reader === reader && token === scope.fileEpoch;
    const finish = () => {
      scope.reader = null;
      setReading(false);
    };
    reader.onload = () => {
      if (!ownsRead()) return;
      try {
        if (
          !(reader.result instanceof ArrayBuffer) ||
          reader.result.byteLength !== file.size ||
          reader.result.byteLength > 1_048_576
        )
          throw new Error("Invalid file read");
        const text = new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(
          new Uint8Array(reader.result),
        );
        setSource(text);
        setFormat(nextFormat);
      } catch {
        setFileError("The definition file could not be read as UTF-8. Existing source was kept.");
      }
      finish();
    };
    reader.onabort = () => {
      if (!ownsRead()) return;
      setFileError("The definition file read was cancelled. Existing source was kept.");
      finish();
    };
    reader.onerror = () => {
      if (!ownsRead()) return;
      setFileError("The definition file could not be read. Existing source was kept.");
      finish();
    };
    try {
      reader.readAsArrayBuffer(file);
    } catch {
      if (ownsRead()) {
        setFileError("The definition file could not be read. Existing source was kept.");
        finish();
      }
    }
  }
  return {
    upload,
    cancelFileRead,
    reading,
    fileError,
    dirty: source !== (selected?.source ?? "") || format !== (selected?.format ?? "JSON"),
    inventory,
    selected,
    source,
    format,
    diagnostics,
    error,
    busy,
    pending,
    setSource: editSource,
    setFormat: editFormat,
    save,
    retry,
    load,
    refreshList,
    newDefinition,
  };
}
