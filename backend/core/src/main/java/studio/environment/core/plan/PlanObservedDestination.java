package studio.environment.core.plan;

import java.util.*;
import studio.environment.core.observation.ObservationResult.Observation;

/** Owned display context copied from a completed registered adapter result; never a live connection. */
public record PlanObservedDestination(String engine,Map<String,String> identity,String observationFingerprint,boolean evidenceValid) {
    public PlanObservedDestination {
        identity=physical(engine,identity);
        require(observationFingerprint!=null && observationFingerprint.matches("[a-f0-9]{64}"));
    }
    @Override public String toString() { return "ObservedDestination[redacted]"; }
    PlanObservedDestination validity(boolean valid) { return new PlanObservedDestination(engine,identity,observationFingerprint,valid); }

    static PlanObservedDestination from(Observation observation,PlanPorts.Destination destination) {
        String engine=destination.engine().name().toLowerCase(Locale.ROOT);
        var evidence=observation.evidence();
        require(engine.equals(evidence.get("engine")) && "complete".equals(evidence.get("cleanup")));
        var endpoint=object(evidence.get("destination"));
        require(endpoint.keySet().equals(Set.of("id","host","port","database","transportIdentity","expectedPhysicalIdentity","observedPhysicalIdentity","provisioningPolicyVersion")));
        require(destination.id().equals(endpoint.get("id")));
        var identity=physical(engine,object(endpoint.get("observedPhysicalIdentity")));
        require(identity.equals(physical(engine,object(endpoint.get("expectedPhysicalIdentity")))));
        var metadata=object(evidence.get("metadata"));
        require(metadata.keySet().equals(Set.of("adapterVersion","operationPolicyVersion","visibility","readOnlyOperation","snapshot")));
        require("jdbc-observation-v2".equals(metadata.get("adapterVersion"))
                && (engine+"-read-operation-v1").equals(metadata.get("operationPolicyVersion"))
                && "complete".equals(metadata.get("visibility")) && "verified".equals(metadata.get("readOnlyOperation"))
                && (engine.equals("postgresql")?"repeatable-read-read-only":"read-only").equals(metadata.get("snapshot")));
        return new PlanObservedDestination(engine,identity,observation.fingerprint(),true);
    }
    private static Map<?,?> object(Object value) {
        if(!(value instanceof Map<?,?> map))throw refusal();
        return map;
    }
    private static Map<String,String> physical(String engine,Map<?,?> fields) {
        require(engine!=null && Set.of("postgresql","oracle").contains(engine));
        var numbers=engine.equals("postgresql")?Set.of("systemIdentifier","databaseOid"):Set.of("dbid","conId","conUid");
        var names=engine.equals("postgresql")?Set.of("databaseName"):Set.of("dbUniqueName","conName");
        var keys=new HashSet<>(numbers);keys.addAll(names);if(engine.equals("oracle"))keys.add("pdbGuid");
        require(fields!=null && fields.keySet().equals(keys));
        var result=new TreeMap<String,String>();
        for(String key:keys) {
            Object raw=fields.get(key);require(raw instanceof String);String value=(String)raw;
            if(numbers.contains(key))require(value.matches("[1-9][0-9]{0,19}"));
            else if(key.equals("pdbGuid"))require(value.matches("[a-f0-9]{32}"));
            else {
                require(!value.isBlank() && value.codePointCount(0,value.length())<=128);
                for(int i=0;i<value.length();) {
                    int cp=value.codePointAt(i);require(!Character.isISOControl(cp) && !(cp>=0xd800 && cp<=0xdfff));i+=Character.charCount(cp);
                }
            }
            result.put(key,value);
        }
        return Collections.unmodifiableMap(result);
    }
    private static void require(boolean condition) { if(!condition)throw refusal(); }
    private static PlanRefusal refusal() { return new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED); }
}
