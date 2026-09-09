package studio.environment.core.plan;

import java.math.BigInteger;
import java.util.*;
import java.util.function.*;
import studio.environment.core.session.*;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.core.observation.*;
import studio.environment.core.profile.*;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.workspace.NativeWorkspaceDigests;
import static studio.environment.core.plan.PlanPorts.*;
import static studio.environment.core.plan.PlanRefusal.Code.*;

/** Session-memory application boundary. All authority transitions acquire the session guard before state. */
public final class HostedPlanService {
    public record Ack(String planId, String revision, Optional<String> operationId) { }
    public record Mutation(String expectedRevision, String requestId) { }
    public record Summary(String planId, String revision, boolean inspectionValid, boolean targetComplete,
            int documents, int entities, boolean exportAvailable) { }
    public record Counts(int documents,int entities,int relations) { }
    public record View(String planId,String revision,NativeCommand.Reference definition,String bindingId,String destinationId,
            Optional<PlanObservedDestination> observedDestination,Counts currentCounts,Counts targetCounts,boolean inspectionValid,boolean targetComplete,boolean exportAvailable,List<String> blockers,Optional<String> activeOperationId) { }
    public View view(SessionLedger.Lease lease,Optional<String> id) {
        return guarded(lease,()-> {
            var state=state(lease); if(state==null || state.plan==null) throw new PlanRefusal(NOT_FOUND);
            var plan=plan(lease,id.orElse(state.plan.id));
            var blockers=new TreeSet<String>(plan.diagnostics); blockers.add("EXPORT_UNAVAILABLE");
            if(!plan.inspectionValid) blockers.add("INSPECTION_REQUIRED");
            if(plan.target==null) blockers.add("TARGET_INCOMPLETE");
            if(blockers.size()>256 || blockers.stream().anyMatch(code->!code.matches("[A-Z][A-Z0-9_]{0,95}"))) throw new PlanRefusal(PROJECTION_REFUSED);
            return new View(plan.id,plan.revision.toString(),plan.definition.reference(),plan.binding,plan.destination.id(),Optional.ofNullable(plan.observedDestination).map(observed->observed.validity(plan.inspectionValid)),counts(plan.current),counts(plan.target),
                    plan.inspectionValid,plan.target!=null,false,List.copyOf(blockers),Optional.ofNullable(plan.active).map(operation->operation.id));
        });
    }
    private static Counts counts(Content content) { return content==null?new Counts(0,0,0):new Counts(content.sources().size(),content.graph().entities().size(),content.graph().edges().size()); }
    /** Verification never holds the session/state locks while the adapter publishes bytes. */
    public void verifySummary(SessionLedger.Lease lease,View snapshot) {
        if(!view(lease,Optional.of(snapshot.planId())).equals(snapshot))throw new PlanRefusal(CONFLICT);
    }
    public enum Phase { RESERVED, RUNNING, SUCCEEDED, REFUSED, CANCELLED, EXPIRED }
    public enum Cleanup { COMPLETE, IN_PROGRESS, INCONCLUSIVE }
    public record Status(String operationId, String planId, Phase phase, String code, Cleanup cleanup, Optional<String> installedRevision) { }
    private record Replay(String identity, Ack acknowledgement) { }
    private static final class LeaseState {
        final SessionLedger.Lease lease;
        final Map<String,Replay> replay = new LinkedHashMap<>();
        final Set<String> ownedPlans = new HashSet<>();
        final Map<String,Operation> operations = new LinkedHashMap<>();
        Plan plan;
        int commandReaders;
        LeaseState(SessionLedger.Lease lease) { this.lease = lease; }
    }
    private static final class Plan {
        final String id = UUID.randomUUID().toString();
        final PublishedDefinition definition;
        final String binding;
        final Destination destination;
        BigInteger revision = BigInteger.ONE;
        long generation;
        boolean retired;
        boolean rendering;
        int readers;
        List<String> diagnostics=List.of();
        final Set<String> profiles=new TreeSet<>();
        final Map<studio.environment.core.planning.TargetIntent.Ref,String> handles=new HashMap<>();
        final Map<String,studio.environment.core.planning.TargetIntent.Ref.Existing> originals=new HashMap<>();
        boolean inspectionValid;
        String observationFingerprint;
        PlanObservedDestination observedDestination;
        Content current;
        Content target;
        Draft draft = Draft.empty();
        Operation active;
        long retainedBytes;
        long enteredBytes;
        Plan(PublishedDefinition definition, String binding, Destination destination) {
            this.definition = definition; this.binding = binding; this.destination = destination;
        }
        Ack ack() { return new Ack(id,revision.toString(),Optional.empty()); }
    }
    private static final class Operation {
        final String id = UUID.randomUUID().toString();
        Plan plan;
        final String planId;
        final String revision;
        final long generation;
        final long reservedAt;
        ObservationPort.Permit permit;
        final ObservationPort.Cancellation cancellation = new ObservationPort.Cancellation();
        Phase phase = Phase.RESERVED;
        Cleanup cleanup = Cleanup.COMPLETE;
        String code = "RESERVED";
        Optional<String> installed = Optional.empty();
        boolean consumed;
        volatile ObservationResult.CleanupHandle cleanupHandle;
        Operation(Plan plan, long now, ObservationPort.Permit permit) {
            this.plan=plan; this.planId=plan.id; this.revision=plan.revision.toString(); this.generation=plan.generation; this.reservedAt=now; this.permit=permit;
        }
        Status status() { return new Status(id,planId,phase,code,cleanup,installed); }
    }
    private static final long MIB = 1_048_576L;
    private static final long RESERVATION_NANOS = 60_000_000_000L;
    private final Object lock = new Object();
    private final Authority authority;
    private final Workspace workspace;
    private final Map<String,Destination> destinations;
    private final ContentAdapter content;
    private final LongSupplier monotonic;
    private final CommandIdentity identities = new CommandIdentity();
    private final Map<String,LeaseState> leases = new LinkedHashMap<>();
    private long retainedBytes;
    private long enteredBytes;
    private boolean materializationScratch;
    private CommandAdmission commandScratch;
    private ViewAdmission viewScratch;
    private int observationScratch;
    public HostedPlanService(Authority authority, Workspace workspace, Map<String,Destination> destinations,
            ContentAdapter content, LongSupplier monotonic) {
        this.authority=Objects.requireNonNull(authority); this.workspace=Objects.requireNonNull(workspace);
        this.destinations=Map.copyOf(destinations); this.content=Objects.requireNonNull(content); this.monotonic=Objects.requireNonNull(monotonic);
    }
    private <T> T guarded(SessionLedger.Lease lease, Supplier<T> transition) {
        return authority.guard(lease,()-> { synchronized(lock) { return transition.get(); } }).orElseThrow(()->new PlanRefusal(SESSION_REQUIRED));
    }
    private LeaseState state(SessionLedger.Lease lease) {
        var state=leases.get(lease.id());
        if(state!=null && !state.lease.equals(lease)) throw new PlanRefusal(SESSION_REQUIRED);
        return state;
    }
    private LeaseState ownedState(SessionLedger.Lease lease,String planId) {
        var state=state(lease);
        if(state==null || !state.ownedPlans.contains(planId)) throw new PlanRefusal(NOT_FOUND);
        return state;
    }
    private Plan plan(SessionLedger.Lease lease,String id) {
        var state=state(lease);
        if(state==null || state.plan==null || state.plan.retired || !state.plan.id.equals(id)) throw new PlanRefusal(NOT_FOUND);
        expireReservation(state.plan);
        return state.plan;
    }
    private static void uuid(String value) {
        try { if(!UUID.fromString(value).toString().equals(value)) throw new PlanRefusal(INVALID_REQUEST); }
        catch(IllegalArgumentException | NullPointerException invalid) { throw new PlanRefusal(INVALID_REQUEST); }
    }
    private String identity(SessionLedger.Lease lease,String id,Mutation mutation,String kind,Object arguments) {
        uuid(mutation.requestId());
        if(mutation.expectedRevision()==null || !mutation.expectedRevision().matches("[1-9][0-9]{0,1023}")) throw new PlanRefusal(INVALID_REQUEST);
        return identities.digest(lease.id(),id,Map.of("requestId",mutation.requestId(),"expectedRevision",mutation.expectedRevision(),"kind",kind,"arguments",arguments));
    }
    private static Optional<Ack> replay(LeaseState state,String requestId,String identity) {
        var previous=state.replay.get(requestId);
        if(previous!=null) {
            if(!previous.identity.equals(identity)) throw new PlanRefusal(CONFLICT);
            return Optional.of(previous.acknowledgement);
        }
        if(state.replay.size()>=256) throw new PlanRefusal(CAPACITY);
        return Optional.empty();
    }
    private static void current(Plan plan,Mutation mutation) {
        if(!plan.revision.toString().equals(mutation.expectedRevision())) throw new PlanRefusal(CONFLICT);
        if(plan.active!=null || plan.rendering) throw new PlanRefusal(PLAN_BUSY);
    }
    public Ack create(SessionLedger.Lease lease,String requestId,NativeCommand.Reference definition,String binding,String destination) {
        uuid(requestId);
        var command=Map.of("kind","create","requestId",requestId,"definition",Map.of("objectId",definition.objectId(),"workspaceRevision",definition.workspaceRevision()),"binding",binding,"destination",destination);
        String identity=identities.digest(lease.id(),"new-plan",command);
        var replay=guarded(lease,()-> {
            var state=state(lease);
            if(state!=null) {
                var previous=replay(state,requestId,identity); if(previous.isPresent()) return previous;
                if(state.plan!=null) throw new PlanRefusal(CAPACITY);
            }
            if(livePlans()>=4) throw new PlanRefusal(CAPACITY);
            return Optional.<Ack>empty();
        });
        if(replay.isPresent()) return replay.get();
        var published=workspace.definition(lease.owner(),definition);
        var selected=destinations.get(destination);
        var declared=published.compiled().checked().definition().bindings().stream().filter(item->item.id().equals(binding)).findFirst().orElseThrow(()->new PlanRefusal(INVALID_DESTINATION));
        if(selected==null || selected.engine()!=declared.engine()) throw new PlanRefusal(INVALID_DESTINATION);
        return guarded(lease,()-> {
            var state=leases.computeIfAbsent(lease.id(),ignored->new LeaseState(lease));
            var previous=replay(state,requestId,identity); if(previous.isPresent()) return previous.get();
            if(state.plan!=null || livePlans()>=4) throw new PlanRefusal(CAPACITY);
            state.plan=new Plan(published,binding,selected); state.ownedPlans.add(state.plan.id);
            var acknowledgement=state.plan.ack(); state.replay.put(requestId,new Replay(identity,acknowledgement));
            return acknowledgement;
        });
    }
    private long livePlans() { return leases.values().stream().filter(state->state.plan!=null).count(); }
    public Summary summary(SessionLedger.Lease lease,String planId) {
        return guarded(lease,()-> {
            var plan=plan(lease,planId);
            return new Summary(plan.id,plan.revision.toString(),plan.inspectionValid,plan.target!=null,
                    plan.current==null?0:plan.current.sources().size(),plan.current==null?0:plan.current.graph().entities().size(),false);
        });
    }
    public Ack reserve(SessionLedger.Lease lease,String planId,Mutation mutation) {
        String identity=identity(lease,planId,mutation,"reserve",List.of());
        return guarded(lease,()-> {
            var state=ownedState(lease,planId);
            var previous=replay(state,mutation.requestId(),identity); if(previous.isPresent()) return previous.get();
            var plan=plan(lease,planId); current(plan,mutation);
            if(state.operations.size()>=256 || observationScratch>=4) throw new PlanRefusal(CAPACITY);
            var reservation=plan.destination.observations().reserve(new ObservationPort.Selection(plan.definition.compiled(),plan.binding));
            if(reservation instanceof ObservationPort.Reservation.Refused refused) throw new PlanRefusal(refused.code()==ObservationResult.Code.CAPACITY?CAPACITY:OBSERVATION_REFUSED);
            var operation=new Operation(plan,monotonic.getAsLong(),((ObservationPort.Reservation.Admitted)reservation).permit());
            observationScratch++; plan.active=operation; state.operations.put(operation.id,operation);
            var acknowledgement=new Ack(plan.id,plan.revision.toString(),Optional.of(operation.id));
            state.replay.put(mutation.requestId(),new Replay(identity,acknowledgement));
            return acknowledgement;
        });
    }
    private void expireReservation(Plan plan) {
        var operation=plan.active;
        if(operation!=null && operation.phase==Phase.RESERVED && monotonic.getAsLong()-operation.reservedAt>=RESERVATION_NANOS) {
            operation.phase=Phase.EXPIRED; operation.code="RESERVATION_EXPIRED"; operation.consumed=true;
            plan.inspectionValid=false; plan.generation++; operation.permit.close(); releaseOperation(operation);
        }
    }
    private Operation operation(SessionLedger.Lease lease,String id) {
        var state=state(lease); var operation=state==null?null:state.operations.get(id);
        if(operation==null) throw new PlanRefusal(NOT_FOUND);
        if(operation.plan!=null) expireReservation(operation.plan); return operation;
    }
    public Status status(SessionLedger.Lease lease,String operationId) {
        var selected=guarded(lease,()->operation(lease,operationId));
        var handle=selected.cleanupHandle;
        boolean complete=false;
        if(handle!=null) {
            try { complete=handle.status()==ObservationResult.Cleanup.COMPLETE; }
            catch(RuntimeException inconclusive) { complete=false; }
        }
        final boolean cleanupComplete=complete;
        return guarded(lease,()-> {
            if(cleanupComplete && selected.cleanupHandle==handle) {
                selected.cleanup=Cleanup.COMPLETE; selected.cleanupHandle=null; releaseOperation(selected); clearRetired(selected.plan);
            }
            return selected.status();
        });
    }
    private void releaseOperation(Operation operation) {
        var plan=operation.plan;
        if(plan!=null && plan.active==operation) { plan.active=null; observationScratch--; }
        operation.plan=null; operation.permit=null; operation.cleanupHandle=null;
        clearRetired(plan);
    }
    public Submission claimCredentials(SessionLedger.Lease lease,String operationId) {
        var operation=guarded(lease,()-> {
            var selected=operation(lease,operationId);
            if(selected.consumed) throw new PlanRefusal(CREDENTIALS_ALREADY_CONSUMED);
            if(selected.plan.retired || selected.phase!=Phase.RESERVED) throw new PlanRefusal(NOT_FOUND);
            selected.consumed=true; selected.phase=Phase.RUNNING; selected.code="RUNNING"; selected.cleanup=Cleanup.IN_PROGRESS;
            return selected;
        });
        return new Submission(lease,operation);
    }
    /** Private-created one-shot authority; contains no credentials and cannot restore a revoked lease. */
    public final class Submission implements AutoCloseable {
        private final SessionLedger.Lease lease;
        private final Operation operation;
        private boolean started,closed,finished;
        private Submission(SessionLedger.Lease lease,Operation operation) { this.lease=lease; this.operation=operation; }
        public boolean cancelled() { return operation.cancellation.cancelled() || !live(lease); }
        public Status process(CredentialReader reader) {
            synchronized(lock) {
                if(started || closed) throw new PlanRefusal(CREDENTIALS_ALREADY_CONSUMED);
                started=true;
            }
            try { return processSubmission(lease,operation,reader); }
            finally { synchronized(lock) { finished=true; } }
        }
        @Override public void close() {
            synchronized(lock) {
                if(closed || finished) return;
                closed=true;
                if(started) { operation.cancellation.cancel(); return; }
                operation.permit.close();
                finish(operation,new ObservationResult.Refused(ObservationResult.Code.INVALID_SOURCE,ObservationResult.Cleanup.COMPLETE),new ContentResult.Rejected(List.of("OBSERVATION_REFUSED")),Map.of(),Optional.empty());
            }
        }
        @Override public String toString() { return "CredentialSubmission[redacted]"; }
    }
    public Status submit(SessionLedger.Lease lease,String operationId,CredentialReader reader) {
        try(var submission=claimCredentials(lease,operationId)) { return submission.process(reader); }
    }
    private void liveSubmission(SessionLedger.Lease lease,Operation operation) {
        guarded(lease,()-> {
            if(operation.plan==null || operation.plan.retired || operation.cancellation.cancelled()) throw new PlanRefusal(CANCELLED);
            return true;
        });
    }
    private Status processSubmission(SessionLedger.Lease lease,Operation operation,CredentialReader reader) {
        ObservationResult observed;
        char[] user=new char[256], password=new char[2048];
        try {
            liveSubmission(lease,operation);
            var lengths=reader.read(user,password);
            validateCredential(user,lengths.username(),128,512); validateCredential(password,lengths.password(),1024,4096);
            char[] ownedUser=Arrays.copyOf(user,lengths.username()), ownedPassword=Arrays.copyOf(password,lengths.password());
            try (var credentials=new TransientCredentials(ownedUser,ownedPassword)) {
                Arrays.fill(ownedUser,'\0'); Arrays.fill(ownedPassword,'\0');
                liveSubmission(lease,operation);
                try { observed=operation.permit.observe(credentials,operation.cancellation); }
                catch(RuntimeException inconclusive) {
                    observed=new ObservationResult.Refused(ObservationResult.Code.CLEANUP_INCONCLUSIVE,ObservationResult.Cleanup.INCONCLUSIVE);
                }
            } finally { Arrays.fill(ownedUser,'\0'); Arrays.fill(ownedPassword,'\0'); }
        } catch (RuntimeException refused) {
            operation.permit.close();
            observed=new ObservationResult.Refused(ObservationResult.Code.INVALID_SOURCE,ObservationResult.Cleanup.COMPLETE);
        } finally { Arrays.fill(user,'\0'); Arrays.fill(password,'\0'); }
        ContentResult projected=new ContentResult.Rejected(List.of("OBSERVATION_REFUSED"));
        Optional<PlanObservedDestination> observedContext=Optional.empty();
        if(observed instanceof ObservationResult.Complete complete && !operation.cancellation.cancelled()) {
            var expected=operation.plan.definition.compiled().checked();
            if(expected.logicalDigest().equals(complete.observation().logicalDigest())
                    && expected.bindingDigests().get(operation.plan.binding).equals(complete.observation().bindingDigest())) {
                try {
                    observedContext=Optional.of(PlanObservedDestination.from(complete.observation(),operation.plan.destination));
                    projected=content.project(operation.plan.definition,operation.plan.binding,complete.observation());
                }
                catch(RuntimeException refused) { projected=new ContentResult.Rejected(List.of("PROJECTION_REFUSED")); }
            }
        }
        Map<studio.environment.core.planning.TargetIntent.Ref,String> observedHandles=Map.of();
        if(projected instanceof ContentResult.Complete complete) {
            try {
                sourceBytes(complete.content());
                observedHandles=PlanHandles.observed(complete.content());
            }
            catch(RuntimeException limit) { projected=new ContentResult.Rejected(List.of("RESOURCE_LIMIT")); }
        }
        final ObservationResult result=observed; final ContentResult projection=projected;final var preparedHandles=observedHandles;final var preparedContext=observedContext;
        var installed=authority.guard(lease,()-> { synchronized(lock) { return finish(operation,result,projection,preparedHandles,preparedContext); } });
        if(installed.isPresent()) return installed.get();
        synchronized(lock) {
            operation.plan.retired=true; operation.cancellation.cancel();
            finish(operation,result,new ContentResult.Rejected(List.of("SESSION_REQUIRED")),Map.of(),Optional.empty());
        }
        throw new PlanRefusal(SESSION_REQUIRED);
    }
    private static void validateCredential(char[] value,int length,int maxPoints,int maxBytes) {
        if(length<1 || length>value.length) throw new PlanRefusal(INVALID_CREDENTIALS);
        int points=0,bytes=0;
        for(int index=0;index<length;index++) {
            char ch=value[index]; if(ch==0) throw new PlanRefusal(INVALID_CREDENTIALS);
            if(Character.isHighSurrogate(ch)) {
                if(++index>=length || !Character.isLowSurrogate(value[index])) throw new PlanRefusal(INVALID_CREDENTIALS);
                bytes+=4;
            } else if(Character.isLowSurrogate(ch)) throw new PlanRefusal(INVALID_CREDENTIALS);
            else bytes+=ch<128?1:ch<2048?2:3;
            points++;
        }
        if(points>maxPoints || bytes>maxBytes) throw new PlanRefusal(INVALID_CREDENTIALS);
    }
    private Status finish(Operation operation,ObservationResult result,ContentResult projected,Map<studio.environment.core.planning.TargetIntent.Ref,String> observedHandles,Optional<PlanObservedDestination> observedContext) {
        var plan=operation.plan;
        operation.cleanup=result.cleanup()==ObservationResult.Cleanup.COMPLETE?Cleanup.COMPLETE:Cleanup.INCONCLUSIVE;
        if(result instanceof ObservationResult.Refused refusal) operation.cleanupHandle=refusal.cleanupHandle().orElse(null);
        boolean cancelled=plan.retired || operation.cancellation.cancelled() || plan.generation!=operation.generation || !plan.revision.toString().equals(operation.revision);
        if(!cancelled && result instanceof ObservationResult.Complete complete && projected instanceof ContentResult.Complete accepted && observedContext.isPresent()) {
            long size=sourceBytes(accepted.content());
            long replacement=2*size;
            if(retainedBytes-plan.retainedBytes+replacement<=128*MIB) {
                retainedBytes+=replacement-plan.retainedBytes; plan.retainedBytes=replacement;
                enteredBytes-=plan.enteredBytes; plan.enteredBytes=0;
                plan.current=accepted.content(); plan.target=accepted.content(); plan.draft=Draft.empty(); plan.profiles.clear();
                plan.handles.clear(); plan.handles.putAll(observedHandles); plan.originals.clear();
                observedHandles.forEach((ref,handle)->plan.originals.put(handle,(studio.environment.core.planning.TargetIntent.Ref.Existing)ref));
                plan.observationFingerprint=complete.observation().fingerprint(); plan.observedDestination=observedContext.orElseThrow(); plan.inspectionValid=true;
                plan.revision=plan.revision.add(BigInteger.ONE); operation.installed=Optional.of(plan.revision.toString());
                operation.phase=Phase.SUCCEEDED; operation.code="SUCCEEDED";
            } else { operation.phase=Phase.REFUSED; operation.code="RESOURCE_LIMIT"; plan.inspectionValid=false; }
        } else {
            operation.phase=cancelled?Phase.CANCELLED:Phase.REFUSED;
            operation.code=cancelled?"CANCELLED":result instanceof ObservationResult.Refused refusal?refusal.code().name():"PROJECTION_REFUSED";
            plan.inspectionValid=false;
        }
        if(operation.cleanup==Cleanup.COMPLETE) releaseOperation(operation);
        clearRetired(plan);
        return operation.status();
    }
    private static long sourceBytes(Content content) {
        if(content.sources().size()>128 || content.graph().entities().size()>20_000 || content.graph().edges().size()>50_000) throw new PlanRefusal(RESOURCE_LIMIT);
        long bytes=0;
        for(var source:content.sources()) { bytes+=utf8(source.xml()); if(bytes>16*MIB) throw new PlanRefusal(RESOURCE_LIMIT); }
        return bytes;
    }
    static long utf8(String text) {
        long bytes=0;
        for(int index=0;index<text.length();index++) {
            char ch=text.charAt(index);
            if(Character.isHighSurrogate(ch)) {
                if(++index>=text.length() || !Character.isLowSurrogate(text.charAt(index))) throw new PlanRefusal(INVALID_REQUEST);
                bytes+=4;
            } else if(Character.isLowSurrogate(ch)) throw new PlanRefusal(INVALID_REQUEST);
            else bytes+=ch<128?1:ch<2048?2:3;
        }
        return bytes;
    }
    private void clearRetired(Plan plan) {
        if(plan==null || !plan.retired || plan.active!=null || plan.rendering || plan.readers>0) return;
        retainedBytes-=plan.retainedBytes; enteredBytes-=plan.enteredBytes;
        plan.retainedBytes=0; plan.enteredBytes=0; plan.current=null; plan.target=null; plan.draft=Draft.empty(); plan.profiles.clear(); plan.observationFingerprint=null; plan.observedDestination=null;
        plan.handles.clear(); plan.originals.clear();
        leases.values().forEach(state->{ if(state.plan==plan) state.plan=null; });
    }
    public Status cancel(SessionLedger.Lease lease,String operationId) {
        var selected=guarded(lease,()-> { var operation=operation(lease,operationId); cancelOperation(operation); return operation; });
        var handle=selected.cleanupHandle;
        if(handle!=null) {
            try { handle.cancel(); }
            catch(RuntimeException inconclusive) {
                guarded(lease,()-> { selected.cleanup=Cleanup.INCONCLUSIVE; return selected.status(); });
            }
        }
        return status(lease,operationId);
    }
    private void cancelOperation(Operation operation) {
        if(operation.plan==null || operation.plan.active!=operation) return;
        operation.plan.inspectionValid=false; operation.plan.generation++; operation.cancellation.cancel();
        if(operation.phase==Phase.RESERVED) {
            operation.consumed=true; operation.permit.close(); operation.cleanup=Cleanup.COMPLETE; releaseOperation(operation);
        }
        operation.phase=Phase.CANCELLED; operation.code="CANCELLED";
    }
    /** Session cleanup hook. Original unfinished cleanup only; never observes again or restores authority. */
    public void invalidate(SessionLedger.Lease lease) {
        List<Operation> pending;
        boolean localWork;
        synchronized(lock) {
            var state=state(lease); if(state==null) return;
            localWork=state.commandReaders>0 || state.plan!=null && (state.plan.rendering || state.plan.readers>0);
            if(state.plan!=null) {
                state.plan.retired=true; state.plan.inspectionValid=false;
                if(state.plan.active!=null) cancelOperation(state.plan.active);
                clearRetired(state.plan);
            }
            pending=state.operations.values().stream().filter(operation->operation.cleanup!=Cleanup.COMPLETE).toList();
        }
        boolean incomplete=localWork;
        for(var operation:pending) {
            var handle=operation.cleanupHandle;
            if(handle==null) { incomplete=true; continue; }
            boolean complete;
            try { handle.cancel(); complete=handle.retry()==ObservationResult.Cleanup.COMPLETE; }
            catch(RuntimeException failure) { complete=false; }
            synchronized(lock) {
                if(complete) { operation.cleanup=Cleanup.COMPLETE; operation.cleanupHandle=null; releaseOperation(operation); clearRetired(operation.plan); }
                else incomplete=true;
            }
        }
        if(incomplete) throw new PlanRefusal(CLEANUP_INCONCLUSIVE);
        synchronized(lock) { leases.remove(lease.id()); }
    }

    /** Internal immutable source for bounded view adapters; never an HTTP authority token. */
    public record ViewSnapshot(String revision,PublishedDefinition definition,String binding,Optional<Content> current,
            Optional<Content> target,Draft draft,Map<studio.environment.core.planning.TargetIntent.Ref,PlanCommand.Ref> references,
            Map<studio.environment.core.planning.TargetIntent.Ref,String> displayHandles) {
        public ViewSnapshot { references=Map.copyOf(references);displayHandles=Map.copyOf(displayHandles); }
        @Override public String toString() { return "ViewSnapshot[redacted]"; }
        public Content selected(boolean targetSide) { return (targetSide?target:current).orElseThrow(()->new PlanRefusal(targetSide?INCOMPLETE_TARGET:INSPECTION_REQUIRED)); }
        public String displayHandle(studio.environment.core.planning.TargetIntent.Ref ref) {
            var handle=displayHandles.get(ref);if(handle==null)throw new PlanRefusal(PROJECTION_REFUSED);return handle;
        }
        public PlanCommand.Ref reference(studio.environment.core.planning.TargetIntent.Ref ref) {
            // Incomplete draft intent may still point to a forgotten Fresh entity.
            // Such intent stays inspectable; only live entities can supply a display token.
            if(ref instanceof studio.environment.core.planning.TargetIntent.Ref.Fresh fresh) return new PlanCommand.Ref.Fresh(fresh.slot(),fresh.type());
            displayHandle(ref);
            var found=references.get(ref); if(found==null) throw new PlanRefusal(PROJECTION_REFUSED); return found;
        }
    }
    public ViewAdmission reserveView(SessionLedger.Lease lease,String planId) {
        return guarded(lease,()->{
            var state=ownedState(lease,planId);
            if(materializationScratch) throw new PlanRefusal(CAPACITY);
            materializationScratch=true; state.commandReaders++;
            viewScratch=new ViewAdmission(lease,planId,state); return viewScratch;
        });
    }
    public final class ViewAdmission implements AutoCloseable {
        private final SessionLedger.Lease lease; private final String planId; private final LeaseState state;
        private boolean used,executing,closed,released; private Plan pinned; private String revision; private long generation; private boolean inspection; private Operation active;
        private ViewAdmission(SessionLedger.Lease lease,String planId,LeaseState state) {this.lease=lease;this.planId=planId;this.state=state;}
        public boolean live() { return authority.guard(lease,()->{synchronized(lock){return !closed;}}).orElse(false); }
        public <T> T run(java.util.function.Supplier<T> action) {
            synchronized(lock) {if(closed || used || viewScratch!=this) throw new PlanRefusal(INVALID_REQUEST);used=true;executing=true;}
            try {return action.get();} finally {synchronized(lock){executing=false;if(closed)release();}}
        }
        public void pin(String requestedRevision) {
            guarded(lease,()->{
                if(closed || !executing || pinned!=null) throw new PlanRefusal(INVALID_REQUEST);
                var plan=plan(lease,planId);if(!plan.revision.toString().equals(requestedRevision))throw new PlanRefusal(CONFLICT);
                pinned=plan; revision=requestedRevision; generation=plan.generation; inspection=plan.inspectionValid; active=plan.active; plan.readers++;return true;
            });
        }
        public void verify() {guarded(lease,()->{check();return true;});}
        private void check() {
            if(closed || !executing || pinned==null || pinned.retired || pinned.generation!=generation || pinned.inspectionValid!=inspection || pinned.active!=active || !pinned.revision.toString().equals(revision)) throw new PlanRefusal(CONFLICT);
        }
        public ViewSnapshot snapshot() {
            return guarded(lease,()->{
                check();return HostedPlanService.snapshot(pinned);
            });
        }
        public studio.environment.core.graph.ObservedGraph.Key observed(String handle) {
            return guarded(lease,()->{check();var ref=pinned.originals.get(handle);if(ref==null)throw new PlanRefusal(INVALID_REQUEST);return ref.key();});
        }
        public Materialization materialize() {verify();var result=HostedPlanService.this.materialize(lease,planId,revision,this);verify();return result;}
        public Validation validation() {verify();var result=validate(lease,planId,revision);verify();return result;}
        public DocumentView document(boolean targetSide,String documentId,ViewMode mode,boolean disclosed) {
            if(!disclosed)throw new PlanRefusal(DISCLOSURE_REQUIRED);
            var snap=snapshot();
            var result=content.compare(snap,targetSide,documentId,mode);verify();return result;
        }
        public CapturedProfile capture(studio.environment.core.profile.ProfileCapture.Command command) {
            var snap=snapshot();guarded(lease,()->{check();inspected(pinned);if(pinned.active!=null || pinned.rendering)throw new PlanRefusal(PLAN_BUSY);return true;});
            var result=content.capture(snap.definition(),snap.binding(),snap.selected(false),command);
            if(utf8(result.source())>MIB)throw new PlanRefusal(RESOURCE_LIMIT);verify();return new CapturedProfile(result,snap.definition().reference());
        }
        public CompositionPreview preview(NativeCommand.Reference reference,List<String> roots) {
            var work=guarded(lease,()->{check();inspected(pinned);if(pinned.active!=null || pinned.rendering)throw new PlanRefusal(PLAN_BUSY);if(pinned.target==null)throw new PlanRefusal(INCOMPLETE_TARGET);return new CompositionSnapshot(pinned,revision,generation,pinned.target,pinned.draft,true);});
            var profile=workspace.profile(lease.owner(),reference,work.plan.definition);
            var result=HostedPlanService.preview(work,profile,roots);verify();return result;
        }
        @Override public void close() {synchronized(lock){if(closed)return;closed=true;if(!executing)release();}}
        private void release() {
            if(released)return;released=true;state.commandReaders--;
            if(pinned!=null){pinned.readers--;clearRetired(pinned);}
            if(viewScratch==this){viewScratch=null;materializationScratch=false;}
        }
        @Override public String toString(){return "ViewAdmission[redacted]";}
    }
    public boolean live(SessionLedger.Lease lease) { return authority.guard(lease,()->true).orElse(false); }
    public void requireOwned(SessionLedger.Lease lease,String planId) { guarded(lease,()->ownedState(lease,planId)); }
    /** Reserves the sole full materialization/command scratch before an HTTP body can be decoded. */
    public CommandAdmission reserveCommand(SessionLedger.Lease lease,String planId) {
        return guarded(lease,()-> {
            var state=ownedState(lease,planId);
            if(materializationScratch) throw new PlanRefusal(CAPACITY);
            materializationScratch=true; state.commandReaders++;
            commandScratch=new CommandAdmission(lease,planId,state); return commandScratch;
        });
    }
    public final class CommandAdmission implements AutoCloseable {
        private final SessionLedger.Lease lease;
        private final String planId;
        private final LeaseState state;
        private boolean used,closed,executing,released;
        private CommandAdmission(SessionLedger.Lease lease,String planId,LeaseState state) { this.lease=lease; this.planId=planId; this.state=state; }
        public boolean live() { return authority.guard(lease,()-> { synchronized(lock) { return !closed; } }).orElse(false); }
        public Ack execute(PlanCommand command) {
            synchronized(lock) { if(closed || used || commandScratch!=this) throw new PlanRefusal(INVALID_REQUEST); used=true; executing=true; }
            try { return executeCommand(this,command); }
            finally { synchronized(lock) { executing=false; if(closed) release(); } }
        }
        @Override public void close() {
            synchronized(lock) {
                if(closed) return; closed=true;
                if(!executing) release();
            }
        }
        private void release() {
            if(released) return; released=true; state.commandReaders--;
            if(commandScratch==this) { commandScratch=null; materializationScratch=false; }
        }
        @Override public String toString() { return "CommandAdmission[redacted]"; }
    }
    public Ack command(SessionLedger.Lease lease,String planId,PlanCommand command) {
        try(var admission=reserveCommand(lease,planId)) { return admission.execute(command); }
    }
    private Ack executeCommand(CommandAdmission admission,PlanCommand command) {
        var lease=admission.lease; String planId=admission.planId;
        var mutation=command.mutation(); uuid(mutation.requestId());
        if(!mutation.expectedRevision().matches("[1-9][0-9]{0,1023}")) throw new PlanRefusal(INVALID_REQUEST);
        String identity=identities.digest(lease.id(),planId,command.encoding());
        var old=guarded(lease,()->replay(ownedState(lease,planId),mutation.requestId(),identity));
        if(old.isPresent()) return old.get();
        if(command.action() instanceof PlanCommand.Action.Compose composition) return composeCommand(admission,command,composition,identity);
        var applied=guarded(lease,()-> {
            var state=ownedState(lease,planId); var previous=replay(state,mutation.requestId(),identity); if(previous.isPresent()) return new Applied(previous.get(),false);
            var plan=plan(lease,planId);
            if(command.action() instanceof PlanCommand.Action.Discard) {
                if(!mutation.expectedRevision().equals(plan.revision.toString())) throw new PlanRefusal(CONFLICT);
                var ack=plan.ack(); plan.retired=true; plan.inspectionValid=false; plan.generation++;
                if(plan.active!=null) cancelOperation(plan.active); clearRetired(plan);
                state.replay.put(mutation.requestId(),new Replay(identity,ack)); return new Applied(ack,false);
            }
            current(plan,mutation); inspected(plan);
            var draft=command.apply(plan.draft,ref->resolve(plan,ref));
            completeDecisions(plan,draft);
            long values=DraftEncoding.enteredBytes(draft);
            if(enteredBytes-plan.enteredBytes+values>64*MIB) throw new PlanRefusal(RESOURCE_LIMIT);
            var handles=PlanHandles.draft(plan.handles,draft);
            enteredBytes+=values-plan.enteredBytes; plan.enteredBytes=values;
            plan.handles.clear(); plan.handles.putAll(handles);
            plan.draft=draft; plan.revision=plan.revision.add(BigInteger.ONE); plan.generation++;
            dropTarget(plan); plan.diagnostics=List.of("TARGET_NOT_MATERIALIZED");
            var ack=plan.ack(); state.replay.put(mutation.requestId(),new Replay(identity,ack)); return new Applied(ack,true);
        });
        if(applied.changed) materialize(lease,planId,applied.ack.revision(),admission);
        return applied.ack;
    }
    private static studio.environment.core.planning.TargetIntent.Ref resolve(Plan plan,PlanCommand.Ref reference) {
        return switch(reference) {
            case PlanCommand.Ref.Existing existing -> {
                var ref=plan.originals.get(existing.handle()); if(ref==null) throw new PlanRefusal(INVALID_REQUEST); yield ref;
            }
            case PlanCommand.Ref.Fresh fresh -> new studio.environment.core.planning.TargetIntent.Ref.Fresh(fresh.slotId(),fresh.typeId());
        };
    }
    private static void completeDecisions(Plan plan,Draft draft) {
        var types=new HashMap<String,studio.environment.core.definitionv2.NativeDefinition.EntityType>();
        plan.definition.compiled().checked().definition().logical().entityTypes().forEach(type->types.put(type.id(),type));
        var relations=new HashMap<String,Set<String>>();
        plan.definition.compiled().checked().definition().logical().relations().stream().filter(r->r.kind()==studio.environment.core.definition.DefinitionDraft.RelationKind.REFERENCE)
            .forEach(r->relations.computeIfAbsent(r.fromType(),ignored->new HashSet<>()).add(r.id()));
        for(var entity:draft.intent().entities()) {
            var type=types.get(entity.entity().type()); if(type==null) throw new PlanRefusal(INVALID_REQUEST);
            if(entity instanceof studio.environment.core.planning.TargetIntent.EntityDecision.Remove) continue;
            var fields=entity instanceof studio.environment.core.planning.TargetIntent.EntityDecision.Retain r?r.fields():((studio.environment.core.planning.TargetIntent.EntityDecision.Create)entity).fields();
            var refs=entity instanceof studio.environment.core.planning.TargetIntent.EntityDecision.Retain r?r.references():((studio.environment.core.planning.TargetIntent.EntityDecision.Create)entity).references();
            if(!fields.keySet().equals(new HashSet<>(type.fields().stream().map(studio.environment.core.definitionv2.NativeDefinition.Field::id).toList()))
                    || !refs.keySet().equals(relations.getOrDefault(type.id(),Set.of()))) throw new PlanRefusal(INVALID_REQUEST);
        }
    }
    public static String compositionPreviewDigest(CompositionPreview preview) {
        return NativeWorkspaceDigests.hash("ES-PLAN-COMPOSITION-PREVIEW-1",Map.of("planId",preview.planId(),"revision",preview.revision(),
                "observationFingerprint",preview.observationFingerprint(),"profile",NativeWorkspaceDigests.reference(preview.profile()),
                "publicationDigest",preview.publicationDigest(),"selectedRoots",preview.roots(),"rootsDigest",preview.rootsDigest(),"closureDigest",preview.closureDigest()));
    }
    private Ack composeCommand(CommandAdmission admission,PlanCommand command,PlanCommand.Action.Compose composition,String identity) {
        var lease=admission.lease; var mutation=command.mutation();
        var snapshot=compositionSnapshot(lease,admission.planId,mutation.expectedRevision(),admission);
        Ack acknowledgement;
        try {
            var profile=workspace.profile(lease.owner(),composition.profile(),snapshot.plan.definition);
            var preview=preview(snapshot,profile,composition.selectedRoots());
            if(!compositionPreviewDigest(preview).equals(composition.previewDigest())) throw new PlanRefusal(STALE_PREVIEW);
            var targetKeys=new HashMap<studio.environment.core.planning.TargetIntent.Ref,studio.environment.core.graph.ObservedGraph.Key>();
            snapshot.target.provenance().forEach((key,ref)->{ if(targetKeys.putIfAbsent(ref,key)!=null) throw new PlanRefusal(PROJECTION_REFUSED); });
            var decisions=composition.decisions().stream().map(decision->switch(decision) {
                case PlanCommand.ProfileDecision.Create created -> (ProfileComposer.Decision)new ProfileComposer.Decision.Create(created.slotId(),created.targetSlotId());
                case PlanCommand.ProfileDecision.Cancel cancelled -> new ProfileComposer.Decision.Cancel(cancelled.slotId());
                case PlanCommand.ProfileDecision.UseExisting existing -> {
                    var key=targetKeys.get(resolve(snapshot.plan,existing.target())); if(key==null) throw new PlanRefusal(INVALID_REQUEST);
                    yield new ProfileComposer.Decision.UseExisting(existing.slotId(),key);
                }
            }).toList();
            var result=new ProfileComposer().compose(snapshot.plan.definition.compiled(),profile.checked(),preview.dependencies(),new GraphValidationResult.Accepted(snapshot.target.graph()),decisions);
            ProfileComposer.Draft proposal;
            if(result instanceof ProfileComposer.CompositionResult.Prepared prepared) proposal=prepared.draft();
            else if(result instanceof ProfileComposer.CompositionResult.NeedsResolution unresolved) proposal=unresolved.draft();
            else throw new PlanRefusal(PROFILE_REFUSED);
            var draft=PlanComposition.merge(snapshot.plan.definition.model(),snapshot.draft,snapshot.target,proposal);
            long values=DraftEncoding.enteredBytes(draft);
            acknowledgement=guarded(lease,()-> {
                compositionCurrent(snapshot); var plan=snapshot.plan; var state=ownedState(lease,admission.planId);
                var replay=replay(state,mutation.requestId(),identity); if(replay.isPresent()) return replay.get();
                if(!plan.profiles.contains(profile.publicationDigest()) && plan.profiles.size()>=100) throw new PlanRefusal(CAPACITY);
                if(enteredBytes-plan.enteredBytes+values>64*MIB) throw new PlanRefusal(RESOURCE_LIMIT);
                var handles=PlanHandles.draft(plan.handles,draft);
                enteredBytes+=values-plan.enteredBytes; plan.enteredBytes=values;
                plan.handles.clear(); plan.handles.putAll(handles);
                plan.profiles.add(profile.publicationDigest()); plan.draft=draft; plan.revision=plan.revision.add(BigInteger.ONE); plan.generation++;
                dropTarget(plan); plan.diagnostics=List.of("TARGET_NOT_MATERIALIZED");
                var ack=plan.ack(); state.replay.put(mutation.requestId(),new Replay(identity,ack)); return ack;
            });
        } finally { endComposition(snapshot); }
        materialize(lease,admission.planId,acknowledgement.revision(),admission);
        return acknowledgement;
    }

    public Ack replaceDraft(SessionLedger.Lease lease,String planId,Mutation mutation,Draft draft) {
        guarded(lease,()->ownedState(lease,planId));
        long values=DraftEncoding.enteredBytes(draft);
        String identity=identity(lease,planId,mutation,"replace-draft",DraftEncoding.encode(draft));
        var applied=guarded(lease,()-> {
            var state=ownedState(lease,planId);
            var previous=replay(state,mutation.requestId(),identity);
            if(previous.isPresent()) return new Applied(previous.get(),false);
            var plan=plan(lease,planId); current(plan,mutation); inspected(plan);
            if(enteredBytes-plan.enteredBytes+values>64*MIB) throw new PlanRefusal(RESOURCE_LIMIT);
            var handles=PlanHandles.draft(plan.handles,draft);
            enteredBytes+=values-plan.enteredBytes; plan.enteredBytes=values;
            plan.handles.clear(); plan.handles.putAll(handles);
            plan.draft=draft; plan.revision=plan.revision.add(BigInteger.ONE); plan.generation++;
            dropTarget(plan); plan.diagnostics=List.of("TARGET_NOT_MATERIALIZED");
            var acknowledgement=plan.ack(); state.replay.put(mutation.requestId(),new Replay(identity,acknowledgement));
            return new Applied(acknowledgement,true);
        });
        if(applied.changed) materialize(lease,planId,applied.ack.revision());
        return applied.ack;
    }
    private record Applied(Ack ack,boolean changed) { }
    private record Render(Plan plan,String revision,long generation,Content current,Draft draft) { }
    public record Materialization(boolean complete,List<String> diagnostics) {
        public Materialization { diagnostics=List.copyOf(diagnostics); }
    }
    private static void inspected(Plan plan) {
        if(!plan.inspectionValid || plan.current==null) throw new PlanRefusal(INSPECTION_REQUIRED);
    }
    private void dropTarget(Plan plan) {
        if(plan.target!=null) {
            long bytes=sourceBytes(plan.target); plan.retainedBytes-=bytes; retainedBytes-=bytes; plan.target=null;
        }
    }
    private boolean ownsScratch(Object admission) {
        return admission instanceof CommandAdmission command && commandScratch==command && !command.closed
            || admission instanceof ViewAdmission view && viewScratch==view && !view.closed;
    }
    public Materialization materialize(SessionLedger.Lease lease,String planId,String revision) { return materialize(lease,planId,revision,null); }
    private Materialization materialize(SessionLedger.Lease lease,String planId,String revision,Object admission) {
        var render=guarded(lease,()-> {
            var plan=plan(lease,planId); inspected(plan);
            if(!plan.revision.toString().equals(revision)) throw new PlanRefusal(CONFLICT);
            if(plan.active!=null || plan.rendering) throw new PlanRefusal(PLAN_BUSY);
            if(materializationScratch && !ownsScratch(admission)) throw new PlanRefusal(CAPACITY);
            // A single full old/new target scratch reservation exists before calling any XML adapter.
            materializationScratch=true; plan.rendering=true;
            return new Render(plan,revision,plan.generation,plan.current,plan.draft);
        });
        ContentResult result;
        try {
            result=content.materialize(render.plan.definition,render.plan.binding,render.current,render.draft);
            if(result instanceof ContentResult.Complete accepted) sourceBytes(accepted.content());
        } catch(RuntimeException refused) { result=new ContentResult.Rejected(List.of("TARGET_REFUSED")); }
        final ContentResult materialized=result;
        try {
            return guarded(lease,()-> {
                var plan=render.plan;
                if(plan.retired || !plan.inspectionValid || plan.generation!=render.generation || !plan.revision.toString().equals(render.revision)) throw new PlanRefusal(CONFLICT);
                if(materialized instanceof ContentResult.Complete accepted) {
                    long bytes=sourceBytes(accepted.content());
                    long old=plan.target==null?0:sourceBytes(plan.target);
                    if(retainedBytes-old+bytes>128*MIB) throw new PlanRefusal(RESOURCE_LIMIT);
                    retainedBytes+=bytes-old; plan.retainedBytes+=bytes-old; plan.target=accepted.content(); plan.diagnostics=List.of();
                    return new Materialization(true,List.of());
                }
                dropTarget(plan); plan.diagnostics=((ContentResult.Rejected)materialized).codes();
                return new Materialization(false,plan.diagnostics);
            });
        } finally {
            synchronized(lock) { if(admission==null) materializationScratch=false; render.plan.rendering=false; clearRetired(render.plan); }
        }
    }
    public record CapturedProfile(Capture draft,NativeCommand.Reference definition) {
        @Override public String toString() { return "CapturedProfile[redacted]"; }
    }
    public CapturedProfile capture(SessionLedger.Lease lease,String planId,String revision,studio.environment.core.profile.ProfileCapture.Command command) {
        var snapshot=guarded(lease,()-> {
            var plan=plan(lease,planId); inspected(plan);
            if(!plan.revision.toString().equals(revision)) throw new PlanRefusal(CONFLICT);
            if(plan.active!=null || plan.rendering) throw new PlanRefusal(PLAN_BUSY);
            if(materializationScratch) throw new PlanRefusal(CAPACITY);
            materializationScratch=true; plan.rendering=true;
            return new Render(plan,revision,plan.generation,plan.current,plan.draft);
        });
        try {
            var captured=content.capture(snapshot.plan.definition,snapshot.plan.binding,snapshot.current,command);
            if(utf8(captured.source())>MIB) throw new PlanRefusal(RESOURCE_LIMIT);
            return guarded(lease,()-> {
                if(snapshot.plan.retired || !snapshot.plan.inspectionValid || snapshot.generation!=snapshot.plan.generation || !revision.equals(snapshot.plan.revision.toString())) throw new PlanRefusal(CONFLICT);
                return new CapturedProfile(captured,snapshot.plan.definition.reference());
            });
        } finally { synchronized(lock) { materializationScratch=false; snapshot.plan.rendering=false; clearRetired(snapshot.plan); } }
    }
    public Ack discard(SessionLedger.Lease lease,String planId,Mutation mutation) {
        String identity=identity(lease,planId,mutation,"discard",List.of());
        return guarded(lease,()-> {
            var state=ownedState(lease,planId);
            var previous=replay(state,mutation.requestId(),identity); if(previous.isPresent()) return previous.get();
            var plan=plan(lease,planId);
            if(!mutation.expectedRevision().equals(plan.revision.toString())) throw new PlanRefusal(CONFLICT);
            var acknowledgement=plan.ack(); plan.retired=true; plan.inspectionValid=false; plan.generation++;
            if(plan.active!=null) cancelOperation(plan.active); clearRetired(plan);
            state.replay.put(mutation.requestId(),new Replay(identity,acknowledgement)); return acknowledgement;
        });
    }

    public record CompositionPreview(String planId,String revision,String observationFingerprint,
            NativeCommand.Reference profile,String publicationDigest,List<String> roots,String rootsDigest,
            String closureDigest,ProfileComposer.Preview dependencies) {
        public CompositionPreview { roots=List.copyOf(roots); }
        @Override public String toString() { return "CompositionPreview[redacted]"; }
    }
    private record CompositionSnapshot(Plan plan,String revision,long generation,Content target,Draft draft,boolean borrowedScratch) { }
    private CompositionSnapshot compositionSnapshot(SessionLedger.Lease lease,String planId,String revision) { return compositionSnapshot(lease,planId,revision,null); }
    private CompositionSnapshot compositionSnapshot(SessionLedger.Lease lease,String planId,String revision,CommandAdmission admission) {
        return guarded(lease,()-> {
            var plan=plan(lease,planId); inspected(plan);
            if(!plan.revision.toString().equals(revision)) throw new PlanRefusal(CONFLICT);
            if(plan.active!=null || plan.rendering) throw new PlanRefusal(PLAN_BUSY);
            if(plan.target==null) throw new PlanRefusal(INCOMPLETE_TARGET);
            if(materializationScratch && (admission==null || commandScratch!=admission || admission.closed)) throw new PlanRefusal(CAPACITY);
            materializationScratch=true; plan.rendering=true;
            return new CompositionSnapshot(plan,revision,plan.generation,plan.target,plan.draft,admission!=null);
        });
    }
    private void endComposition(CompositionSnapshot snapshot) {
        synchronized(lock) { if(!snapshot.borrowedScratch) materializationScratch=false; snapshot.plan.rendering=false; clearRetired(snapshot.plan); }
    }
    private static CompositionPreview preview(CompositionSnapshot snapshot,PublishedProfile profile,List<String> roots) {
        if(roots.size()>20_000 || new HashSet<>(roots).size()!=roots.size()) throw new PlanRefusal(INVALID_REQUEST);
        var selected=roots.isEmpty()?profile.checked().profile().entities().stream().map(Profile.Entity::id).sorted().toList():roots.stream().sorted().toList();
        var result=new ProfileComposer().preview(snapshot.plan.definition.compiled(),profile.checked(),new HashSet<>(selected));
        if(!(result instanceof ProfileComposer.PreviewResult.Proposed proposed)) throw new PlanRefusal(PROFILE_REFUSED);
        var dependencies=proposed.preview();
        var closure=Map.of("included",dependencies.included().stream().map(Profile.Entity::id).toList(),
                "relations",dependencies.relations().stream().map(relation->Map.of("type",relation.type(),"from",relation.from(),"to",relation.to())).toList());
        return new CompositionPreview(snapshot.plan.id,snapshot.revision,snapshot.plan.observationFingerprint,
                profile.reference(),profile.publicationDigest(),selected,NativeWorkspaceDigests.hash("ES-PLAN-ROOTS-1",selected),
                NativeWorkspaceDigests.hash("ES-PLAN-CLOSURE-1",closure),dependencies);
    }
    private static void compositionCurrent(CompositionSnapshot snapshot) {
        if(snapshot.plan.retired || !snapshot.plan.inspectionValid || snapshot.plan.generation!=snapshot.generation || !snapshot.plan.revision.toString().equals(snapshot.revision)) throw new PlanRefusal(CONFLICT);
    }
    public CompositionPreview previewProfile(SessionLedger.Lease lease,String planId,String revision,NativeCommand.Reference reference,List<String> roots) {
        var snapshot=compositionSnapshot(lease,planId,revision);
        try {
            var profile=workspace.profile(lease.owner(),reference,snapshot.plan.definition);
            var preview=preview(snapshot,profile,roots);
            return guarded(lease,()-> { compositionCurrent(snapshot); return preview; });
        } finally { endComposition(snapshot); }
    }
    private static Object previewInputs(CompositionPreview preview) {
        var dependencies=preview.dependencies();
        var detail=new TreeMap<String,Object>();
        detail.put("profileId",dependencies.profileId()); detail.put("revision",dependencies.revision()); detail.put("contentDigest",dependencies.contentDigest()); detail.put("logicalDigest",dependencies.logicalDigest());
        detail.put("selected",dependencies.selected());
        detail.put("included",dependencies.included().stream().map(entity->Map.of("id",entity.id(),"type",entity.type(),"label",entity.label(),"requiredInputs",entity.requiredInputs())).toList());
        detail.put("dependencies",dependencies.dependencies().stream().map(item->Map.of("slot",item.slot(),"causedBy",item.causedBy(),"relation",item.relation(),"reason",item.reason().name())).toList());
        detail.put("relations",dependencies.relations().stream().map(item->Map.of("type",item.type(),"from",item.from(),"to",item.to())).toList());
        detail.put("conflicts",dependencies.conflicts().stream().map(item->Map.of("code",item.code(),"slot",item.slot(),"relation",item.relation())).toList());
        return Map.of("planId",preview.planId(),"revision",preview.revision(),"profile",NativeWorkspaceDigests.reference(preview.profile()),
                "publicationDigest",preview.publicationDigest(),"observationFingerprint",preview.observationFingerprint(),"roots",preview.roots(),
                "rootsDigest",preview.rootsDigest(),"closureDigest",preview.closureDigest(),"dependencies",detail);
    }
    public Ack composeProfile(SessionLedger.Lease lease,String planId,Mutation mutation,CompositionPreview preview,List<ProfileComposer.Decision> decisions) {
        guarded(lease,()->ownedState(lease,planId));
        if(decisions.size()>20_000) throw new PlanRefusal(RESOURCE_LIMIT);
        var arguments=Map.of("preview",previewInputs(preview),"decisions",decisions.stream().map(decision->switch(decision) {
                    case ProfileComposer.Decision.Create created -> Map.of("kind","create","slot",created.slot(),"targetSlot",created.targetSlot());
                    case ProfileComposer.Decision.UseExisting existing -> Map.of("kind","existing","slot",existing.slot(),"type",existing.target().type(),"identity",existing.target().identity());
                    case ProfileComposer.Decision.Cancel cancelled -> Map.of("kind","cancel","slot",cancelled.slot());
                }).toList());
        String identity=identity(lease,planId,mutation,"compose-profile",arguments);
        var previous=guarded(lease,()->replay(ownedState(lease,planId),mutation.requestId(),identity));
        if(previous.isPresent()) return previous.get();
        var snapshot=compositionSnapshot(lease,planId,mutation.expectedRevision());
        Ack acknowledgement;
        try {
            var profile=workspace.profile(lease.owner(),preview.profile(),snapshot.plan.definition);
            var fresh=preview(snapshot,profile,preview.roots());
            if(!fresh.equals(preview)) throw new PlanRefusal(STALE_PREVIEW);
            var result=new ProfileComposer().compose(snapshot.plan.definition.compiled(),profile.checked(),fresh.dependencies(),new GraphValidationResult.Accepted(snapshot.target.graph()),decisions);
            ProfileComposer.Draft proposal;
            if(result instanceof ProfileComposer.CompositionResult.Prepared prepared) proposal=prepared.draft();
            else if(result instanceof ProfileComposer.CompositionResult.NeedsResolution unresolved) proposal=unresolved.draft();
            else throw new PlanRefusal(PROFILE_REFUSED);
            var draft=PlanComposition.merge(snapshot.plan.definition.model(),snapshot.draft,snapshot.target,proposal);
            long values=DraftEncoding.enteredBytes(draft);
            acknowledgement=guarded(lease,()-> {
                compositionCurrent(snapshot); var plan=snapshot.plan; var state=state(lease);
                var replay=replay(state,mutation.requestId(),identity); if(replay.isPresent()) return replay.get();
                if(!plan.profiles.contains(profile.publicationDigest()) && plan.profiles.size()>=100) throw new PlanRefusal(CAPACITY);
                if(enteredBytes-plan.enteredBytes+values>64*MIB) throw new PlanRefusal(RESOURCE_LIMIT);
                enteredBytes+=values-plan.enteredBytes; plan.enteredBytes=values;
                plan.profiles.add(profile.publicationDigest()); plan.draft=draft; plan.revision=plan.revision.add(BigInteger.ONE); plan.generation++;
                dropTarget(plan); plan.diagnostics=List.of("TARGET_NOT_MATERIALIZED");
                var ack=plan.ack(); state.replay.put(mutation.requestId(),new Replay(identity,ack)); return ack;
            });
        } finally { endComposition(snapshot); }
        materialize(lease,planId,acknowledgement.revision());
        return acknowledgement;
    }

    public record FieldView(String field,boolean present,boolean masked,Optional<String> value) {
        @Override public String toString() { return "FieldView[redacted]"; }
    }
    public record EntityView(String handle,String type,List<FieldView> fields) {
        public EntityView { fields=List.copyOf(fields); }
        @Override public String toString() { return "EntityView[redacted]"; }
    }
    public record EntityPage(String revision,int total,List<EntityView> entities) {
        public EntityPage { entities=List.copyOf(entities); }
        @Override public String toString() { return "EntityPage[redacted]"; }
    }
    public List<String> documents(SessionLedger.Lease lease,String planId,String revision) {
        return guarded(lease,()-> {
            var plan=plan(lease,planId); if(!plan.revision.toString().equals(revision)) throw new PlanRefusal(CONFLICT);
            return plan.current==null?List.of():plan.current.sources().stream().map(Source::documentId).sorted().toList();
        });
    }
    public EntityPage entities(SessionLedger.Lease lease,String planId,String revision,boolean target,int offset,int limit) {
        if(offset<0 || limit<1 || limit>100) throw new PlanRefusal(INVALID_REQUEST);
        return guarded(lease,()-> {
            var plan=plan(lease,planId); if(!plan.revision.toString().equals(revision)) throw new PlanRefusal(CONFLICT);
            var selected=target?plan.target:plan.current; if(selected==null) throw new PlanRefusal(target?INCOMPLETE_TARGET:INSPECTION_REQUIRED);
            var types=new HashMap<String,studio.environment.core.definitionv2.NativeDefinition.EntityType>();
            plan.definition.compiled().checked().definition().logical().entityTypes().forEach(type->types.put(type.id(),type));
            var ordered=selected.graph().entities().stream().sorted(Comparator.comparing(entity->entityHandle(plan,selected,entity.key()))).toList();
            var result=new ArrayList<EntityView>();
            for(var entity:ordered.subList(Math.min(offset,ordered.size()),Math.min(ordered.size(),(int)Math.min(Integer.MAX_VALUE,(long)offset+limit)))) {
                var fields=new ArrayList<FieldView>();
                for(var declaration:types.get(entity.key().type()).fields()) {
                    var value=entity.fields().get(declaration.id());
                    boolean masked=!declaration.readable() || declaration.sensitivity()==studio.environment.core.definition.DefinitionDraft.Sensitivity.SECRET || declaration.sensitivity()==studio.environment.core.definition.DefinitionDraft.Sensitivity.UNKNOWN;
                    fields.add(new FieldView(declaration.id(),value!=null,masked,masked?Optional.empty():Optional.ofNullable(value)));
                }
                fields.sort(Comparator.comparing(FieldView::field)); result.add(new EntityView(entityHandle(plan,selected,entity.key()),entity.key().type(),fields));
            }
            return new EntityPage(revision,ordered.size(),result);
        });
    }
    private static String entityHandle(Plan plan,Content content,studio.environment.core.graph.ObservedGraph.Key key) {
        var provenance=content.provenance().get(key); if(provenance==null) throw new PlanRefusal(PROJECTION_REFUSED);
        var handle=plan.handles.get(provenance);if(handle==null)throw new PlanRefusal(PROJECTION_REFUSED);return handle;
    }
    private static ViewSnapshot snapshot(Plan plan) {
        var refs=new HashMap<studio.environment.core.planning.TargetIntent.Ref,PlanCommand.Ref>();
        plan.originals.forEach((handle,ref)->refs.put(ref,new PlanCommand.Ref.Existing(handle)));
        return new ViewSnapshot(plan.revision.toString(),plan.definition,plan.binding,Optional.ofNullable(plan.current),Optional.ofNullable(plan.target),plan.draft,refs,plan.handles);
    }
    public DocumentView comparison(SessionLedger.Lease lease,String planId,String revision,boolean target,String documentId,ViewMode mode,boolean completeDocumentDisclosure) {
        if(!completeDocumentDisclosure) throw new PlanRefusal(DISCLOSURE_REQUIRED);
        record ComparisonWork(Plan plan,ViewSnapshot snapshot,long generation) { }
        var work=guarded(lease,()-> {
            var plan=plan(lease,planId); if(!plan.revision.toString().equals(revision)) throw new PlanRefusal(CONFLICT);
            var selected=target?plan.target:plan.current; if(selected==null) throw new PlanRefusal(target?INCOMPLETE_TARGET:INSPECTION_REQUIRED);
            var source=selected.sources().stream().filter(item->item.documentId().equals(documentId)).findFirst().orElseThrow(()->new PlanRefusal(NOT_FOUND));
            if(materializationScratch) throw new PlanRefusal(CAPACITY);
            materializationScratch=true; plan.readers++; return new ComparisonWork(plan,snapshot(plan),plan.generation);
        });
        try {
            var display=content.compare(work.snapshot,target,documentId,mode);
            return guarded(lease,()-> {
                if(work.plan.retired || !work.plan.revision.toString().equals(revision) || work.plan.generation!=work.generation) throw new PlanRefusal(CONFLICT);
                return display;
            });
        } finally { synchronized(lock) { materializationScratch=false; work.plan.readers--; clearRetired(work.plan); } }
    }
    public record Validation(String inputFingerprint,List<studio.environment.core.CheckResult> checks,
            Map<String,studio.environment.core.Outcome> applicationRules,boolean exportAvailable) {
        public Validation { checks=List.copyOf(checks); applicationRules=Map.copyOf(applicationRules); }
    }
    public Validation validate(SessionLedger.Lease lease,String planId,String revision) {
        return guarded(lease,()-> {
            var plan=plan(lease,planId); inspected(plan); if(!plan.revision.toString().equals(revision)) throw new PlanRefusal(CONFLICT);
            if(plan.active!=null || plan.rendering) throw new PlanRefusal(PLAN_BUSY);
            var fields=new TreeMap<String,Object>();
            fields.put("planId",plan.id); fields.put("revision",revision); fields.put("definitionPublication",plan.definition.publicationDigest());
            fields.put("profilePublications",List.copyOf(plan.profiles)); fields.put("observationFingerprint",plan.observationFingerprint==null?"":plan.observationFingerprint);
            fields.put("bindingId",plan.binding); fields.put("bindingDigest",plan.definition.compiled().checked().bindingDigests().get(plan.binding)); fields.put("destinationId",plan.destination.id());
            fields.put("draft",DraftEncoding.encode(plan.draft)); fields.put("current",sourceDigests(plan.current)); fields.put("target",sourceDigests(plan.target));
            fields.put("versions",Map.of("compiler","native-compiler-v2","parser","woodstox-7.2.2-xml10-fifth-edition-patch1","writer","structural-target-v1","rules","generic-graph-v1"));
            fields.put("documentPolicies",NativeWorkspaceDigests.policies(plan.definition.policies()));
            String fingerprint=NativeWorkspaceDigests.hash("ES-PLAN-INPUT-1",fields);
            boolean complete=plan.inspectionValid && plan.current!=null && plan.target!=null && !plan.retired;
            var checks=new ArrayList<studio.environment.core.CheckResult>();
            for(var category:studio.environment.core.RequiredCheck.values()) {
                var outcome=category==studio.environment.core.RequiredCheck.CLIENT_CAPABILITY || category==studio.environment.core.RequiredCheck.REVIEW || category==studio.environment.core.RequiredCheck.CONTENT_POLICY
                        ?studio.environment.core.Outcome.UNKNOWN:complete?studio.environment.core.Outcome.PASS:studio.environment.core.Outcome.UNKNOWN;
                checks.add(new studio.environment.core.CheckResult(category,outcome,fingerprint));
            }
            var rules=new TreeMap<String,studio.environment.core.Outcome>();
            for(var rule:plan.definition.compiled().checked().definition().logical().rules()) {
                var count=plan.target==null?BigInteger.ZERO:BigInteger.valueOf(plan.target.graph().entities().stream().filter(entity->entity.key().type().equals(rule.type())).count());
                rules.put(rule.id(),!complete?studio.environment.core.Outcome.UNKNOWN:count.compareTo(rule.minimum())>=0 && count.compareTo(rule.maximum())<=0?studio.environment.core.Outcome.PASS:studio.environment.core.Outcome.FAIL);
            }
            return new Validation(fingerprint,checks,rules,false);
        });
    }
    private static Object sourceDigests(Content content) {
        return content==null?List.of():content.sources().stream().sorted(Comparator.comparing(Source::documentId)).map(source->Map.of("documentId",source.documentId(),"sourceDigest",source.digest())).toList();
    }
    /** No qualified D07 capability is installed in this slice; caller validation/review cannot override it. */
    public void requestExport(SessionLedger.Lease lease,String planId,String revision) {
        guarded(lease,()-> { var plan=plan(lease,planId); if(!plan.revision.toString().equals(revision)) throw new PlanRefusal(CONFLICT); throw new PlanRefusal(EXPORT_UNAVAILABLE); });
    }

}
