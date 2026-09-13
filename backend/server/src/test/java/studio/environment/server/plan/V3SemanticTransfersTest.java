package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.session.*;
import studio.environment.server.session.HostedSessions;

class V3SemanticTransfersTest {
    final SessionLedger.Lease lease=new SessionLedger.Lease("invented-semantic-transfer",new Owner("https://semantic.invalid","owner"),Instant.MAX);
    final HostedSessions sessions=new HostedSessions(Clock.systemUTC(),List.of());
    @Test void oneSemanticPlusFourMetadataAndFourCredentialRecordsFitInEveryOrder() {
        for(String order:List.of("SMC","SCM","MSC","MCS","CSM","CMS")) {
            var registry=new V3PlanTransfers();var records=new ArrayList<V3PlanTransfers.Operation>();
            try {
                for(char kind:order.toCharArray())for(int index=0;index<(kind=='S'?1:4);index++)
                    records.add(assertDoesNotThrow(()->switch(kind){
                        case 'S'->registry.admitSemantic(lease);case 'M'->registry.admitMetadata(lease);
                        case 'C'->registry.admitCredentials(lease);default->throw new AssertionError("MOCK_KIND");
                    },"independent capacity order "+order));
                assertEquals(9,records.size());
                capacity(()->registry.admitSemantic(lease));capacity(()->registry.admitMetadata(lease));
                capacity(()->registry.admitCredentials(lease));capacity(PlanController::metadata);
            } finally {records.forEach(record->record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions));}
            assertFalse(registry.awaitingWork(lease));
        }
    }
    @Test void semanticProgressKeepsCapacityUntilConclusiveOriginalSettlement() {
        var registry=new V3PlanTransfers();var record=registry.admitSemantic(lease);
        var other=new SessionLedger.Lease("another-generation",lease.owner(),Instant.MAX);
        try {
            record.settlement(OwnedAsyncCompletion.Outcome.IN_PROGRESS,sessions);
            capacity(()->registry.admitSemantic(other));assertTrue(registry.awaitingWork(lease));
            record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);
            var replacement=registry.admitSemantic(other);
            try {
                record.settlement(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,sessions);
                assertFalse(registry.awaitingWork(lease));assertFalse(replacement.cancelled());
                capacity(()->registry.admitSemantic(lease));
            } finally {replacement.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);}
        } finally {record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);}
        assertFalse(registry.awaitingWork(other));
    }
    @Test void terminalSemanticUncertaintyBlocksOnlyItsOwnRecordClass() {
        var registry=new V3PlanTransfers();var record=registry.admitSemantic(lease);
        record.settlement(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,sessions);
        record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);
        assertTrue(record.cancelled());assertTrue(registry.awaitingWork(lease));
        capacity(()->registry.admitSemantic(lease));
        var records=new ArrayList<V3PlanTransfers.Operation>();
        try {
            for(int index=0;index<4;index++)records.add(registry.admitMetadata(lease));
            for(int index=0;index<4;index++)records.add(registry.admitCredentials(lease));
            capacity(()->registry.admitSemantic(lease));
        } finally {records.forEach(value->value.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions));}
        assertTrue(registry.awaitingWork(lease));capacity(()->registry.admitSemantic(lease));
    }
    interface Admission {Object run();}
    static void capacity(Admission admission) {assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,admission::run).code());}
}
