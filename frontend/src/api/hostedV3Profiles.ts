import { ApiFailure, type HostedApi } from "./hosted";
import * as D from "./hostedV3Decoding";
import * as P from "./hostedV3ProfileDecoding";
import type { CaptureResponse } from "./hostedV3Types";

export type ProfileRevision = ReturnType<typeof P.profile>;
export type ProfileList = ReturnType<typeof P.list>;
export type SaveProfile = ReturnType<typeof P.save>;
export type PublishProfile = ReturnType<typeof P.publish>;
export type PreparedProfileSave = ReturnType<typeof P.preparedSave>;
export type PreparedProfilePublication = ReturnType<typeof P.preparedPublication>;

function request<T>(decode: (value: unknown) => T, value: unknown): T {
  try {
    return decode(value);
  } catch (error) {
    if (error instanceof ApiFailure) throw new ApiFailure(0, "INVALID_REQUEST");
    throw error;
  }
}
function path(objectId: string): string {
  return `/api/v3/profiles/${request(D.uuid, objectId)}`;
}
function sameObject(result: ProfileRevision, objectId: string): ProfileRevision {
  if (result.objectId !== objectId) return D.invalid();
  return result;
}
// Retain the destination with the exact command for explicit response-loss replay.
// Nothing here generates identities, retries, publishes or persists source.
export function prepareProfileSave(objectId: string, command: SaveProfile): PreparedProfileSave {
  return request(P.preparedSave, { objectId, command });
}
export function prepareProfilePublication(
  objectId: string,
  command: PublishProfile,
): PreparedProfilePublication {
  return request(P.preparedPublication, { objectId, command });
}
export function prepareCapturedProfileSave(
  objectId: string,
  expectedRevision: string,
  requestId: string,
  capture: CaptureResponse,
): PreparedProfileSave {
  const result = request(D.captureResponse, capture);
  return prepareProfileSave(objectId, {
    expectedRevision,
    requestId,
    format: "JSON",
    source: result.source,
    definition: result.definition,
  });
}

/** Owned workspace history. Stored publication never grants current readiness. */
export class HostedV3Profiles {
  constructor(private readonly api: HostedApi) {}
  async profiles(): Promise<ProfileList> {
    const result = P.list(await this.api.get("/api/v3/profiles"));
    if (
      !result.profiles.every(
        (item, i) => i === 0 || result.profiles[i - 1].objectId < item.objectId,
      )
    )
      return D.invalid();
    return result;
  }
  async profile(objectId: string): Promise<ProfileRevision> {
    return sameObject(P.profile(await this.api.get(path(objectId))), objectId);
  }
  async profileRevision(objectId: string, revision: string): Promise<ProfileRevision> {
    const url = `${path(objectId)}/revisions/${request(D.revision, revision)}`;
    const result = sameObject(P.profile(await this.api.get(url)), objectId);
    if (result.workspaceRevision !== revision) return D.invalid();
    return result;
  }
  async saveProfile(value: PreparedProfileSave): Promise<ProfileRevision> {
    const prepared = request(P.preparedSave, value);
    const result = sameObject(
      P.profile(await this.api.put(path(prepared.objectId), prepared.command)),
      prepared.objectId,
    );
    if (
      result.state !== "draft" ||
      result.source !== prepared.command.source ||
      result.format !== prepared.command.format ||
      result.definition.objectId !== prepared.command.definition.objectId ||
      result.definition.workspaceRevision !== prepared.command.definition.workspaceRevision
    )
      return D.invalid();
    return result;
  }
  async publishProfile(
    value: PreparedProfilePublication,
  ): Promise<ProfileRevision & { readonly state: "published" }> {
    const prepared = request(P.preparedPublication, value);
    const result = sameObject(
      P.profile(await this.api.post(`${path(prepared.objectId)}/publish`, prepared.command)),
      prepared.objectId,
    );
    if (
      result.state !== "published" ||
      result.publication.sourceRevision !== prepared.command.expectedRevision
    )
      return D.invalid();
    return result;
  }
}
