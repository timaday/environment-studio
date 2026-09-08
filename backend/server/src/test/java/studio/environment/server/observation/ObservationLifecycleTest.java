package studio.environment.server.observation;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.*;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.observation.*;
import studio.environment.core.observation.ObservationResult.*;

class ObservationLifecycleTest {
    final ObservationDestination destination = new ObservationBoundaryTest().destination();
    ObservationPort.Selection selection() {
        var binding=new Binding("invented",Engine.POSTGRESQL,Storage.TEXT,"invented","sample","key","xml",KeyType.INT64,List.of(new NativeDefinition.Document("sample","1",List.of())));
        var definition=new NativeDefinition("invented",BigInteger.ONE,new Logical(List.of(),List.of(),List.of(),List.of()),List.of(binding));
        return new ObservationPort.Selection(new NativeCompilationResult.ReadyToPublish(new NativeCompilationResult.Checked(definition,"invented-logical",Map.of("invented","invented-binding"),Map.of())),"invented");
    }
    TransientCredentials credentials() { return new TransientCredentials("invented".toCharArray(),"owned-secret-canary".toCharArray()); }
    Connection fake(Runnable close) {
        return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement"))throw new SQLException("independent-metadata-denial-canary");
            if(method.getName().equals("close")){close.run();return null;}
            if(method.getReturnType()==boolean.class)return false;
            if(method.getReturnType()==int.class)return 0;
            return null;
        });
    }
    @Test void fifthOperationRefusesBeforeFactoryAllocation() throws Exception {
        var entered=new CountDownLatch(4); var release=new CountDownLatch(1); var calls=new AtomicInteger();
        var adapter=new JdbcObservation(destination,credentials->{calls.incrementAndGet();entered.countDown();try{release.await();}catch(InterruptedException e){throw new SQLException();}return fake(()->{});},()->{},TimeUnit.SECONDS.toNanos(5),TimeUnit.SECONDS.toNanos(1));
        try(var executor=Executors.newFixedThreadPool(4)) {
            var pending=new ArrayList<Future<ObservationResult>>();
            for(int i=0;i<4;i++)pending.add(executor.submit(()->adapter.observe(selection(),credentials(),new ObservationPort.Cancellation())));
            try {
                assertTrue(entered.await(2,TimeUnit.SECONDS));
                var refused=adapter.observe(selection(),credentials(),new ObservationPort.Cancellation());
                assertEquals(Code.CAPACITY,((Refused)refused).code()); assertEquals(4,calls.get());
            } finally {release.countDown();}
            for(var future:pending)assertEquals(Cleanup.COMPLETE,future.get(3,TimeUnit.SECONDS).cleanup());
        }
    }
    @Test void cleanupTimeoutRetainsOriginalWorkerUntilCloseActuallyFinishes() throws Exception {
        var closing=new CountDownLatch(1); var release=new CountDownLatch(1); var credentials=credentials();
        var adapter=new JdbcObservation(destination,c->fake(()->{closing.countDown();try{release.await();}catch(InterruptedException interrupted){throw new IllegalStateException("mock-close-interrupted");}}),()->{},TimeUnit.SECONDS.toNanos(5),TimeUnit.MILLISECONDS.toNanos(100));
        try {
            var result=adapter.observe(selection(),credentials,new ObservationPort.Cancellation());
            assertInstanceOf(Refused.class,result); assertEquals(Cleanup.INCONCLUSIVE,result.cleanup());
            var handle=((Refused)result).cleanupHandle().orElseThrow();
            assertEquals(Cleanup.INCONCLUSIVE,handle.status()); assertTrue(credentials.closed(),"Authentication buffers must clear before a stalled resource close");
            release.countDown();
            for(int attempt=0;attempt<100 && handle.status()!=Cleanup.COMPLETE;attempt++)Thread.sleep(10);
            assertEquals(Cleanup.COMPLETE,handle.status()); assertTrue(credentials.closed());
        } finally {release.countDown();}
    }

    @Test void fourQuarantinesRefuseAdmissionUntilOriginalClosuresComplete() throws Exception {
        var closing = new CountDownLatch(4); var release = new CountDownLatch(1); var allocations = new AtomicInteger();
        var adapter = new JdbcObservation(destination, c -> {
            allocations.incrementAndGet();
            return fake(() -> { closing.countDown(); try { release.await(); } catch (InterruptedException failure) { throw new IllegalStateException("mock-close-interrupted"); } });
        }, () -> {}, TimeUnit.SECONDS.toNanos(5), TimeUnit.MILLISECONDS.toNanos(60));
        var handles = new ArrayList<CleanupHandle>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<ObservationResult>>();
            for (int index = 0; index < 4; index++) futures.add(executor.submit(() -> adapter.observe(selection(), credentials(), new ObservationPort.Cancellation())));
            try {
                assertTrue(closing.await(2, TimeUnit.SECONDS));
                for (var future : futures) {
                    var result = assertInstanceOf(Refused.class, future.get(2, TimeUnit.SECONDS));
                    assertEquals(Cleanup.INCONCLUSIVE, result.cleanup()); handles.add(result.cleanupHandle().orElseThrow());
                }
                assertEquals(Code.CAPACITY, ((Refused) adapter.observe(selection(), credentials(), new ObservationPort.Cancellation())).code());
                assertEquals(4, allocations.get());
            } finally { release.countDown(); }
        }
        for (var handle : handles) {
            for (int attempt = 0; attempt < 100 && handle.status() != Cleanup.COMPLETE; attempt++) Thread.sleep(10);
            assertEquals(Cleanup.COMPLETE, handle.status());
        }
        assertEquals(Cleanup.COMPLETE, adapter.observe(selection(), credentials(), new ObservationPort.Cancellation()).cleanup());
        assertEquals(5, allocations.get());
    }

    @Test void selectionSafePrintDoesNotExposeDefinitionOrBinding() {
        assertEquals("Selection[REDACTED]", selection().toString());
    }

    @Test void deadlineKeepsAdmissionReservedUntilOriginalConnectAndCleanupFinish() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var calls = new AtomicInteger();
        var credentials = credentials();
        var adapter = new JdbcObservation(destination, c -> {
            calls.incrementAndGet(); entered.countDown();
            try { release.await(); } catch (InterruptedException interrupted) { throw new SQLException(); }
            return fake(() -> {});
        }, () -> {}, TimeUnit.MILLISECONDS.toNanos(60), TimeUnit.MILLISECONDS.toNanos(60));
        try {
            var result = adapter.observe(selection(), credentials, new ObservationPort.Cancellation());
            assertEquals(0, entered.getCount());
            var refused = assertInstanceOf(Refused.class, result);
            assertEquals(Code.DEADLINE_EXCEEDED, refused.code());
            var handle = refused.cleanupHandle().orElseThrow();
            assertEquals(Cleanup.INCONCLUSIVE, handle.retry());
            assertEquals(1, calls.get(), "Cleanup retry must never reconnect");
            release.countDown();
            for (int attempt = 0; attempt < 100 && handle.status() != Cleanup.COMPLETE; attempt++) Thread.sleep(10);
            assertEquals(Cleanup.COMPLETE, handle.status());
            assertTrue(credentials.closed());
        } finally { release.countDown(); }
    }

    @Test void lazilyConfiguredDriverLoggerRefusesInIsolatedJvm() throws Exception {
        for (String name : List.of("org.postgresql.core.v3.ConnectionFactoryImpl", "oracle.jdbc.driver", "oracle.jdbc.internal.replay",
                "oracle.jdbc.driver.resource.InstalledProviders", "oracle.jdbc.replay.driver.ReplayLoggerFactory", "custom.oracle.diagnostic")) {
            var process = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                LazyDriverLoggingProbe.class.getName(), name).redirectErrorStream(true).start();
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), "Lazy logger pre-auth refusal failed: " + name + "; " + output);
        }
    }

    @Test void verboseChildLoggerCannotBypassQuietParentBeforeAuthentication() {
        for(String name : List.of("org.postgresql.core.v3.ConnectionFactoryImpl", "oracle.jdbc.driver.T4CConnection")) {
            var parent = java.util.logging.Logger.getLogger(name.startsWith("org.postgresql") ? "org.postgresql" : "oracle.jdbc");
            var child = java.util.logging.Logger.getLogger(name);
            var oldParent = parent.getLevel(); var oldChild = child.getLevel(); var oldHandlers = child.getUseParentHandlers();
            var captured = new StringBuilder(); var allocations = new AtomicInteger(); var credentials = credentials();
            var handler = new java.util.logging.Handler() {
                @Override public void publish(java.util.logging.LogRecord record) { captured.append(record.getMessage()); }
                @Override public void flush() { }
                @Override public void close() { }
            };
            handler.setLevel(java.util.logging.Level.ALL);
            try {
                parent.setLevel(java.util.logging.Level.INFO); child.setLevel(java.util.logging.Level.FINEST);
                child.setUseParentHandlers(false); child.addHandler(handler);
                var adapter = new JdbcObservation(destination, c -> {
                    allocations.incrementAndGet(); char[] password = c.copyPassword();
                    try { child.finest(new String(password)); } finally { Arrays.fill(password, '\0'); }
                    return fake(() -> {});
                }, () -> {}, TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(1));
                var result = adapter.observe(selection(), credentials, new ObservationPort.Cancellation());
                assertAll(() -> assertEquals(Code.DESTINATION_UNQUALIFIED, ((Refused) result).code()),
                    () -> assertEquals(0, allocations.get()),
                    () -> assertFalse(captured.toString().contains("owned-secret-canary"), "Authentication canary reached verbose child logger"),
                    () -> assertTrue(credentials.closed()));
            } finally {
                child.removeHandler(handler); child.setUseParentHandlers(oldHandlers); child.setLevel(oldChild); parent.setLevel(oldParent);
            }
        }
    }

    @Test void verboseDriverLoggingRefusesBeforeCredentialsCanReachDriver() {
        var logger=java.util.logging.Logger.getLogger("org.postgresql");
        var previous=logger.getLevel(); var allocations=new AtomicInteger(); var credentials=credentials();
        try {
            logger.setLevel(java.util.logging.Level.FINE);
            var adapter=new JdbcObservation(destination,c->{allocations.incrementAndGet();return fake(()->{});},()->{},TimeUnit.SECONDS.toNanos(1),TimeUnit.SECONDS.toNanos(1));
            var result=adapter.observe(selection(),credentials,new ObservationPort.Cancellation());
            assertEquals(Code.DESTINATION_UNQUALIFIED,((Refused)result).code());
            assertEquals(0,allocations.get()); assertTrue(credentials.closed());
        } finally {logger.setLevel(previous);}
    }
}
