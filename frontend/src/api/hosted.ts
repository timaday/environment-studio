import { workspaceRejection } from "./workspaceRejection";

// Closed early-refusal codes from the hosted v3 plan HTTP contract.
const v3ControllerCodes = new Set([
  "BODY_DEADLINE",
  "BODY_TOO_LARGE",
  "CANCELLED",
  "CAPACITY",
  "CLEANUP_INCONCLUSIVE",
  "CONFLICT",
  "CREDENTIALS_ALREADY_CONSUMED",
  "DESTINATION_DENIED",
  "DISCLOSURE_REQUIRED",
  "EXPORT_UNAVAILABLE",
  "INCOMPLETE_TARGET",
  "INSPECTION_REQUIRED",
  "INVALID_CREDENTIALS",
  "INVALID_DESTINATION",
  "INVALID_REQUEST",
  "MALFORMED_BODY",
  "NOT_FOUND",
  "OBSERVATION_REFUSED",
  "PLAN_BUSY",
  "PLAN_INTERNAL_REFUSAL",
  "PLAN_SERVICES_UNAVAILABLE",
  "PROFILE_REFUSED",
  "PROJECTION_REFUSED",
  "PUBLICATION_REQUIRED",
  "RESERVATION_EXPIRED",
  "RESOURCE_LIMIT",
  "SESSION_REQUIRED",
  "STALE_PREVIEW",
  "UNSUPPORTED_DEFINITION",
]);

export type Capabilities = {
  mode: "demo" | "hosted";
  definitionWorkspaceEnabled: boolean;
  inspectionEnabled: boolean;
  inspectionUiEnabled: boolean;
  inspectionApiConfigured: boolean;
  exportEnabled: boolean;
  blockers: string[];
};
export type Session = {
  authenticated: true;
  csrfHeaderName: string;
  csrfToken: string;
  idleTimeoutSeconds: number;
  absoluteExpiresAt: string;
};
export type Reference = { objectId: string; workspaceRevision: string };
export type Diagnostic = { phase: string; code: string; pointer: string; message: string };
export type DefinitionModel = {
  id: string;
  revision: string;
  logical: {
    entityTypes: { id: string; label: string; fields: { id: string; valueType: string }[] }[];
    relations: { id: string; fromType: string; toType: string }[];
  };
  bindings: { id: string; engine: string; documents: { id: string }[] }[];
};
export type Definition = Reference & {
  source: string;
  sourceDigest: string;
  state: "draft" | "published";
  format: string;
  projection: {
    kind: "incomplete" | "ready-to-publish";
    model: DefinitionModel;
    diagnostics: Diagnostic[];
  };
  publication?: { digest: string };
};
export type DefinitionSummary = Reference & {
  nativeId: string;
  nativeRevision: string;
  state: "draft" | "published";
  compilationKind: string;
};
export type DefinitionList = { definitions: DefinitionSummary[]; canPublish: boolean };
export type Destination = {
  id: string;
  engine: string;
  host: string;
  port: number;
  database: string;
};
export type Counts = { documents: number; entities: number; relations: number };
export type Plan = {
  planId: string;
  revision: string;
  definition: Reference;
  bindingId: string;
  destinationId: string;
  currentCounts: Counts;
  targetCounts: Counts;
  inspectionValid: boolean;
  targetComplete: boolean;
  exportAvailable: false;
  blockers: string[];
  activeOperationId?: string;
};
export type Ack = { planId: string; revision: string; operationId?: string };
export type Operation = {
  operationId: string;
  planId: string;
  phase: string;
  code: string;
  cleanup: string;
  installedRevision?: string;
};
export type Documents = {
  revision: string;
  documents: {
    documentId: string;
    currentDigest: string;
    targetDigest: string | null;
    changed: boolean | null;
  }[];
};
export type DocumentView = {
  revision: string;
  documentId: string;
  side: "current" | "target";
  mode: string;
  text: string;
  exact: boolean;
  redacted: boolean;
  unmappedConcreteMayRemain: boolean;
  omissions: unknown[];
};
export class ApiFailure extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    public readonly diagnostics: Diagnostic[] = [],
  ) {
    super(code);
  }
}
export const definitiveRefusal = (error: unknown) =>
  error instanceof ApiFailure &&
  error.status >= 400 &&
  error.status < 500 &&
  error.code !== "RESPONSE_UNAVAILABLE";
export function checkedAck(value: Ack, operationRequired = false): Ack {
  if (
    !value ||
    typeof value.planId !== "string" ||
    !value.planId ||
    typeof value.revision !== "string" ||
    !/^[1-9][0-9]*$/.test(value.revision) ||
    (operationRequired && (typeof value.operationId !== "string" || !value.operationId))
  )
    throw new ApiFailure(200, "RESPONSE_UNAVAILABLE");
  return value;
}
export class HostedApi {
  private authority: Session | null = null;
  private readonly submitted = new Set<string>();
  private readonly requests = new Set<AbortController>();
  private generation = 0;
  private idleAt = 0;
  private expiryTimer: ReturnType<typeof setTimeout> | undefined;
  constructor(
    private readonly transport: typeof fetch = fetch,
    private readonly expired: () => void = () => {},
  ) {}
  clear() {
    this.generation++;
    clearTimeout(this.expiryTimer);
    this.authority = null;
    this.idleAt = 0;
    this.submitted.clear();
    for (const request of this.requests) request.abort();
    this.requests.clear();
  }
  private end() {
    this.clear();
    this.expired();
  }
  private armExpiry() {
    clearTimeout(this.expiryTimer);
    if (!this.authority) return;
    const remaining = Math.min(
      Date.parse(this.authority.absoluteExpiresAt) - Date.now(),
      this.idleAt - performance.now(),
    );
    this.expiryTimer = setTimeout(
      () => this.end(),
      Math.max(0, Math.min(remaining, 2_147_483_647)),
    );
  }
  async session(): Promise<Session> {
    const started = performance.now();
    const result = await this.get<Session>("/api/v1/session");
    if (
      result?.authenticated !== true ||
      !result.csrfHeaderName ||
      !result.csrfToken ||
      !Number.isSafeInteger(result.idleTimeoutSeconds) ||
      result.idleTimeoutSeconds <= 0 ||
      result.idleTimeoutSeconds > 28_800 ||
      !Number.isFinite(Date.parse(result.absoluteExpiresAt)) ||
      Date.parse(result.absoluteExpiresAt) <= Date.now()
    )
      throw new ApiFailure(503, "SESSION_UNAVAILABLE");
    this.authority = result;
    this.idleAt = started + result.idleTimeoutSeconds * 1000;
    this.armExpiry();
    return result;
  }
  get<T>(path: string): Promise<T> {
    return this.request<T>(path, "GET");
  }
  post<T>(path: string, body: unknown): Promise<T> {
    return this.request<T>(path, "POST", body);
  }
  put<T>(path: string, body: unknown): Promise<T> {
    return this.request<T>(path, "PUT", body);
  }
  async logout(): Promise<void> {
    try {
      await this.post("/api/v1/session/logout", {});
    } finally {
      this.clear();
      this.expired();
    }
  }
  credentials(id: string, username: string, password: string): Promise<Operation> {
    return this.submitCredentials("/api/v1/operations", id, username, password);
  }
  credentialsV3(id: string, username: string, password: string): Promise<Operation> {
    return this.submitCredentials("/api/v3/operations", id, username, password);
  }
  private submitCredentials(
    path: "/api/v1/operations" | "/api/v3/operations",
    id: string,
    username: string,
    password: string,
  ): Promise<Operation> {
    if (this.submitted.has(id))
      return Promise.reject(new ApiFailure(409, "CREDENTIALS_ALREADY_SENT"));
    this.submitted.add(id);
    return this.post(`${path}/${encodeURIComponent(id)}/credentials`, {
      username,
      password,
    });
  }
  private async request<T>(path: string, method: string, body?: unknown): Promise<T> {
    if (
      this.authority &&
      (Date.now() >= Date.parse(this.authority.absoluteExpiresAt) ||
        performance.now() >= this.idleAt)
    )
      this.end();
    const publicRead =
      method === "GET" && (path === "/api/v1/session" || path === "/api/v1/capabilities");
    if (!this.authority && !publicRead) throw new ApiFailure(401, "SESSION_REQUIRED");
    const generation = this.generation;
    const started = performance.now();
    const controller = new AbortController();
    this.requests.add(controller);
    const deadline = setTimeout(() => controller.abort(), 120_000);
    const live = () => {
      if (generation !== this.generation) throw new ApiFailure(401, "SESSION_REQUIRED");
    };
    try {
      const headers: Record<string, string> = {};
      if (method !== "GET") {
        if (!this.authority) throw new ApiFailure(401, "SESSION_REQUIRED");
        headers[this.authority.csrfHeaderName] = this.authority.csrfToken;
        headers["Content-Type"] = "application/json";
      }
      let response: Response;
      try {
        const transport = this.transport;
        response = await transport(path, {
          method,
          headers,
          credentials: "same-origin",
          cache: "no-store",
          redirect: "error",
          signal: controller.signal,
          ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        });
      } catch {
        live();
        throw new ApiFailure(0, "NETWORK_UNCERTAIN");
      }
      live();
      if (response.status === 401) {
        this.end();
        throw new ApiFailure(401, "SESSION_REQUIRED");
      }
      if (response.ok && method !== "GET" && this.authority) {
        this.idleAt = Math.max(this.idleAt, started + this.authority.idleTimeoutSeconds * 1000);
        this.armExpiry();
      }
      // V3 controllers may refuse before owning a body/output transfer. Only their
      // closed, explicitly empty error response carries authority in this header.
      const earlyCode = response.headers.get("X-Environment-Studio-Code");
      if (
        !response.ok &&
        /^\/api\/v3\/(?:plans|operations)(?:\/|$)/.test(path) &&
        response.headers.get("Content-Length") === "0" &&
        earlyCode !== null &&
        v3ControllerCodes.has(earlyCode)
      ) {
        let text: string;
        try {
          text = await response.text();
        } catch {
          live();
          throw new ApiFailure(response.status, "RESPONSE_UNAVAILABLE");
        }
        live();
        if (text !== "") throw new ApiFailure(response.status, "RESPONSE_UNAVAILABLE");
        throw new ApiFailure(response.status, earlyCode);
      }
      if (response.status === 204) return undefined as T;
      let value: unknown;
      try {
        value = await response.json();
      } catch {
        live();
        throw new ApiFailure(response.status, "RESPONSE_UNAVAILABLE");
      }
      live();
      if (!response.ok) {
        if (
          response.status === 422 &&
          method === "PUT" &&
          /^\/api\/v3\/(?:definitions|profiles)\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?![\s\S])/u.test(
            path,
          )
        ) {
          const diagnostics = workspaceRejection(value);
          if (diagnostics === null) throw new ApiFailure(422, "RESPONSE_UNAVAILABLE");
          throw new ApiFailure(422, "REJECTED", diagnostics);
        }
        const failure = value as { code?: unknown; diagnostics?: Diagnostic[] };
        const code =
          typeof failure.code === "string" && /^[A-Z][A-Z0-9_]{0,95}$/.test(failure.code)
            ? failure.code
            : `HTTP_${response.status}`;
        throw new ApiFailure(
          response.status,
          code,
          Array.isArray(failure.diagnostics) ? failure.diagnostics : [],
        );
      }
      return value as T;
    } finally {
      clearTimeout(deadline);
      this.requests.delete(controller);
    }
  }
}
export const failureMessage = (error: unknown) =>
  error instanceof ApiFailure
    ? error.status === 409
      ? `Conflict (${error.code}). Reload the authoritative revision before another command.`
      : error.code
    : "REQUEST_UNAVAILABLE";
