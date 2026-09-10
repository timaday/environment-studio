package studio.environment.core.plan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.observation.ObservationResult;
import studio.environment.core.observation.ObservationResult.*;
import static studio.environment.core.plan.PlanReadback.Result.*;

class PlanReadbackTest {
    static final String A="<?xml version='1.0'?><mock a='𐀀'>é&amp;\r\n</mock>";
    static final String B="<other><!--invented unselected sibling--><![CDATA[raw &]]></other>";
    static String hash(String s) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new AssertionError(e); } }
    static Document doc(String id,String key,String xml) { return new Document(id,new Key("int64",key),xml,xml.getBytes(StandardCharsets.UTF_8).length,xml.length(),hash(xml)); }
    static List<Document> docs() { return List.of(doc("mock-a","1",A),doc("mock-b","2",B)); }
    static PlanReadback.Destination destination() { return new PlanReadback.Destination("postgresql","mock-destination","invented.invalid",5432,"invented_db","mock-tls-policy","mock-v1",Map.of("systemIdentifier","731","databaseOid","19","databaseName","invented_db")); }
    static PlanReadback.Expected expected() { return new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),destination(),docs()); }
    static Map<String,Object> evidence() {
        var d=destination();return Map.of("engine",d.engine(),"cleanup","complete","destination",Map.of("id",d.id(),"host",d.host(),"port",d.port(),"database",d.database(),"transportIdentity",d.transportIdentity(),"provisioningPolicyVersion",d.provisioningPolicyVersion(),"expectedPhysicalIdentity",d.physicalIdentity(),"observedPhysicalIdentity",d.physicalIdentity()),"metadata",Map.of("adapterVersion","jdbc-observation-v3","operationPolicyVersion","postgresql-read-operation-v1","visibility","complete","readOnlyOperation","verified","snapshot","repeatable-read-read-only"));
    }
    static ObservationResult observation(List<Document> documents,Map<String,Object> evidence) { return new Complete(new Observation("c".repeat(64),"a".repeat(64),"b".repeat(64),documents,evidence)); }
    @Test void reorderedExactWholeDocumentsMatch() { assertEquals(MATCHES,PlanReadback.compare(expected(),observation(List.of(docs().get(1),docs().get(0)),evidence()),()->false)); }
    @Test void missingDestinationEvidenceCannotProveMatchingState() { assertEquals(UNKNOWN,PlanReadback.compare(expected(),observation(docs(),Map.of()),()->false)); }
    @Test void cancellationCannotReturnMatchingState() { var c=new Cancellation();c.cancel();assertEquals(UNKNOWN,PlanReadback.compare(expected(),observation(docs(),evidence()),c::cancelled)); }

    @Test void validScalarTextKeysRetainControlsAndExactIdentity() {
        var document=new Document("mock-a",new Key("text","invented\nkey𐀀"),A,A.getBytes(StandardCharsets.UTF_8).length,A.length(),hash(A));
        var target=new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),destination(),List.of(document));
        assertEquals(MATCHES,PlanReadback.compare(target,observation(List.of(document),evidence()),()->false));
    }
    @Test void supplementaryCharactersCannotSkipCancellationChunkChecks() {
        String xml="x"+"𐀀".repeat(1024);var document=doc("mock-a","1",xml);
        var target=new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),destination(),List.of(document));
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(MATCHES,PlanReadback.compare(target,observation(List.of(document),evidence()),()->{calls.incrementAndGet();return false;}));
        assertTrue(calls.get()>=9,"Bounded cancellation checkpoints must include mixed UTF-16 alignment");
    }

    @Test void completeInventoryOrExactCharactersDifferWithoutNormalization() {
        var cases=List.of(List.of(docs().getFirst()),List.of(docs().getFirst(),doc("mock-c","3",B)),
            List.of(docs().getFirst(),docs().getLast(),doc("mock-c","3","<extra/>")),
            List.of(doc("mock-a","9",A),docs().getLast()),List.of(doc("mock-a","1",A.replace("\r\n","\n")),docs().getLast()),
            List.of(docs().getFirst(),doc("mock-b","2",B.replace("unselected","changed"))),
            List.of(docs().getFirst(),doc("mock-b","2",B.replace("<![CDATA[raw &]]>","raw &amp;"))));
        for(var documents:cases) assertEquals(DIFFERS,PlanReadback.compare(expected(),observation(documents,evidence()),()->false));
    }
    @Test void malformedInventoriesCannotBecomeKnownDifferences() {
        var original=docs().getFirst();var bad=new ArrayList<List<Document>>();
        bad.add(List.of());bad.add(List.of(original,original));bad.add(List.of(original,doc("mock-b","1",B)));
        bad.add(List.of(doc("Mock","1",A)));bad.add(List.of(doc("mock-a","01",A)));bad.add(List.of(doc("mock-a","-0",A)));
        bad.add(List.of(doc("mock-a","9223372036854775808",A)));bad.add(List.of(doc("mock-a","-9223372036854775809",A)));
        bad.add(List.of(doc("mock-a","1","")));bad.add(List.of(doc("mock-a","1","<x>\uD800</x>")));
        bad.add(List.of(new Document("mock-a",null,A,1,1,"a".repeat(64))));
        bad.add(List.of(new Document("mock-a",original.key(),null,1,1,"a".repeat(64))));
        bad.add(List.of(new Document("mock-a",original.key(),A,original.utf8Bytes()+1,A.length(),hash(A))));
        bad.add(List.of(new Document("mock-a",original.key(),A,original.utf8Bytes(),A.length()+1,hash(A))));
        bad.add(List.of(new Document("mock-a",original.key(),A,original.utf8Bytes(),A.length(),"f".repeat(64))));
        bad.add(List.of(new Document("mock-a",new Key("TEXT","1"),A,original.utf8Bytes(),A.length(),hash(A))));
        bad.add(List.of(original,new Document("mock-b",new Key("text","2"),B,B.length(),B.length(),hash(B))));
        for(var documents:bad) {
            assertEquals(UNKNOWN,PlanReadback.compare(expected(),observation(documents,evidence()),()->false));
            var error=assertThrows(IllegalArgumentException.class,()->new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),destination(),documents));
            assertEquals("INVALID_READBACK_EXPECTATION",error.getMessage());
        }
    }
    @Test void sourceBoundsAreInclusiveAndCheckedBeforeDefiniteResults() {
        String xml="<x>"+"a".repeat(1_048_576-7)+"</x>";
        var sixteen=new ArrayList<Document>();for(int i=0;i<16;i++)sixteen.add(doc("mock-"+i,""+i,xml));
        var target=new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),destination(),sixteen);
        assertEquals(MATCHES,PlanReadback.compare(target,observation(sixteen,evidence()),()->false));
        var excessive=new ArrayList<>(sixteen);excessive.add(doc("mock-extra","17","<x/>"));
        assertEquals(UNKNOWN,PlanReadback.compare(target,observation(excessive,evidence()),()->false));
        assertThrows(IllegalArgumentException.class,()->new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),destination(),excessive));
        assertEquals(UNKNOWN,PlanReadback.compare(expected(),observation(List.of(doc("mock-a","1",xml+"x")),evidence()),()->false));
        var count=new ArrayList<Document>();for(int i=0;i<128;i++)count.add(doc("mock-"+i,""+i,"<x/>"));
        var bounded=new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),destination(),count);
        assertEquals(MATCHES,PlanReadback.compare(bounded,observation(count,evidence()),()->false));
        count.add(doc("mock-extra","128","<x/>"));assertEquals(UNKNOWN,PlanReadback.compare(bounded,observation(count,evidence()),()->false));
    }
    @Test void keyBoundsAndUtf8WidthsAreExact() {
        for(String key:List.of("-9223372036854775808","9223372036854775807","0")) {
            var d=doc("mock-a",key,"<x>\u007f\u0080\u07ff\u0800\ud7ff\ue000\uffff𐀀􏿿</x>");
            var target=new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),destination(),List.of(d));
            assertEquals(MATCHES,PlanReadback.compare(target,observation(List.of(d),evidence()),()->false));
        }
        for(int n:List.of(256,257)) {
            var d=new Document("mock-a",new Key("text","𐀀".repeat(n)),A,A.getBytes(StandardCharsets.UTF_8).length,A.length(),hash(A));
            if(n==256) {
                var target=new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),destination(),List.of(d));
                assertEquals(MATCHES,PlanReadback.compare(target,observation(List.of(d),evidence()),()->false));
            } else assertEquals(UNKNOWN,PlanReadback.compare(expected(),observation(List.of(d),evidence()),()->false));
        }
    }
    @Test void everyRequiredEvidenceFieldIsEnforced() {
        for(String key:List.of("engine","cleanup","destination","metadata")) {
            var e=new HashMap<>(evidence());e.remove(key);assertEquals(UNKNOWN,PlanReadback.compare(expected(),observation(docs(),e),()->false));
        }
        for(String section:List.of("destination","metadata")) {
            @SuppressWarnings("unchecked") var original=(Map<String,Object>)evidence().get(section);
            for(String key:original.keySet()) {
                var changed=new HashMap<>(original);changed.put(key,"wrong");var e=new HashMap<>(evidence());e.put(section,changed);
                assertEquals(UNKNOWN,PlanReadback.compare(expected(),observation(docs(),e),()->false));
            }
            var changed=new HashMap<>(original);changed.put("extra","unqualified");var e=new HashMap<>(evidence());e.put(section,changed);
            assertEquals(UNKNOWN,PlanReadback.compare(expected(),observation(docs(),e),()->false));
        }
        for(String[] pins:List.of(new String[]{"bad","a".repeat(64),"b".repeat(64)},new String[]{"c".repeat(64),"d".repeat(64),"b".repeat(64)},new String[]{"c".repeat(64),"a".repeat(64),"d".repeat(64)}))
            assertEquals(UNKNOWN,PlanReadback.compare(expected(),new Complete(new Observation(pins[0],pins[1],pins[2],docs(),evidence())),()->false));
    }
    @Test void refusedOrUnavailableNeverRetriesCleanup() {
        var handle=new CleanupHandle(){public Cleanup status(){throw new AssertionError();}public Cleanup retry(){throw new AssertionError();}public void cancel(){throw new AssertionError();}};
        for(Code code:Code.values()) for(Cleanup cleanup:Cleanup.values())
            assertEquals(UNKNOWN,PlanReadback.compare(expected(),new Refused(code,cleanup,Optional.of(handle)),()->false));
        assertEquals(UNKNOWN,PlanReadback.compare(expected(),null,()->false));
        assertEquals(UNKNOWN,PlanReadback.compare(null,observation(docs(),evidence()),()->false));
        assertEquals(UNKNOWN,PlanReadback.compare(expected(),observation(docs(),evidence()),null));
    }
    @Test void cancellationAtEveryBoundaryAndOwnerFailureNeverReturnDefiniteState() {
        var d=doc("mock-a","1","<x/>");var target=new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),destination(),List.of(d));
        // Entry, inventory document/chunk/end, expected document, final result.
        for(int cancelAt=1;cancelAt<=6;cancelAt++) {var count=new java.util.concurrent.atomic.AtomicInteger();int boundary=cancelAt;
            assertEquals(UNKNOWN,PlanReadback.compare(target,observation(List.of(d),evidence()),()->count.incrementAndGet()>=boundary));
        }
        assertThrows(IllegalStateException.class,()->PlanReadback.compare(target,observation(List.of(d),evidence()),()->{throw new IllegalStateException("invented owner failure");}));
    }
    @Test void expectedIsDetachedAndDoesNotRenderContentOrIdentity() {
        var documents=new ArrayList<>(docs());var identities=new HashMap<>(destination().physicalIdentity());
        var d=new PlanReadback.Destination("postgresql","mock-destination","invented.invalid",5432,"invented_db","mock-tls-policy","mock-v1",identities);
        var target=new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),d,documents);documents.clear();identities.clear();
        assertEquals(MATCHES,PlanReadback.compare(target,observation(docs(),evidence()),()->false));
        assertThrows(UnsupportedOperationException.class,()->target.documents().clear());
        assertThrows(UnsupportedOperationException.class,()->target.destination().physicalIdentity().clear());
        assertEquals("ReadbackExpected[redacted]",target.toString());assertEquals("ReadbackDestination[redacted]",d.toString());
    }
    @Test void malformedExpectationHasOnlySafeDiagnostic() {
        assertEquals("INVALID_READBACK_EXPECTATION",assertThrows(IllegalArgumentException.class,()->new PlanReadback.Expected(1,"a".repeat(64),"b".repeat(64),destination(),docs())).getMessage());
        assertThrows(IllegalArgumentException.class,()->new PlanReadback.Expected(3,"bad","b".repeat(64),destination(),docs()));
        assertThrows(IllegalArgumentException.class,()->new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),null,docs()));
        assertThrows(IllegalArgumentException.class,()->new PlanReadback.Expected(3,"a".repeat(64),"b".repeat(64),destination(),null));
        assertThrows(IllegalArgumentException.class,()->new PlanReadback.Destination("oracle","mock","invented.invalid",1521,"db","tls","v1",destination().physicalIdentity()));
    }

    @Test void bothModelVersionsAndOracleIdentityUseExactWitnesses() {
        for(int version:List.of(2,3)) for(String engine:List.of("postgresql","oracle")) {
            Map<String,String> physical=engine.equals("postgresql")?destination().physicalIdentity():Map.of("dbid","731","dbUniqueName","invented_db","conId","3","conUid","19","conName","invented_pdb","pdbGuid","d".repeat(32));
            var d=new PlanReadback.Destination(engine,"mock-destination","invented.invalid",engine.equals("oracle")?1521:5432,"invented_db","mock-tls-policy","mock-v1",physical);
            var target=new PlanReadback.Expected(version,"a".repeat(64),"b".repeat(64),d,docs());
            var e=new HashMap<>(evidence());e.put("engine",engine);
            e.put("destination",Map.of("id",d.id(),"host",d.host(),"port",d.port(),"database",d.database(),"transportIdentity",d.transportIdentity(),"provisioningPolicyVersion",d.provisioningPolicyVersion(),"expectedPhysicalIdentity",physical,"observedPhysicalIdentity",physical));
            e.put("metadata",Map.of("adapterVersion","jdbc-observation-v"+version,"operationPolicyVersion",engine+"-read-operation-v1","visibility","complete","readOnlyOperation","verified","snapshot",engine.equals("oracle")?"read-only":"repeatable-read-read-only"));
            assertEquals(MATCHES,PlanReadback.compare(target,observation(docs(),e),()->false));
            @SuppressWarnings("unchecked") var endpoint=new HashMap<>((Map<String,Object>)e.get("destination"));
            var changed=new HashMap<>(physical);changed.put(engine.equals("oracle")?"pdbGuid":"systemIdentifier",engine.equals("oracle")?"e".repeat(32):"732");endpoint.put("observedPhysicalIdentity",changed);e.put("destination",endpoint);
            assertEquals(UNKNOWN,PlanReadback.compare(target,observation(docs(),e),()->false));
        }
    }
}
