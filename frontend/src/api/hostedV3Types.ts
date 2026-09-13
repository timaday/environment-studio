import type * as D from "./hostedV3Decoding";

// Exact, recursively readonly shapes inferred from the closed wire decoders.
export type CreatePlan = ReturnType<typeof D.createPlan>;
export type ReserveInspection = ReturnType<typeof D.reserveInspection>;
export type Ack = ReturnType<typeof D.ack>;
export type ReviewRequest = ReturnType<typeof D.reviewRequest>;
export type ReviewAck = ReturnType<typeof D.reviewAck>;
export type Operation = ReturnType<typeof D.operation>;
export type PlanSummary = ReturnType<typeof D.summary>;
export type Materialization = Omit<ReturnType<typeof D.materialization>, "state" | "complete"> &
  (
    | { readonly state: "COMPLETE"; readonly complete: true }
    | { readonly state: "INCOMPLETE" | "REFUSED"; readonly complete: false }
  );
export type PlanCommand = ReturnType<typeof D.command>;
export type CaptureRequest = ReturnType<typeof D.captureRequest>;
export type CaptureResponse = ReturnType<typeof D.captureResponse>;
export type PreviewRequest = ReturnType<typeof D.previewRequest>;
export type PreviewResponse = ReturnType<typeof D.previewResponse>;
export type RevisionRequest = ReturnType<typeof D.revisionRequest>;
export type ValidationPageRequest = ReturnType<typeof D.validationPageRequest>;
export type ValidationPageResponse = ReturnType<typeof D.validationPageResponse>;
export type ValidationSummary = Omit<
  ReturnType<typeof D.validationSummary>,
  "targetComplete" | "computedRuleCount"
> &
  (
    | { readonly targetComplete: false; readonly computedRuleCount: null }
    | { readonly targetComplete: true; readonly computedRuleCount: number }
  );
