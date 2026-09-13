package studio.environment.server.observation;

import studio.environment.core.observation.ObservationResult.Code;

final class ObservationFailure extends RuntimeException {
    private final Code code;
    ObservationFailure(Code code) { super(code.name(), null, false, false); this.code = code; }
    Code code() { return code; }
}
