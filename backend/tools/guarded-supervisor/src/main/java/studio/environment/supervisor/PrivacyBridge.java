package studio.environment.supervisor;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Private installed JNI boundary; no loader, policy provider or admission fallback. */
final class PrivacyBridge {
    private PrivacyBridge() { }
    private static boolean nativeBound;
    /** Registration lifetime guard only; never installation or privacy admission. */
    private static synchronized boolean claimNativeRegistration(){
        if(nativeBound||PrivacyBridge.class.getClassLoader()!=ClassLoader.getSystemClassLoader())return false;
        nativeBound=true;return true;
    }
    enum Failure { PLATFORM, INSTALLATION, SELF_PRIVACY, THREAD_SYNC, RESOURCE,
        IDENTITY, CHAIN, PROTOCOL, DEADLINE, CANCELLED, CLEANUP }
    enum ArmOwnership { NO_WINDOW_ACQUIRED, DISARM_REQUIRED_OR_UNKNOWN }
    sealed interface SelfResult permits SelfEstablished, SelfFailed { }
    enum SelfEstablished implements SelfResult { ESTABLISHED }
    record SelfFailed(Failure failure) implements SelfResult {
        SelfFailed { Objects.requireNonNull(failure); }
    }
    sealed interface ForkResult permits ForkArmed, ForkFailed { }
    enum ForkArmed implements ForkResult { ARMED }
    record ForkFailed(Failure failure, ArmOwnership ownership) implements ForkResult {
        ForkFailed { Objects.requireNonNull(failure); Objects.requireNonNull(ownership); }
    }
    sealed interface RootResult permits RootRegistered, RootFailed { }
    enum RootRegistered implements RootResult { REGISTERED }
    record RootFailed(Failure failure) implements RootResult {
        RootFailed { Objects.requireNonNull(failure); }
    }
    sealed interface DisarmResult permits ForkDisarmed, DisarmFailed { }
    enum ForkDisarmed implements DisarmResult { DISARMED }
    record DisarmFailed(Failure failure, ArmOwnership ownership) implements DisarmResult {
        DisarmFailed { Objects.requireNonNull(failure); Objects.requireNonNull(ownership); }
    }
    sealed interface OpenResult permits Opened, OpenFailed { }
    record Opened(long launch, String socketPath) implements OpenResult {
        Opened {
            Objects.requireNonNull(socketPath);
            if(launch<=0||socketPath.isEmpty()||socketPath.length()>103||socketPath.indexOf(0)>=0
                ||!StandardCharsets.UTF_8.newEncoder().canEncode(socketPath)
                ||socketPath.getBytes(StandardCharsets.UTF_8).length>103)throw new IllegalArgumentException();
        }
        @Override public String toString(){return "Opened[redacted]";}
    }
    record OpenFailed(Failure failure) implements OpenResult { OpenFailed {Objects.requireNonNull(failure);} }
    sealed interface Event permits FinalAdmitted, EventFailed, EventClosed { }
    record FinalAdmitted(long identity) implements Event {
        FinalAdmitted {if(identity<=0)throw new IllegalArgumentException();}
        @Override public String toString(){return "FinalAdmitted[redacted]";}
    }
    record EventFailed(Failure failure) implements Event { EventFailed {Objects.requireNonNull(failure);} }
    enum EventClosed implements Event { CLOSED }
    enum Status { STARTING, ADMITTED, FAILED, CLOSED }
    enum CloseResult { COMPLETE, INCONCLUSIVE }
    static native SelfResult establishSelf(int compiledMechanism);
    static native OpenResult openLaunch(int compiledChain, long operationRemainingNanos);
    static native ForkResult armFork(long launch);
    static native RootResult registerRoot(long launch, long ownedPid);
    static native DisarmResult disarmFork(long launch);
    static native Event nextEvent(long launch);
    static native Status status(long launch);
    static native void cancel(long launch);
    static native CloseResult closeLaunch(long launch, long cleanupRemainingNanos);
}
