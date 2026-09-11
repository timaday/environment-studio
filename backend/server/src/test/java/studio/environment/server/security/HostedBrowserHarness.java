package studio.environment.server.security;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.SpringApplication;
import studio.environment.server.EnvironmentStudioApplication;
import studio.environment.server.plan.PlanHttpTestConfiguration;
import studio.environment.server.plan.V3DocumentHttpTestConfiguration;
import studio.environment.server.plan.V3WorkflowHttpTestConfiguration;
import studio.environment.server.workspace.SqliteDraftStore;
import studio.environment.server.workspace.V3WorkflowStorageFixtures;
import studio.environment.core.session.Owner;

/** Actual HTTPS/OIDC/workspace harness; plan modes use explicit test-only observation. */
public final class HostedBrowserHarness {
    private static final class BoundedLog extends OutputStream {
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        public synchronized void write(int value){if(bytes.size()>=16_777_216)throw new IllegalStateException("MOCK_LOG_LIMIT");bytes.write(value);}
        synchronized boolean contains(String value){return bytes.toString(StandardCharsets.UTF_8).contains(value);}
        synchronized void clear(){bytes.reset();}
    }
    @FunctionalInterface private interface Cleanup { void run() throws Exception; }
    private static final class Owned implements AutoCloseable {
        private final Deque<Cleanup> actions=new ArrayDeque<>();
        private final PrintStream original; private boolean closed;
        Owned(PrintStream original){this.original=original;}
        synchronized void add(Cleanup cleanup)throws Exception {
            if(closed){cleanup.run();throw new IllegalStateException("MOCK_ALREADY_CLOSED");}
            actions.push(cleanup);
        }
        public synchronized void close(){
            if(closed)return;closed=true;boolean complete=true;
            while(!actions.isEmpty())try{actions.pop().run();}catch(Throwable failure){complete=false;}
            original.println(complete?"MOCK_CLEANUP_COMPLETE":"MOCK_CLEANUP_INCONCLUSIVE");
        }
    }
    public static void main(String[] args)throws Exception {
        var out=System.out;try {start(mode(args));}catch(Throwable failure){for(Throwable cause=failure;cause!=null;cause=cause.getCause()){out.println("MOCK_START_FAILURE "+cause.getClass().getSimpleName());if(cause.getMessage()!=null && cause.getMessage().matches("[A-Z][A-Z0-9_]{1,100}"))out.println(cause.getMessage());}throw new IllegalStateException("MOCK_START_FAILED");}
    }
    enum Mode { LEGACY, DEFINITIONS_V3, DEFINITIONS_V3_REFUSAL, PLANS_V3, PROFILES_V3 }
    static Mode mode(String[] args) {
        if(args.length==0)return Mode.LEGACY;
        if(args.length==1)return switch(args[0]) {
            case "definitions-v3" -> Mode.DEFINITIONS_V3;
            case "definitions-v3-refusal" -> Mode.DEFINITIONS_V3_REFUSAL;
            case "plans-v3" -> Mode.PLANS_V3;
            case "profiles-v3" -> Mode.PROFILES_V3;
            default -> throw new IllegalArgumentException("MOCK_INVALID_MODE");
        };
        throw new IllegalArgumentException("MOCK_INVALID_MODE");
    }
    private static void start(Mode mode)throws Exception {
        int listenerPort=mode==Mode.PROFILES_V3 || mode==Mode.DEFINITIONS_V3_REFUSAL?18445:18443;
        int controlPort=mode==Mode.PROFILES_V3 || mode==Mode.DEFINITIONS_V3_REFUSAL?18446:18444;
        var original=System.out;var captured=new BoundedLog();System.setOut(new PrintStream(captured,true,StandardCharsets.UTF_8));System.setErr(new PrintStream(captured,true,StandardCharsets.UTF_8));
        var owned=new Owned(original);
        owned.add(captured::clear);
        Runtime.getRuntime().addShutdownHook(new Thread(owned::close,"hosted-browser-cleanup"));
        try {
        var root=Files.createTempDirectory(Path.of("/dev/shm"),"es-browser-mock-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        owned.add(()->{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}});
        var store=Files.createDirectory(root.resolve("workspace"),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));if(mode==Mode.LEGACY)SqliteDraftStore.initialize(store);else SqliteDraftStore.initializeV3(store);
        String password=UUID.randomUUID().toString();var key=root.resolve("listener.p12");
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/keytool").toString(),"-genkeypair","-alias","mock","-keyalg","RSA","-keysize","2048","-validity","1","-storetype","PKCS12","-keystore",key.toString(),"-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.1","-noprompt").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        owned.add(()->{if(process.isAlive()){process.destroyForcibly();if(!process.waitFor(10,TimeUnit.SECONDS))throw new IllegalStateException("MOCK_TLS_CLEANUP_INCONCLUSIVE");}});
        byte[] answer=(password+"\n"+password+"\n").getBytes(StandardCharsets.UTF_8);
        try(var input=process.getOutputStream()){input.write(answer);input.flush();}finally{Arrays.fill(answer,(byte)0);}
        if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("MOCK_TLS_TIMEOUT");}if(process.exitValue()!=0)throw new IllegalStateException("MOCK_TLS_UNAVAILABLE");
        var issuer=new MockIssuer();owned.add(issuer::close);issuer.subject="browser-maintainer";
        // Matching invented historical definition; actual compiler/store, no production qualification.
        if(mode==Mode.PROFILES_V3)V3WorkflowStorageFixtures.definition(store,
                new Owner(issuer.issuer(),"browser-maintainer"),V3WorkflowHttpTestConfiguration.OBJECT);
        var properties=new HashMap<String,Object>();properties.put("studio.mode","hosted");properties.put("spring.profiles.active","oidc-test");properties.put("studio.security.allow-test-http","true");
        properties.put("studio.security.public-origin","https://localhost:"+listenerPort);properties.put("studio.security.issuer",issuer.issuer());properties.put("studio.security.client-id","mock-client");properties.put("studio.security.client-secret","mock-platform-secret");
        properties.put("studio.workspace.directory",store.toString());properties.put("studio.workspace.definition-publishers[0].issuer",issuer.issuer());properties.put("studio.workspace.definition-publishers[0].subject","browser-maintainer");
        properties.put("server.address","127.0.0.1");properties.put("server.port",Integer.toString(listenerPort));properties.put("server.ssl.enabled","true");properties.put("server.ssl.key-store",key.toUri().toString());properties.put("server.ssl.key-store-password",password);properties.put("server.ssl.key-store-type","PKCS12");
        properties.put("spring.web.resources.static-locations",Path.of("../../frontend/dist").toAbsolutePath().normalize().toUri().toString()+"/");properties.put("logging.level.org.springframework.web","DEBUG");properties.put("logging.level.org.springframework.web.client.DefaultRestClient","TRACE");
        var app=switch(mode) {
            case DEFINITIONS_V3, DEFINITIONS_V3_REFUSAL -> new SpringApplication(EnvironmentStudioApplication.class);
            case LEGACY -> new SpringApplication(EnvironmentStudioApplication.class,PlanHttpTestConfiguration.class);
            case PLANS_V3 -> new SpringApplication(EnvironmentStudioApplication.class,V3DocumentHttpTestConfiguration.class);
            case PROFILES_V3 -> new SpringApplication(EnvironmentStudioApplication.class,V3WorkflowHttpTestConfiguration.class);
        };var environment=new org.springframework.core.env.StandardEnvironment();environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("independent-browser-test",properties));app.setEnvironment(environment);app.setAdditionalProfiles("oidc-test");var context=app.run();owned.add(context::close);
        var control=HttpServer.create(new InetSocketAddress("127.0.0.1",controlPort),0);
        owned.add(()->control.stop(0));
        control.createContext("/control",exchange->{
            String path=exchange.getRequestURI().getPath();int status=200;
            if(path.equals("/control/operator"))issuer.subject="browser-operator";
            else if(path.equals("/control/maintainer"))issuer.subject="browser-maintainer";
            else if(mode==Mode.LEGACY && path.equals("/control/expire"))PlanHttpTestConfiguration.clock.advance(1800);
            else if(mode==Mode.LEGACY && path.equals("/control/reset-clock")){PlanHttpTestConfiguration.clock.advance(28801);context.getBean(studio.environment.server.session.HostedSessions.class).expire();PlanHttpTestConfiguration.clock.reset();PlanHttpTestConfiguration.connections.set(0);PlanHttpTestConfiguration.exactCredentials.set(false);}
            else if(mode==Mode.PLANS_V3 && path.equals("/control/settled")) {
                try { V3DocumentHttpTestConfiguration.awaitRecords(0); }
                catch(InterruptedException failure) { Thread.currentThread().interrupt();status=500; }
                catch(Exception | AssertionError failure) { status=500; }
            }
            else if(mode==Mode.PROFILES_V3 && path.equals("/control/settled")) {
                try { V3WorkflowHttpTestConfiguration.awaitRecords(0); }
                catch(InterruptedException failure) { Thread.currentThread().interrupt();status=500; }
                catch(Exception | AssertionError failure) { status=500; }
            }
            else if(path.equals("/control/checks")){
                var canaries=new ArrayList<>(List.of("Db-Password-Canary","Browser-Source-Canary","mock-platform-secret","mock-access-canary",password));canaries.addAll(issuer.issuedCodes);canaries.addAll(issuer.receivedVerifiers);canaries.addAll(issuer.issuedTokens);
                if(mode==Mode.PLANS_V3)canaries.addAll(List.of("MockV3-Password-𐀀","MOCK-DOC-SECRET"));
                if(mode==Mode.PLANS_V3 && (!V3DocumentHttpTestConfiguration.exactCredentials.get() || V3DocumentHttpTestConfiguration.observations.get()!=1))status=500;
                if(mode==Mode.PROFILES_V3) {
                    canaries.addAll(List.of("MockV3Reader","MockV3-Password","MOCK-DOC-SECRET"));
                    if(!V3WorkflowHttpTestConfiguration.exactCredentials.get() || V3WorkflowHttpTestConfiguration.observations.get()!=1)status=500;
                    try(var files=Files.list(store)) {
                        for(var file:files.filter(Files::isRegularFile).toList()) {
                            String stored=Files.readString(file,StandardCharsets.ISO_8859_1);
                            if(canaries.stream().anyMatch(stored::contains))status=500;
                        }
                    }
                }
                if(!issuer.verifiedPkce || (mode==Mode.LEGACY && (!PlanHttpTestConfiguration.exactCredentials.get() || PlanHttpTestConfiguration.connections.get()!=1)) || canaries.stream().anyMatch(captured::contains))status=500;
            }else status=404;
            byte[] body=(status==200?"MOCK_CHECK_OK":"MOCK_CHECK_FAILED").getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();
        });control.start();
        original.println("MOCK_HTTPS_BROWSER_READY");
        }catch(Throwable failure){owned.close();throw failure;}
    }
}
