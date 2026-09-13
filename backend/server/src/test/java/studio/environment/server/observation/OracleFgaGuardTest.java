package studio.environment.server.observation;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.*;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.observation.*;
import static org.junit.jupiter.api.Assertions.*;

class OracleFgaGuardTest {
    private final List<String> queries=new ArrayList<>();
    private final List<Object> bound=new ArrayList<>();
    private final AtomicInteger sourceBarrier=new AtomicInteger();
    private boolean enabled=true, denied=false;
    private Object zero(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null;}
    private ResultSet rows(List<List<String>> data){
        int[] position={-1};
        var meta=(ResultSetMetaData)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSetMetaData.class},(p,m,a)->m.getName().equals("getColumnCount")?(data.isEmpty()?1:data.getFirst().size()):zero(m.getReturnType()));
        return (ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)->switch(m.getName()){case "next"->++position[0]<data.size();case "getMetaData"->meta;case "getString"->data.get(position[0]).get((Integer)a[0]-1);default->zero(m.getReturnType());});
    }
    private Connection connection(){return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
        if(!m.getName().equals("prepareStatement"))return zero(m.getReturnType());
        String query=(String)a[0];queries.add(query);
        return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(sp,sm,sa)->{
            if(sm.getName().equals("getFetchSize"))return 1;
            if(sm.getName().equals("setObject")&&query.contains("dba_audit_policies")){bound.add(sa[1]);return null;}
            if(sm.getName().equals("executeQuery")){
                if(query.contains("dba_audit_policies")){
                    if(denied)throw new SQLException("independent-denied-catalog-value-canary");
                    assertTrue(query.contains("enabled='YES'"));assertFalse(query.contains("sel="));assertFalse(query.contains("pf_schema"));assertFalse(query.contains("pf_package"));
                    return rows(enabled?List.of(List.of("1")):List.of());
                }
                if(query.contains("version_full"))return rows(List.of(List.of("23.26.3.0.0")));
                if(query.contains("NLS_CHARACTERSET"))return rows(List.of(List.of("AL32UTF8")));
                if(query.contains("v_$option"))return rows(List.of(List.of("Oracle Label Security","FALSE"),List.of("Oracle Database Vault","FALSE")));
                if(query.contains("'ISDBA'"))return rows(List.of(List.of("FALSE")));
                if(query.equals("SELECT SYS_CONTEXT('USERENV','SESSION_USER') FROM SYS.DUAL"))return rows(List.of(List.of("mock_reader")));
                assertFalse(query.contains("FROM \"MockOwner\".\"MockDocs\""),"No source SELECT before complete policy qualification");
                return rows(List.of());
            }
            return zero(sm.getReturnType());
        });
    });}
    private ObservationResult observe(){
        var binding=new Binding("mock",Engine.ORACLE,Storage.CLOB,"MockOwner","MockDocs","ID","XML_DATA",KeyType.TEXT,List.of(new NativeDefinition.Document("mock.doc","one",List.of())));
        var model=new NativeDefinition("mock",BigInteger.ONE,new Logical(List.of(),List.of(),List.of(),List.of()),List.of(binding));
        var selection=new ObservationPort.Selection(new NativeCompilationResult.ReadyToPublish(new NativeCompilationResult.Checked(model,"mock-logical",Map.of("mock","mock-binding"),Map.of())),"mock");
        var destination=new ObservationDestination("mock",Engine.ORACLE,"127.0.0.1",1,"MOCKPDB",ObservationDestination.Transport.DISPOSABLE_LOOPBACK,"","mock-transport",Map.of("dbid","1","dbUniqueName","MOCK","conId","3","conUid","2","conName","MOCKPDB","pdbGuid","a".repeat(32)),"mock-policy","oracle-read-operation-v1");
        var credentials=new TransientCredentials("reader".toCharArray(),"independent-password-canary".toCharArray());
        var result=new JdbcObservation(destination,c->connection(),sourceBarrier::incrementAndGet,java.util.concurrent.TimeUnit.SECONDS.toNanos(5),java.util.concurrent.TimeUnit.SECONDS.toNanos(1)).observe(selection,credentials,new ObservationPort.Cancellation());
        assertTrue(credentials.closed());assertEquals(ObservationResult.Cleanup.COMPLETE,result.cleanup());assertEquals(0,sourceBarrier.get());assertFalse(result.toString().contains("canary"));return result;
    }
    @Test void enabledPolicyRefusesWithoutInferringStatementOrHandlerSafety(){
        assertEquals(ObservationResult.Code.VISIBILITY_UNQUALIFIED,assertInstanceOf(ObservationResult.Refused.class,observe()).code());
        assertEquals(List.of("MockOwner","MockDocs"),bound);
    }
    @Test void deniedCompleteCatalogHasSafeMetadataFailure(){denied=true;assertEquals(ObservationResult.Code.METADATA_UNAVAILABLE,assertInstanceOf(ObservationResult.Refused.class,observe()).code());assertEquals(List.of("MockOwner","MockDocs"),bound);}
    @Test void disabledPolicyDoesNotAuthorizeRemainingMetadata(){enabled=false;assertEquals(ObservationResult.Code.METADATA_UNAVAILABLE,assertInstanceOf(ObservationResult.Refused.class,observe()).code());assertEquals(List.of("MockOwner","MockDocs"),bound);assertTrue(queries.stream().anyMatch(q->q.contains("v_$containers")));}
}
