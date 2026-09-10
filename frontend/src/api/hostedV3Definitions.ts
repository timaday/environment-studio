import { ApiFailure, type HostedApi } from "./hosted";
import * as D from "./hostedV3Decoding";
import * as W from "./hostedV3DefinitionDecoding";

export type DefinitionRevision = ReturnType<typeof W.definition>;
export type DefinitionList = ReturnType<typeof W.list>;
function request<T>(decode: (value: unknown) => T, value: unknown): T {
  try {
    return decode(value);
  } catch (error) {
    if (error instanceof ApiFailure) throw new ApiFailure(0, "INVALID_REQUEST");
    throw error;
  }
}
function path(objectId: string): string {
  return `/api/v3/definitions/${request(D.uuid, objectId)}`;
}
function sameObject(value: unknown, objectId: string): DefinitionRevision {
  const result = W.definition(value);
  if (result.objectId !== objectId) return D.invalid();
  if (result.state === "published") {
    const compare = (a: string, b: string) => (a === b ? 0 : a < b ? -1 : 1);
    const expected = result.projection.model.bindings
      .flatMap((binding) => binding.documents.map((document) => [binding.id, document.id] as const))
      .sort((a, b) => compare(a[0], b[0]) || compare(a[1], b[1]));
    const actual = result.publication.exportPolicies.map((policy) => [
      policy.bindingId,
      policy.documentId,
    ]);
    if (
      new Set(expected.map((pair) => JSON.stringify(pair))).size !== expected.length ||
      JSON.stringify(expected) !== JSON.stringify(actual)
    )
      return D.invalid();
  }
  return result;
}

/** Exact v3 history; historical-ready data never grants current qualification. */
export class HostedV3Definitions {
  constructor(private readonly api: HostedApi) {}
  async definitions(): Promise<DefinitionList> {
    const result = W.list(await this.api.get("/api/v3/definitions"));
    if (
      !result.definitions.every(
        (row, index) => index === 0 || result.definitions[index - 1].objectId < row.objectId,
      )
    )
      return D.invalid();
    return result;
  }
  async definition(objectId: string): Promise<DefinitionRevision> {
    return sameObject(await this.api.get(path(objectId)), objectId);
  }
  async definitionRevision(objectId: string, revision: string): Promise<DefinitionRevision> {
    const url = `${path(objectId)}/revisions/${request(D.revision, revision)}`;
    const result = sameObject(await this.api.get(url), objectId);
    if (result.workspaceRevision !== revision) return D.invalid();
    return result;
  }
}

/** Declared candidates only. Parent coordinates still come from the placement API. */
export function discoverBinding(
  definition: DefinitionRevision,
  reference: ReturnType<typeof D.publicationRef>,
  bindingId: string,
) {
  const pinned = request(D.publicationRef, reference),
    selected = request(D.declaredId, bindingId);
  const conflict = (): never => {
    throw new ApiFailure(409, "CONFLICT");
  };
  if (
    definition.objectId !== pinned.objectId ||
    definition.workspaceRevision !== pinned.workspaceRevision
  )
    return conflict();
  const matches = definition.projection.model.bindings.filter((binding) => binding.id === selected);
  if (matches.length !== 1) return conflict();
  const binding = matches[0];
  const types = definition.projection.model.logical.entityTypes.map((type) => type.id);
  const documents = binding.documents.map((document) => {
    const projections = document.entities.map((entity) => {
      if (types.filter((type) => type === entity.type).length !== 1) return conflict();
      return Object.freeze({ projectionId: entity.id, typeId: entity.type });
    });
    if (new Set(projections.map((row) => row.projectionId)).size !== projections.length)
      return conflict();
    return Object.freeze({ documentId: document.id, projections: Object.freeze(projections) });
  });
  if (new Set(documents.map((row) => row.documentId)).size !== documents.length) return conflict();
  return Object.freeze({
    definition: pinned,
    bindingId: selected,
    engine: binding.engine,
    documents: Object.freeze(documents),
  });
}
