package studio.environment.core.observation;

import studio.environment.core.definitionv2.NativeCompilationResult;

/** Server composition supplies the checked selection; this interface is not caller publication authority. */
public interface ObservationPort {
    record Selection(NativeCompilationResult.ReadyToPublish compiled, String bindingId) {
        @Override public String toString() { return "Selection[REDACTED]"; }
    }
    ObservationResult observe(Selection selection, TransientCredentials credentials, Cancellation cancellation);
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
    final class Cancellation {
        private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        public void cancel() { cancelled.set(true); }
        public boolean cancelled() { return cancelled.get(); }
    }
}
