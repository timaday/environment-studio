package studio.environment.core.observation;

import studio.environment.core.definitionv2.NativeCompilationResult;

/** Server composition supplies the checked selection; this interface is not caller publication authority. */
public interface ObservationPort {
    record Selection(NativeCompilationResult.ReadyToPublish compiled, String bindingId) {
        @Override public String toString() { return "Selection[REDACTED]"; }
    }
    /** Internal checked v3 selection; it conveys no publication or hosted authority. */
    record V3Selection(studio.environment.core.definitionv3.NativeCompilationResult.Checked compiled, String bindingId) {
        @Override public String toString() { return "V3Selection[REDACTED]"; }
    }
    ObservationResult observe(Selection selection, TransientCredentials credentials, Cancellation cancellation);
    default ObservationResult observeV3(V3Selection selection, TransientCredentials credentials, Cancellation cancellation) {
        java.util.Objects.requireNonNull(credentials).close();
        return new ObservationResult.Refused(ObservationResult.Code.DESTINATION_UNQUALIFIED, ObservationResult.Cleanup.COMPLETE);
    }
    sealed interface Reservation {
        record Admitted(Permit permit) implements Reservation { }
        record Refused(ObservationResult.Code code) implements Reservation { }
    }
    interface Permit extends AutoCloseable {
        ObservationResult observe(TransientCredentials credentials, Cancellation cancellation);
        /** Releases only an unused reservation; started work owns its cleanup. */
        @Override void close();
    }
    default Reservation reserve(Selection selection) {
        return new Reservation.Refused(ObservationResult.Code.DESTINATION_UNQUALIFIED);
    }
    default Reservation reserveV3(V3Selection selection) {
        return new Reservation.Refused(ObservationResult.Code.DESTINATION_UNQUALIFIED);
    }
    final class Cancellation {
        private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        public void cancel() { cancelled.set(true); }
        public boolean cancelled() { return cancelled.get(); }
    }
}
