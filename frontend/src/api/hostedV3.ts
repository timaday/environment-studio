import { ApiFailure, type HostedApi } from "./hosted";
import * as D from "./hostedV3Decoding";
import type * as T from "./hostedV3Types";

function request<T>(decode: (value: unknown) => T, value: unknown): T {
  try {
    return decode(value);
  } catch (error) {
    if (error instanceof ApiFailure) throw new ApiFailure(0, "INVALID_REQUEST");
    throw error;
  }
}
function planPath(id: string): string {
  return `/api/v3/plans/${encodeURIComponent(request(D.uuid, id))}`;
}
function operationPath(id: string): string {
  return `/api/v3/operations/${encodeURIComponent(request(D.uuid, id))}`;
}
function sameRevision(actual: string, expected: string) {
  if (actual !== expected) D.invalid();
}
function samePlan(actual: string, expected: string) {
  if (actual !== expected) D.invalid();
}
function sortedUnique(values: readonly string[]): boolean {
  return values.every((value, i) => i === 0 || values[i - 1] < value);
}
function completePage(
  value: { total: number; offset: number; nextOffset: number | null; items: readonly unknown[] },
  requested: { offset: number; limit: number },
) {
  const count = Math.min(requested.limit, Math.max(0, value.total - requested.offset));
  if (
    value.offset !== requested.offset ||
    value.items.length !== count ||
    value.nextOffset !== (requested.offset + count < value.total ? requested.offset + count : null)
  )
    D.invalid();
}

// A retained replay input is a recursively frozen, detached snapshot. No request ID
// is generated here; neither commands nor credentials are automatically retried.
export function prepareCommand(value: T.PlanCommand): T.PlanCommand {
  return request(D.command, value);
}
export function prepareReview(value: T.ReviewRequest): T.ReviewRequest {
  return request(D.reviewRequest, value);
}

export function assertPreviewCompatible(first: T.PreviewResponse, next: T.PreviewResponse): void {
  if (
    first.revision !== next.revision ||
    first.previewDigest !== next.previewDigest ||
    JSON.stringify(first.pins) !== JSON.stringify(next.pins) ||
    JSON.stringify(first.affectedDerivations) !== JSON.stringify(next.affectedDerivations) ||
    (first.section === next.section && first.total !== next.total)
  )
    throw new ApiFailure(409, "CONFLICT");
}
export function assertValidationCompatible(
  summary: T.ValidationSummary,
  page: T.ValidationPageResponse,
): void {
  if (
    !summary.targetComplete ||
    summary.revision !== page.revision ||
    summary.inputFingerprint !== page.inputFingerprint ||
    summary.computedRuleCount !== page.total
  )
    throw new ApiFailure(409, "CONFLICT");
}

/** Fixed V3 routes over the existing session owner; construction enables no UI. */
export class HostedV3Api {
  constructor(private readonly api: HostedApi) {}
  async review(planId: string, value: T.ReviewRequest): Promise<T.ReviewAck> {
    const body = prepareReview(value);
    const result = D.reviewAck(await this.api.post(`${planPath(planId)}/reviews`, body));
    samePlan(result.planId, planId);
    sameRevision(result.revision, body.expectedRevision);
    return result;
  }
  async create(value: T.CreatePlan): Promise<T.Ack> {
    return D.ack(await this.api.post("/api/v3/plans", request(D.createPlan, value)));
  }
  async current(): Promise<T.PlanSummary> {
    return D.summary(await this.api.get("/api/v3/plans/current"));
  }
  async summary(planId: string): Promise<T.PlanSummary> {
    const value = D.summary(await this.api.get(planPath(planId)));
    samePlan(value.planId, planId);
    return value;
  }
  async reserveInspection(
    planId: string,
    value: T.ReserveInspection,
  ): Promise<T.Ack & { readonly operationId: string }> {
    const result = D.ack(
      await this.api.post(`${planPath(planId)}/inspections`, request(D.reserveInspection, value)),
    );
    samePlan(result.planId, planId);
    if (result.operationId === undefined) return D.invalid();
    return Object.freeze({ ...result, operationId: result.operationId });
  }
  async credentials(operationId: string, username: string, password: string): Promise<T.Operation> {
    request(D.uuid, operationId);
    const result = D.operation(await this.api.credentialsV3(operationId, username, password));
    if (result.operationId !== operationId) return D.invalid();
    return result;
  }
  async operation(operationId: string): Promise<T.Operation> {
    const result = D.operation(await this.api.get(operationPath(operationId)));
    if (result.operationId !== operationId) return D.invalid();
    return result;
  }
  async cancel(operationId: string): Promise<T.Operation> {
    const result = D.operation(await this.api.post(`${operationPath(operationId)}/cancel`, {}));
    if (result.operationId !== operationId) return D.invalid();
    return result;
  }
  async command(planId: string, value: T.PlanCommand): Promise<T.Ack> {
    const result = D.ack(
      await this.api.post(`${planPath(planId)}/commands`, prepareCommand(value)),
    );
    // A replay may acknowledge an older revision; never replace it with the latest.
    samePlan(result.planId, planId);
    return result;
  }
  async materialize(planId: string, value: T.RevisionRequest): Promise<T.Materialization> {
    const body = request(D.revisionRequest, value);
    const result = D.materialization(
      await this.api.post(`${planPath(planId)}/materializations`, body),
    );
    sameRevision(result.revision, body.revision);
    if (
      result.complete !== (result.state === "COMPLETE") ||
      (result.complete && result.diagnostics.length !== 0)
    )
      return D.invalid();
    return result as T.Materialization;
  }
  async capture(planId: string, value: T.CaptureRequest): Promise<T.CaptureResponse> {
    const body = request(D.captureRequest, value);
    if (
      new Set(body.mappings.map((item) => item.slotId)).size !== body.mappings.length ||
      new Set(body.mappings.map((item) => item.entity.handle)).size !== body.mappings.length
    )
      throw new ApiFailure(0, "INVALID_REQUEST");
    const result = D.captureResponse(
      await this.api.post(`${planPath(planId)}/profile-captures`, body),
    );
    sameRevision(result.revision, body.revision);
    if (new TextEncoder().encode(result.source).byteLength > 1048576) return D.invalid();
    return result;
  }
  async preview(planId: string, value: T.PreviewRequest): Promise<T.PreviewResponse> {
    const body = request(D.previewRequest, value);
    const result = D.previewResponse(
      await this.api.post(`${planPath(planId)}/profile-previews`, body),
    );
    sameRevision(result.revision, body.revision);
    sameRevision(result.pins.revision, body.revision);
    samePlan(result.pins.planId, planId);
    if (
      result.section !== body.section ||
      result.pins.profile.objectId !== body.profile.objectId ||
      result.pins.profile.workspaceRevision !== body.profile.workspaceRevision ||
      !sortedUnique(result.pins.selectedRoots) ||
      !sortedUnique(result.affectedDerivations)
    )
      return D.invalid();
    if (
      body.selection.kind === "selected" &&
      JSON.stringify([...body.selection.roots].sort()) !== JSON.stringify(result.pins.selectedRoots)
    )
      return D.invalid();
    const maxima = { included: 20000, dependencies: 19999, relations: 50000, conflicts: 255 };
    if (result.total > maxima[result.section]) return D.invalid();
    completePage(result, body);
    return result;
  }
  async validate(planId: string, value: T.RevisionRequest): Promise<T.ValidationSummary> {
    const body = request(D.revisionRequest, value);
    const result = D.validationSummary(
      await this.api.post(`${planPath(planId)}/validations`, body),
    );
    sameRevision(result.revision, body.revision);
    if (
      result.targetComplete !== (result.computedRuleCount !== null) ||
      result.checks.some((check) => check.inputFingerprint !== result.inputFingerprint) ||
      !sortedUnique(result.applicationRules.map((rule) => rule.ruleId))
    )
      return D.invalid();
    return result as T.ValidationSummary;
  }
  async validationRules(
    planId: string,
    value: T.ValidationPageRequest,
  ): Promise<T.ValidationPageResponse> {
    const body = request(D.validationPageRequest, value);
    const result = D.validationPageResponse(
      await this.api.post(`${planPath(planId)}/validations`, body),
    );
    sameRevision(result.revision, body.revision);
    if (result.inputFingerprint !== body.inputFingerprint) return D.invalid();
    completePage(result, body);
    return result;
  }
}
