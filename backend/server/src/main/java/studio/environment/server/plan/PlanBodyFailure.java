package studio.environment.server.plan;

/** Closed HTTP-body refusal; never contains input or parser exception text. */
final class PlanBodyFailure extends RuntimeException {
    enum Code { MALFORMED_BODY, BODY_TOO_LARGE, BODY_DEADLINE, CANCELLED }
    private final Code code;
    PlanBodyFailure(Code code) { super(code.name(),null,false,false); this.code=code; }
    Code code() { return code; }
}
