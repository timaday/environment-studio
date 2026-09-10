import { ApiFailure, type HostedApi } from "./hosted";
import * as D from "./hostedV3Decoding";
import * as P from "./hostedV3PhysicalDecoding";

export type RevisionRequest = ReturnType<typeof P.revisionRequest>;
export type EntitiesRequest = ReturnType<typeof P.entitiesRequest>;
export type DraftRequest = ReturnType<typeof P.draftRequest>;
export type PlacementsRequest = ReturnType<typeof P.placementsRequest>;
export type BindingsRequest = ReturnType<typeof P.bindingsRequest>;
export type DocumentRequest = ReturnType<typeof P.documentRequest>;
export type LocationsRequest = ReturnType<typeof P.locationsRequest>;
type Route =
  | "documents"
  | "entities"
  | "relations"
  | "draft"
  | "containment"
  | "placements"
  | "bindings"
  | "document"
  | "binding-locations";
export type PhysicalRead<R extends Route, Q, A> = Readonly<{
  planId: string;
  route: R;
  request: Q;
  response: A;
}>;
type Page = Readonly<{
  revision: string;
  total: number;
  offset: number;
  nextOffset: number | null;
  items: readonly unknown[];
}>;
type PageRequest = Readonly<{ revision: string; offset: number; limit: number }>;
function request<T>(decode: (value: unknown) => T, value: unknown): T {
  try {
    return decode(value);
  } catch (error) {
    if (error instanceof ApiFailure) throw new ApiFailure(0, "INVALID_REQUEST");
    throw error;
  }
}
function fullPage(result: Page, body: PageRequest): void {
  const count = Math.min(body.limit, Math.max(0, result.total - body.offset));
  if (
    result.offset !== body.offset ||
    result.items.length !== count ||
    result.nextOffset !== (body.offset + count < result.total ? body.offset + count : null)
  )
    D.invalid();
}
// Context travels with each result. Equal revisions/totals alone cannot identify
// the requested side/entity/field/projection. This is not an atomic snapshot proof.
export function assertPhysicalPagesCompatible(
  first: PhysicalRead<Route, PageRequest, Page>,
  next: PhysicalRead<Route, PageRequest, Page>,
): void {
  const scope = (value: PageRequest) =>
    Object.fromEntries(
      Object.entries(value).filter(([key]) => key !== "offset" && key !== "limit"),
    );
  if (
    first.planId !== next.planId ||
    first.route !== next.route ||
    first.response.total !== next.response.total ||
    JSON.stringify(scope(first.request)) !== JSON.stringify(scope(next.request))
  )
    throw new ApiFailure(409, "CONFLICT");
}

/** Fixed physical v3 routes. Requests are explicit; no paging or disclosure defaults. */
export class HostedV3Physical {
  constructor(private readonly api: HostedApi) {}
  private async read<
    R extends Route,
    Q extends { readonly revision: string },
    A extends { readonly revision: string },
  >(
    route: R,
    planId: string,
    value: Q,
    decodeRequest: (value: unknown) => Q,
    decodeResponse: (value: unknown) => A,
  ): Promise<PhysicalRead<R, Q, A>> {
    const id = request(D.uuid, planId);
    const body = request(decodeRequest, value);
    const response = decodeResponse(
      await this.api.post(`/api/v3/plans/${id}/views/${route}`, body),
    );
    if (response.revision !== body.revision) return D.invalid();
    return Object.freeze({ planId: id, route, request: body, response });
  }
  private async page<R extends Route, Q extends PageRequest, A extends Page>(
    route: R,
    planId: string,
    value: Q,
    decodeRequest: (value: unknown) => Q,
    decodeResponse: (value: unknown) => A,
  ): Promise<PhysicalRead<R, Q, A>> {
    const result = await this.read(route, planId, value, decodeRequest, decodeResponse);
    fullPage(result.response, result.request);
    return result;
  }
  async documents(planId: string, value: RevisionRequest) {
    const result = await this.read("documents", planId, value, P.revisionRequest, P.documents);
    const ids = result.response.documents.map((row) => row.documentId);
    if (new Set(ids).size !== ids.length) return D.invalid();
    return result;
  }
  entities(planId: string, value: EntitiesRequest) {
    return this.page("entities", planId, value, P.entitiesRequest, P.entities);
  }
  relations(planId: string, value: EntitiesRequest) {
    return this.page("relations", planId, value, P.entitiesRequest, P.relations);
  }
  draft(planId: string, value: DraftRequest) {
    return this.page("draft", planId, value, P.draftRequest, P.draft);
  }
  containment(planId: string, value: DraftRequest) {
    return this.page("containment", planId, value, P.draftRequest, P.containment);
  }
  async placements(planId: string, value: PlacementsRequest) {
    const result = await this.page("placements", planId, value, P.placementsRequest, P.placements);
    if (result.response.items.some((item) => item.documentId !== result.request.documentId))
      return D.invalid();
    return result;
  }
  bindings(planId: string, value: BindingsRequest) {
    return this.page("bindings", planId, value, P.bindingsRequest, P.bindings);
  }
  async document(planId: string, value: DocumentRequest) {
    const result = await this.read("document", planId, value, P.documentRequest, P.document);
    if (
      result.response.documentId !== result.request.documentId ||
      result.response.side !== result.request.side ||
      result.response.mode !== result.request.mode
    )
      return D.invalid();
    return result;
  }
  async bindingLocations(planId: string, value: LocationsRequest) {
    const result = await this.page(
      "binding-locations",
      planId,
      value,
      P.locationsRequest,
      P.locations,
    );
    if (result.response.items.some((item) => item.span.end < item.span.start)) return D.invalid();
    return result;
  }
}

// Lexical selection only. Matching request scope/digests do not establish an
// atomic cross-request snapshot or replace the server's complete proof checks.
export function rawLocationText(
  inventory: Awaited<ReturnType<HostedV3Physical["documents"]>>,
  document: Awaited<ReturnType<HostedV3Physical["document"]>>,
  locations: Awaited<ReturnType<HostedV3Physical["bindingLocations"]>>,
  index: number,
): string {
  const refusal = () => {
    throw new ApiFailure(409, "CONFLICT");
  };
  if (!Number.isSafeInteger(index) || index < 0 || index >= locations.response.items.length)
    return refusal();
  const location = locations.response.items[index],
    view = document.response;
  if (
    inventory.planId !== document.planId ||
    locations.planId !== document.planId ||
    inventory.response.revision !== view.revision ||
    locations.response.revision !== view.revision ||
    locations.request.side !== view.side ||
    location.documentId !== view.documentId ||
    view.mode !== "raw" ||
    !view.exact ||
    view.redacted ||
    view.omissions.length !== 0 ||
    location.span.end > view.text.length
  )
    return refusal();
  const entry = inventory.response.documents.find((row) => row.documentId === view.documentId);
  const digest = view.side === "current" ? entry?.currentDigest : entry?.targetDigest;
  if (digest !== location.sourceDigest) return refusal();
  for (const boundary of [location.span.start, location.span.end]) {
    const before = view.text.charCodeAt(boundary - 1),
      after = view.text.charCodeAt(boundary);
    if (before >= 0xd800 && before <= 0xdbff && after >= 0xdc00 && after <= 0xdfff)
      return refusal();
  }
  return view.text.slice(location.span.start, location.span.end);
}
