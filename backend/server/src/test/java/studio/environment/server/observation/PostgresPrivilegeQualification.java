package studio.environment.server.observation;

import java.util.*;
import studio.environment.core.observation.ObservationResult.*;
import static studio.environment.server.observation.DisposableObservationQualification.*;

/** Independent actual catalog grant challenges; never invokes a managed write from the adapter. */
final class PostgresPrivilegeQualification {
    static void run(ObservationDestination destination) throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-", "").substring(0,12);
        String role="es_privilege_"+suffix, sequence="mock_pg.mock_sequence_"+suffix;
        pg("CREATE ROLE "+role+" NOLOGIN; GRANT "+role+" TO "+reader+" WITH INHERIT FALSE; CREATE SEQUENCE "+sequence+";");
        String domain="mock_pg.mock_domain_"+suffix, wrapper="mock_fdw_"+suffix, server="mock_server_"+suffix;
        pg("CREATE DOMAIN "+domain+" AS text; CREATE FOREIGN DATA WRAPPER "+wrapper+"; CREATE SERVER "+server+" FOREIGN DATA WRAPPER "+wrapper+";");
        String lob=pg("SELECT lo_create(0);");check(lob.matches("[0-9]+"),"MOCK_LARGE_OBJECT_ID_INVALID");
        var failures=new ArrayList<String>();
        for(String grantee:List.of(reader,role,"PUBLIC")) {
            String kind=grantee.equals(reader)?"direct":grantee.equals(role)?"reachable-role":"PUBLIC";
            challenge(destination,"sequence-USAGE-"+kind,"GRANT USAGE ON SEQUENCE "+sequence+" TO "+grantee,"REVOKE USAGE ON SEQUENCE "+sequence+" FROM "+grantee,failures);
            challenge(destination,"sequence-UPDATE-"+kind,"GRANT UPDATE ON SEQUENCE "+sequence+" TO "+grantee,"REVOKE UPDATE ON SEQUENCE "+sequence+" FROM "+grantee,failures);
            challenge(destination,"MAINTAIN-"+kind,"GRANT MAINTAIN ON mock_pg.mock_tiles TO "+grantee,"REVOKE MAINTAIN ON mock_pg.mock_tiles FROM "+grantee,failures);
            challenge(destination,"large-object-UPDATE-"+kind,"GRANT UPDATE ON LARGE OBJECT "+lob+" TO "+grantee,"REVOKE UPDATE ON LARGE OBJECT "+lob+" FROM "+grantee,failures);
            challenge(destination,"foreign-wrapper-USAGE-"+kind,"GRANT USAGE ON FOREIGN DATA WRAPPER "+wrapper+" TO "+grantee,"REVOKE USAGE ON FOREIGN DATA WRAPPER "+wrapper+" FROM "+grantee,failures);
            challenge(destination,"foreign-server-USAGE-"+kind,"GRANT USAGE ON FOREIGN SERVER "+server+" TO "+grantee,"REVOKE USAGE ON FOREIGN SERVER "+server+" FROM "+grantee,failures);
        }
        for(String grantee:List.of(reader,role)) {
            String kind=grantee.equals(reader)?"direct":"reachable-role";
            challenge(destination,"domain-owner-"+kind,"ALTER DOMAIN "+domain+" OWNER TO "+grantee,"ALTER DOMAIN "+domain+" OWNER TO postgres",failures);
            challenge(destination,"domain-USAGE-delegation-"+kind,"GRANT USAGE ON DOMAIN "+domain+" TO "+grantee+" WITH GRANT OPTION","REVOKE GRANT OPTION FOR USAGE ON DOMAIN "+domain+" FROM "+grantee,failures);
            challenge(destination,"sequence-owner-"+kind,"ALTER SEQUENCE "+sequence+" OWNER TO "+grantee,"ALTER SEQUENCE "+sequence+" OWNER TO postgres",failures);
            challenge(destination,"large-object-owner-"+kind,"ALTER LARGE OBJECT "+lob+" OWNER TO "+grantee,"ALTER LARGE OBJECT "+lob+" OWNER TO postgres",failures);
            for(String object:List.of("TABLE mock_pg.mock_tiles","SEQUENCE "+sequence,"LARGE OBJECT "+lob)) {
                challenge(destination,"SELECT-delegation-"+object.split(" ")[0]+"-"+kind,"GRANT SELECT ON "+object+" TO "+grantee+" WITH GRANT OPTION","REVOKE GRANT OPTION FOR SELECT ON "+object+" FROM "+grantee,failures);
            }
            challenge(destination,"column-SELECT-delegation-"+kind,"GRANT SELECT(mock_xml) ON mock_pg.mock_tiles TO "+grantee+" WITH GRANT OPTION","REVOKE GRANT OPTION FOR SELECT(mock_xml) ON mock_pg.mock_tiles FROM "+grantee,failures);
            challenge(destination,"schema-USAGE-delegation-"+kind,"GRANT USAGE ON SCHEMA mock_pg TO "+grantee+" WITH GRANT OPTION","REVOKE GRANT OPTION FOR USAGE ON SCHEMA mock_pg FROM "+grantee,failures);
            challenge(destination,"database-CONNECT-delegation-"+kind,"GRANT CONNECT ON DATABASE postgres TO "+grantee+" WITH GRANT OPTION","REVOKE GRANT OPTION FOR CONNECT ON DATABASE postgres FROM "+grantee,failures);
            challenge(destination,"function-EXECUTE-delegation-"+kind,"GRANT EXECUTE ON FUNCTION pg_control_system() TO "+grantee+" WITH GRANT OPTION","REVOKE GRANT OPTION FOR EXECUTE ON FUNCTION pg_control_system() FROM "+grantee,failures);
        }
        pg("REVOKE "+role+" FROM "+reader+";");
        check(failures.isEmpty(),"PG_UNQUALIFIED_PRIVILEGES_ACCEPTED_"+failures);
        check(observe(destination) instanceof Complete,"PG_READ_ONLY_BASELINE_NOT_RESTORED");backendGone(studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL);
        System.out.println("POSTGRESQL direct/reachable-role/PUBLIC sequence/MAINTAIN/large-object and delegation policy PASS");
    }
    static void challenge(ObservationDestination destination,String label,String grant,String revoke,List<String> failures) throws Exception {
        pg(grant+";");
        try {
            var result=observe(destination);
            if(!(result instanceof Refused r && r.code()==Code.ACCOUNT_NOT_READ_ONLY && r.cleanup()==Cleanup.COMPLETE))failures.add(label+"="+result);
            else passed++;
            backendGone(studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL);
        } finally {pg(revoke+";");}
    }
}
