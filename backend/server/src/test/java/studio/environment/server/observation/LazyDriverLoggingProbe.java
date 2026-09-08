package studio.environment.server.observation;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import studio.environment.core.observation.*;
import studio.environment.core.observation.ObservationResult.*;

/** Isolated process: LogManager configuration never changes the test runner or user configuration. */
public final class LazyDriverLoggingProbe {
    public static void main(String[] args) throws Exception {
        String name = args[0];
        if (name.equals("custom.oracle.diagnostic")) System.setProperty("oracle.jdbc.diagnostic.loggerName", name);
        var manager = LogManager.getLogManager();
        manager.readConfiguration(new ByteArrayInputStream((".level=INFO\norg.postgresql.level=INFO\noracle.jdbc.level=INFO\n" + name + ".level=FINEST\n").getBytes(StandardCharsets.UTF_8)));
        if (manager.getLogger(name) != null) throw new AssertionError("Probe logger must start uninitialized");
        var fixture = new ObservationLifecycleTest();
        var allocations = new AtomicInteger();
        var captured = new StringBuilder();
        var adapter = new JdbcObservation(fixture.destination, credentials -> {
            allocations.incrementAndGet();
            var logger = Logger.getLogger(name);
            logger.setUseParentHandlers(false);
            var handler = new Handler() {
                public void publish(LogRecord record) { captured.append(record.getMessage()); }
                public void flush() { }
                public void close() { }
            };
            handler.setLevel(Level.ALL); logger.addHandler(handler);
            char[] password = credentials.copyPassword();
            try { logger.finest(new String(password)); } finally { java.util.Arrays.fill(password, '\0'); }
            return fixture.fake(() -> {});
        }, () -> {}, TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(1));
        var credentials = fixture.credentials();
        var result = adapter.observe(fixture.selection(), credentials, new ObservationPort.Cancellation());
        if (!(result instanceof Refused refused) || refused.code() != Code.DESTINATION_UNQUALIFIED
            || allocations.get() != 0 || captured.length() != 0 || !credentials.closed()) {
            throw new AssertionError("Lazy configuration reached authentication factory=" + allocations.get()
                + "; in-memory canary captured=" + (captured.length() != 0));
        }
        // Initialize an actual pinned driver class only after refusal; its lazy property remains effective.
        if (name.equals("org.postgresql.core.v3.ConnectionFactoryImpl")) {
            Class.forName(name);
            if (Logger.getLogger(name).getLevel() != Level.FINEST)
                throw new AssertionError("Guard must preserve actual driver logging configuration");
        }
    }
}
