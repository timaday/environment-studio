package studio.environment.core.plan;

/** Safe boundary refusal: never embeds inputs or downstream exception details. */
public final class PlanRefusal extends RuntimeException {
    public enum Code { SESSION_REQUIRED, NOT_FOUND, CONFLICT, INVALID_REQUEST, CAPACITY, PLAN_BUSY,
        PUBLICATION_REQUIRED, UNSUPPORTED_DEFINITION, INVALID_DESTINATION, INSPECTION_REQUIRED,
        CREDENTIALS_ALREADY_CONSUMED, INVALID_CREDENTIALS, RESERVATION_EXPIRED, CANCELLED,
        OBSERVATION_REFUSED, PROJECTION_REFUSED, INCOMPLETE_TARGET, RESOURCE_LIMIT, CLEANUP_INCONCLUSIVE,
        DISCLOSURE_REQUIRED, STALE_PREVIEW, PROFILE_REFUSED, EXPORT_UNAVAILABLE }
    private final Code code;
    public PlanRefusal(Code code) { super(code.name(), null, false, false); this.code = code; }
    public Code code() { return code; }
}
