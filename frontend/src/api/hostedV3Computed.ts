import { ApiFailure, type HostedApi } from "./hosted";
import * as C from "./hostedV3ComputedDecoding";
import * as D from "./hostedV3Decoding";

export type ComputedKey = ReturnType<typeof C.key>;
export type ComputedSelector = ReturnType<typeof C.selector>;
export type ComputedRequest = ReturnType<typeof C.collectionRequest>;
export type ContributorsRequest = ReturnType<typeof C.contributorsRequest>;
type Route = "nodes" | "memberships" | "cooccurrences" | "rules" | "contributors";
type Page = Readonly<{
  revision: string;
  total: number;
  offset: number;
  nextOffset: number | null;
  items: readonly unknown[];
}>;
export type ComputedRead<R extends Route, Q extends ComputedRequest, A extends Page> = Readonly<{
  planId: string;
  route: R;
  request: Q;
  response: A;
}>;
function request<T>(decode: (value: unknown) => T, value: unknown): T {
  try {
    return decode(value);
  } catch (error) {
    if (error instanceof ApiFailure) throw new ApiFailure(0, "INVALID_REQUEST");
    throw error;
  }
}
function conflict(): never {
  throw new ApiFailure(409, "CONFLICT");
}
function same(first: unknown, second: unknown): boolean {
  return JSON.stringify(first) === JSON.stringify(second);
}

/** Context equality is not an atomic cross-page snapshot or validation proof. */
export function assertComputedPagesCompatible(
  first: ComputedRead<Route, ComputedRequest, Page>,
  next: ComputedRead<Route, ComputedRequest, Page>,
): void {
  const scope = (value: ComputedRequest) =>
    Object.fromEntries(
      Object.entries(value).filter(([key]) => key !== "offset" && key !== "limit"),
    );
  if (
    first.planId !== next.planId ||
    first.route !== next.route ||
    first.response.total !== next.response.total ||
    !same(scope(first.request), scope(next.request))
  )
    conflict();
}

/** Fixed v3 computed routes using the original session; all requests are explicit. */
export class HostedV3Computed {
  constructor(private readonly api: HostedApi) {}
  private async page<R extends Route, Q extends ComputedRequest, A extends Page>(
    route: R,
    planId: string,
    value: Q,
    decodeRequest: (value: unknown) => Q,
    decodeResponse: (value: unknown) => A,
  ): Promise<ComputedRead<R, Q, A>> {
    const id = request(D.uuid, planId),
      body = request(decodeRequest, value);
    const response = decodeResponse(
      await this.api.post(`/api/v3/plans/${id}/views/computed/${route}`, body),
    );
    const count = Math.min(body.limit, Math.max(0, response.total - body.offset));
    if (
      response.revision !== body.revision ||
      response.offset !== body.offset ||
      response.items.length !== count ||
      response.nextOffset !== (body.offset + count < response.total ? body.offset + count : null)
    )
      return D.invalid();
    return Object.freeze({ planId: id, route, request: body, response });
  }
  nodes(planId: string, value: ComputedRequest) {
    return this.page("nodes", planId, value, C.collectionRequest, C.nodes);
  }
  memberships(planId: string, value: ComputedRequest) {
    return this.page("memberships", planId, value, C.collectionRequest, C.memberships);
  }
  cooccurrences(planId: string, value: ComputedRequest) {
    return this.page("cooccurrences", planId, value, C.collectionRequest, C.cooccurrences);
  }
  rules(planId: string, value: ComputedRequest) {
    return this.page("rules", planId, value, C.collectionRequest, C.rules);
  }
  async contributors(planId: string, value: ContributorsRequest) {
    const read = await this.page(
      "contributors",
      planId,
      value,
      C.contributorsRequest,
      C.contributors,
    );
    const selector = read.request.selector;
    const expected =
      selector.kind === "node"
        ? [selector.key.value]
        : selector.kind === "membership"
          ? [selector.computed.value]
          : [selector.source.value, selector.target.value];
    for (const row of read.response.items) {
      if (
        row.roles.length !== expected.length ||
        (selector.kind === "membership" && !same(row.physical, selector.physical))
      )
        return D.invalid();
      for (const [index, role] of row.roles.entries()) {
        if (role.location.value.decodedValue !== expected[index]) return D.invalid();
        const pins = role.location.selector
          ? [role.location.value, role.location.selector.discriminator]
          : [role.location.value];
        if (pins.some((pin) => pin.valueEnd < pin.valueStart)) return D.invalid();
      }
    }
    return read;
  }
}

type SelectedRead =
  | Awaited<ReturnType<HostedV3Computed["nodes"]>>
  | Awaited<ReturnType<HostedV3Computed["memberships"]>>
  | Awaited<ReturnType<HostedV3Computed["cooccurrences"]>>;
/** Correlate an explicit contributor request/page with its selected returned row. */
export function assertContributorsForSelection(
  selected: SelectedRead,
  index: number,
  contributors: Awaited<ReturnType<HostedV3Computed["contributors"]>>,
): void {
  if (!Number.isSafeInteger(index) || index < 0 || index >= selected.response.items.length)
    conflict();
  let selector: ComputedSelector;
  if (selected.route === "nodes")
    selector = { kind: "node", key: selected.response.items[index].key };
  else if (selected.route === "memberships") {
    const row = selected.response.items[index];
    selector = {
      kind: "membership",
      relation: row.relation,
      physical: row.physical,
      computed: row.computed,
    };
  } else {
    const row = selected.response.items[index];
    selector = {
      kind: "cooccurrence",
      relation: row.relation,
      source: row.source,
      target: row.target,
    };
  }
  if (
    selected.planId !== contributors.planId ||
    selected.request.revision !== contributors.request.revision ||
    selected.request.side !== contributors.request.side ||
    selected.response.items[index].contributorTotal !== contributors.response.total ||
    !same(selector, contributors.request.selector)
  )
    conflict();
}
