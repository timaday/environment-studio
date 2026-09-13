package studio.environment.server.observation;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.observation.*;
import studio.environment.core.observation.ObservationResult.*;
import studio.environment.server.definition.*;

/** Invented native-v3 declarations plus independently invented read-policy catalog/source facts. */
class V3ObservationTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static ObservationPort.V3Selection selection(Engine engine) throws Exception {
        var input=(ObjectNode)JSON.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v3/definition.json")));
        var result=assertInstanceOf(NativeCompilationResult.Incomplete.class,new NativeV3DefinitionBytesCompiler().compile(JSON.writeValueAsBytes(input),DefinitionBytesCompiler.Format.JSON));
        assertEquals(Set.of("MECHANISM_UNQUALIFIED"),result.diagnostics().stream().map(d->d.code()).collect(java.util.stream.Collectors.toSet()));
        return new ObservationPort.V3Selection(result.checked(),engine==Engine.POSTGRESQL?"mock-pg":"mock-oracle");
    }
    static class Database extends ReadOperationPolicyTest.Database {
        Database(Engine engine){super(engine);}
        @Override List<List<String>> rows(String original) throws SQLException {
            String normalized=original.replace("\"mock_pg\"","\"mock_owner\"").replace("\"mock_oracle\"","\"mock_owner\"");
            String sql=normalized.toLowerCase(Locale.ROOT);
            if(sql.equals("select count(*) from \"mock_owner\".\"mock_tiles\""))return ReadOperationPolicyTest.one("2");
            if(sql.equals("select \"mock_key\",\"mock_xml\" from \"mock_owner\".\"mock_tiles\""))return List.of(List.of("2",ReadOperationPolicyTest.XML),List.of("1",ReadOperationPolicyTest.XML));
            if(sql.startsWith("select \"mock_key\",pg_catalog.char_length")||sql.startsWith("select \"mock_key\",sys.dbms_lob.getlength")) {
                String length=Integer.toString(ReadOperationPolicyTest.XML.length());
                return List.of(List.of("2",length,length),List.of("1",length,length));
            }
            return super.rows(normalized);
        }
        JdbcObservation adapter(){return new JdbcObservation(destination(),c->open(),()->{},TimeUnit.SECONDS.toNanos(5),TimeUnit.SECONDS.toNanos(1));}
    }
    static TransientCredentials credentials(){return new TransientCredentials("mock_owner".toCharArray(),"invented-v3-canary".toCharArray());}
    @Test void actualJdbcPathReadsBothEnginesWithFreshV3DigestsAndReadOnlyOperationEvidence() throws Exception {
        for(var engine:Engine.values()) {
            var database=new Database(engine);var selection=selection(engine);var credentials=credentials();
            var result=assertInstanceOf(Complete.class,database.adapter().observeV3(selection,credentials,new ObservationPort.Cancellation()));
            assertTrue(credentials.closed());assertEquals(1,database.opens);assertEquals(1,database.rollbacks);assertEquals(1,database.closes);
            var observed=result.observation();
            assertEquals(selection.compiled().logicalDigest(),observed.logicalDigest());
            assertEquals(selection.compiled().bindingDigests().get(selection.bindingId()),observed.bindingDigest());
            assertEquals(List.of("glyph-sheet","palette-sheet"),observed.documents().stream().map(Document::documentId).toList());
            assertTrue(observed.documents().stream().allMatch(d->d.xml().equals(ReadOperationPolicyTest.XML)));
            var metadata=(Map<?,?>)observed.evidence().get("metadata");
            assertEquals("jdbc-observation-v3",metadata.get("adapterVersion"));assertEquals("verified",metadata.get("readOnlyOperation"));
            assertEquals(ObservationFingerprint.hash("ES-OBSERVATION-3",observed.evidence()),observed.fingerprint());
            assertNotEquals(ObservationFingerprint.hash(observed.evidence()),observed.fingerprint());
            // Separate Python oracle frames the contract's explicit invented result and existing v3 golden digests.
            assertEquals(engine==Engine.POSTGRESQL?"4bb4a39663c1a423c66f97a40d1a6775edbed79b924e7764d5d47425ae239836":"329b88567dc8d1865794d03660b3f645272416e82b64a062b6cd77a010f2cdae",observed.fingerprint());
        }
    }
    @Test void legacyAdapterStillMatchesItsIndependentWholeObservationOracle() {
        for(var engine:Engine.values()) {
            var observed=assertInstanceOf(Complete.class,new ReadOperationPolicyTest.Database(engine).observe()).observation();
            assertEquals(engine==Engine.POSTGRESQL?"0a5b04098e8ac67f164a81526290beabe2c1e02f993fab20a425c99876d481e4":"bf3467561c7e3cd8d037ea961c6a522893ebdb126c9a02f67a03750dc6dca86f",observed.fingerprint());
        }
    }
    @Test void changedCheckedEvidenceOrSelectionNeverConnectsAndAlwaysReleasesCapacity() throws Exception {
        var valid=selection(Engine.POSTGRESQL);var checked=valid.compiled();
        var mechanisms=new TreeMap<>(checked.mechanisms());mechanisms.put("native-compiler-v3",java.math.BigInteger.TWO);
        var noBindings=new studio.environment.core.definitionv3.NativeDefinition(checked.definition().id(),checked.definition().revision(),checked.definition().logical(),List.of());
        var bad=Arrays.asList(null,new ObservationPort.V3Selection(null,valid.bindingId()),new ObservationPort.V3Selection(checked,null),new ObservationPort.V3Selection(checked,"missing"),
            new ObservationPort.V3Selection(checked,"mock-oracle"),
            new ObservationPort.V3Selection(new NativeCompilationResult.Checked(checked.definition(),"0".repeat(64),checked.bindingDigests(),checked.mechanisms()),valid.bindingId()),
            new ObservationPort.V3Selection(new NativeCompilationResult.Checked(checked.definition(),checked.logicalDigest(),Map.of(),checked.mechanisms()),valid.bindingId()),
            new ObservationPort.V3Selection(new NativeCompilationResult.Checked(checked.definition(),checked.logicalDigest(),checked.bindingDigests(),mechanisms),valid.bindingId()),
            new ObservationPort.V3Selection(new NativeCompilationResult.Checked(noBindings,checked.logicalDigest(),checked.bindingDigests(),checked.mechanisms()),valid.bindingId()));
        var database=new Database(Engine.POSTGRESQL);
        for(var selected:bad) {
            var credentials=credentials();var result=assertInstanceOf(Refused.class,database.adapter().observeV3(selected,credentials,new ObservationPort.Cancellation()));
            assertEquals(Code.INVALID_SELECTION,result.code());assertEquals(Cleanup.COMPLETE,result.cleanup());assertTrue(credentials.closed());
        }
        assertEquals(0,database.opens);assertInstanceOf(Complete.class,database.adapter().observeV3(valid,credentials(),new ObservationPort.Cancellation()));
        assertEquals(1,database.opens);
    }
    @Test void v2AndV3ReservationsShareFourSlotsAndAreConsumedOnce() throws Exception {
        var database=new Database(Engine.POSTGRESQL);var adapter=database.adapter();var v3=selection(Engine.POSTGRESQL);
        var v2=ReadOperationPolicyTest.selection(ReadOperationPolicyTest.PG);var permits=new ArrayList<ObservationPort.Permit>();
        try {
            for(int i=0;i<4;i++)permits.add(assertInstanceOf(ObservationPort.Reservation.Admitted.class,i%2==0?adapter.reserve(v2):adapter.reserveV3(v3)).permit());
            assertEquals(Code.CAPACITY,assertInstanceOf(ObservationPort.Reservation.Refused.class,database.adapter().reserveV3(v3)).code());
            var capacityCredentials=credentials();assertEquals(Code.CAPACITY,assertInstanceOf(Refused.class,adapter.observeV3(v3,capacityCredentials,new ObservationPort.Cancellation())).code());assertTrue(capacityCredentials.closed());assertEquals(0,database.opens);
            permits.getFirst().close();permits.getFirst().close();
            var replacement=assertInstanceOf(ObservationPort.Reservation.Admitted.class,adapter.reserveV3(v3)).permit();permits.add(replacement);
            assertInstanceOf(ObservationPort.Reservation.Refused.class,adapter.reserve(v2));
            assertInstanceOf(Complete.class,permits.get(1).observe(credentials(),new ObservationPort.Cancellation()));
            var replayCredentials=credentials();assertEquals(Code.INVALID_SELECTION,assertInstanceOf(Refused.class,permits.get(1).observe(replayCredentials,new ObservationPort.Cancellation())).code());assertTrue(replayCredentials.closed());
            assertEquals(1,database.opens);
        } finally {permits.forEach(ObservationPort.Permit::close);}
        assertInstanceOf(Complete.class,adapter.observeV3(v3,credentials(),new ObservationPort.Cancellation()));
    }
    @Test void mixedVersionQuarantinesRetainCapacityUntilTheirActualOriginalClosuresFinish() throws Exception {
        var database=new Database(Engine.POSTGRESQL);var v3=selection(Engine.POSTGRESQL);var v2=ReadOperationPolicyTest.selection(ReadOperationPolicyTest.PG);
        var closing=new CountDownLatch(4);var release=new CountDownLatch(1);var allocations=new AtomicInteger();
        var controls=new ObservationLifecycleTest();
        var adapter=new JdbcObservation(database.destination(),c->{allocations.incrementAndGet();return controls.fake(()->{
            closing.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("mock-interrupted");}
        });},()->{},TimeUnit.SECONDS.toNanos(5),TimeUnit.MILLISECONDS.toNanos(60));
        var handles=new ArrayList<CleanupHandle>();
        try(var executor=Executors.newFixedThreadPool(4)) {
            var futures=new ArrayList<Future<ObservationResult>>();
            for(int i=0;i<4;i++){boolean newVersion=i%2==0;futures.add(executor.submit(()->newVersion?adapter.observeV3(v3,credentials(),new ObservationPort.Cancellation()):adapter.observe(v2,credentials(),new ObservationPort.Cancellation())));}
            try {
                assertTrue(closing.await(2,TimeUnit.SECONDS));
                for(var future:futures){var refused=assertInstanceOf(Refused.class,future.get(2,TimeUnit.SECONDS));assertEquals(Cleanup.INCONCLUSIVE,refused.cleanup());handles.add(refused.cleanupHandle().orElseThrow());}
                assertEquals(Code.CAPACITY,assertInstanceOf(ObservationPort.Reservation.Refused.class,database.adapter().reserveV3(v3)).code());
                assertEquals(Code.CAPACITY,assertInstanceOf(ObservationPort.Reservation.Refused.class,database.adapter().reserve(v2)).code());
                assertEquals(4,allocations.get());
            } finally {release.countDown();}
        }
        for(var handle:handles){for(int i=0;i<100&&handle.status()!=Cleanup.COMPLETE;i++)Thread.sleep(10);assertEquals(Cleanup.COMPLETE,handle.status());}
        assertInstanceOf(Complete.class,database.adapter().observeV3(v3,credentials(),new ObservationPort.Cancellation()));
    }
    @Test void cancellationMissingControlAndReadPolicyFailuresCloseCredentialsWithoutNewChannels() throws Exception {
        var selected=selection(Engine.POSTGRESQL);var database=new Database(Engine.POSTGRESQL);
        for(boolean missing:List.of(false,true)){
            var cancellation=new ObservationPort.Cancellation();cancellation.cancel();var credentials=credentials();
            assertEquals(missing?Code.INVALID_SELECTION:Code.CANCELLED,assertInstanceOf(Refused.class,database.adapter().observeV3(selected,credentials,missing?null:cancellation)).code());assertTrue(credentials.closed());
        }
        assertEquals(0,database.opens);
        for(var engine:Engine.values()) {
            var denied=new Database(engine);denied.readAccess=false;var credentials=credentials();
            assertEquals(Code.READ_ACCESS_DENIED,assertInstanceOf(Refused.class,denied.adapter().observeV3(selection(engine),credentials,new ObservationPort.Cancellation())).code());
            assertTrue(credentials.closed());assertEquals(1,denied.rollbacks);assertEquals(1,denied.closes);
        }
        assertEquals("V3Selection[REDACTED]",selected.toString());
    }
    @Test void unsupportedPortConsumesCredentialsWithoutDelegatingToV2() throws Exception {
        ObservationPort port=(s,c,x)->{fail("V3 must never delegate to legacy observation");return null;};
        var credentials=credentials();var selected=selection(Engine.POSTGRESQL);
        assertEquals(Code.DESTINATION_UNQUALIFIED,assertInstanceOf(Refused.class,port.observeV3(selected,credentials,new ObservationPort.Cancellation())).code());
        assertTrue(credentials.closed());
        assertEquals(Code.DESTINATION_UNQUALIFIED,assertInstanceOf(ObservationPort.Reservation.Refused.class,port.reserveV3(selected)).code());
    }
}
