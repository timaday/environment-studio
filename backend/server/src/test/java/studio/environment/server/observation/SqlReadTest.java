package studio.environment.server.observation;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import studio.environment.core.observation.ObservationPort;

class SqlReadTest {
    @Test void failedStatementConfigurationClosesAllocatedStatement() {
        var closes = new AtomicInteger();
        var statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (proxy, method, arguments) -> {
            if (method.getName().equals("setFetchSize")) throw new SQLException("invented-configuration-denial");
            if (method.getName().equals("close")) { closes.incrementAndGet(); return null; }
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            return null;
        });
        var connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, arguments) -> statement);
        var read = new SqlRead(connection, new ObservationPort.Cancellation(), System.nanoTime() + 1_000_000_000L);
        assertThrows(SQLException.class, () -> read.statement("SELECT 1"));
        assertEquals(1, closes.get(), "Failure before returning a statement still owns its cleanup");
        assertNull(read.active);
    }
}
