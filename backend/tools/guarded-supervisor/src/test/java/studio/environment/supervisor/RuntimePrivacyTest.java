package studio.environment.supervisor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimePrivacyTest {
    private static final String CLOSED="Limit                     Soft Limit           Hard Limit           Units\nMax core file size        0                    0                    bytes\n";
    @Test void pipedOrUnavailableCoreCollectorIsNotQualifiedByLimitsAlone(){assertTrue(RuntimePrivacy.fileCorePolicy("core.%p\n"));for(String value:new String[]{"","\n","|/usr/share/apport/apport %p","core\nextra"})assertFalse(RuntimePrivacy.fileCorePolicy(value));}
    @Test void onlyClosedCoreAndJvmCrashDestinationsCanBeAdmitted(){
        assertTrue(RuntimePrivacy.accepts(CLOSED,"/dev/null","false","false"));
        for(String invalid:new String[]{"",CLOSED.replace("0                    0","0                    unlimited"),CLOSED.replace("0                    0","1                    0"),CLOSED+CLOSED,CLOSED.replace("bytes","records")})assertFalse(RuntimePrivacy.accepts(invalid,"/dev/null","false","false"));
        assertFalse(RuntimePrivacy.accepts(CLOSED,"hs_err_pid%p.log","false","false"));
        assertFalse(RuntimePrivacy.accepts(CLOSED,"/dev/null","true","false"));
        assertFalse(RuntimePrivacy.accepts(CLOSED,"/dev/null","false","true"));
    }
}
