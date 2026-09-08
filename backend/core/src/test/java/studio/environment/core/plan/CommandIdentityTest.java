package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CommandIdentityTest {
    @Test void independentPythonHmacVectorPinsDomainFramingCaseAndOwnerPlanBoundaries() {
        byte[] key=new byte[32]; for(int index=0;index<key.length;index++) key[index]=(byte)index;
        var identity=new CommandIdentity(key);
        String digest=identity.digest("lease","plan",Map.of("kind","edit","entered","MiXeD"));
        assertEquals("47161ad3ce18698f53fe31c381dc2a7834acf560c42b84c3b1a8bfd266773cec",digest);
        assertEquals(digest,identity.digest("lease","plan",new LinkedHashMap<>(Map.of("entered","MiXeD","kind","edit"))));
        assertNotEquals(digest,identity.digest("lease","plan",Map.of("kind","edit","entered","mixed")));
        assertNotEquals(digest,identity.digest("lease-other","plan",Map.of("kind","edit","entered","MiXeD")));
        assertNotEquals(digest,identity.digest("lease","plan-other",Map.of("kind","edit","entered","MiXeD")));
    }
}
