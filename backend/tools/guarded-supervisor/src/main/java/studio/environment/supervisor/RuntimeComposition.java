package studio.environment.supervisor;

import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import studio.environment.server.export.PackageData;

/** Production composition for the explicitly qualified PostgreSQL 16.11 pilot tuple. */
final class RuntimeComposition {
    private RuntimeComposition() { }
    static final String POSTGRES16_RUNTIME_IDENTITY="91fcf6c68561cc51668392419ae792e2fa3347c407d0ed74b80aa9b2494c7242";
    static final Admission.Registry REGISTRY=(configuration,client,destination)->
        System.getProperty("os.name").equals("Linux")
        && Set.of("amd64","x86_64").contains(System.getProperty("os.arch"))
        && destination.engine()==PackageData.Engine.POSTGRESQL
        && destination.binding().transport().equals("verified-tls")
        && client.family().equals("psql")
        && client.version().equals("16.11")
        && client.identity().equals(POSTGRES16_RUNTIME_IDENTITY);

    static Supervisor.ConsolePort console(String[] args) {
        return new Supervisor.ConsolePort() {
            private TerminalConsole delegate;
            @Override public Credentials read(boolean postgres) {
                Refusal.require(postgres,"RUNTIME_UNQUALIFIED");
                var invocation=Invocation.parse(args);
                var configuration=Configuration.parse(AdmittedFiles.read(invocation.configuration(),1_048_576));
                delegate=TerminalConsole.verified(configuration.terminalControl().path(),configuration.terminalControl().sha256());
                return delegate.read(true);
            }
            @Override public SessionEngine.Cleanup restore() { return delegate==null?SessionEngine.Cleanup.COMPLETE:delegate.restore(); }
        };
    }

    static Supervisor.RuntimePort runtime() {
        return (admitted,credentials)-> {
            var destination=admitted.destination();var client=admitted.client();
            Refusal.require(destination.engine()==PackageData.Engine.POSTGRESQL&&client.family().equals("psql"),"RUNTIME_UNQUALIFIED");
            var work=ownedDirectory();var root=work.resolve("root.crt");
            try {Files.write(root,admitted.trust().bytes(),StandardOpenOption.CREATE_NEW);Files.setPosixFilePermissions(root,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}
            catch(Exception failure){cleanup(work);throw new Refusal("TRUST_RUNTIME_UNAVAILABLE");}
            var child=new OwnedNativeProcess(admitted.configuration().sessionLauncher().path(),psqlCommand(client,destination,credentials),psqlEnvironment(root),work);
            return new ManagedWire(new ClientProtocol(child,admitted.archive(),nonce(),credentials.username(),credentials.password(),""),work);
        };
    }

    static List<String> psqlCommand(Configuration.Client client,Configuration.Destination destination,Credentials credentials) {
        return List.of(client.root().resolve("bin/psql").toString(),"-X","-W","-A","-t","-q","-v","ON_ERROR_STOP=on","-v","ON_ERROR_ROLLBACK=off","-P","pager=off","-h",destination.binding().host(),"-p",Integer.toString(destination.binding().port()),"-U",credentials.username(),"-d",destination.binding().database());
    }
    static Map<String,String> psqlEnvironment(Path rootCertificate) {
        return Map.of("LANG","C.UTF-8","LC_ALL","C.UTF-8","PGSSLMODE","verify-full","PGSSLROOTCERT",rootCertificate.toString(),"PGCONNECT_TIMEOUT","10","PGSERVICEFILE","/dev/null","PSQL_HISTORY","/dev/null");
    }
    private static String nonce(){byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);return HexFormat.of().formatHex(bytes);}
    private static Path ownedDirectory(){try{var dir=Files.createTempDirectory("environment-studio-guarded-");Files.setPosixFilePermissions(dir,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));return dir;}catch(Exception e){throw new Refusal("RUNTIME_SCRATCH_UNAVAILABLE");}}
    private static void cleanup(Path dir){try(var stream=Files.list(dir)){for(Path item:stream.toList())Files.deleteIfExists(item);}catch(Throwable ignored){/* cleanup remains best effort */}try{Files.deleteIfExists(dir);}catch(Throwable ignored){/* cleanup remains best effort */}}
    private record ManagedWire(SessionEngine.Wire delegate,Path work) implements SessionEngine.Wire {
        public void authenticate(){delegate.authenticate();} public void bootstrap(){delegate.bootstrap();} public void program(){delegate.program();} public void readiness(){delegate.readiness();} public void commit(){delegate.commit();} public void acknowledgement(){delegate.acknowledgement();} public void cleanExit(){delegate.cleanExit();}
        public SessionEngine.Cleanup rollbackAndCleanup(){try{return delegate.rollbackAndCleanup();}finally{cleanup(work);}}
        public void terminate(){try{delegate.terminate();}finally{cleanup(work);}}
        public void close(){try{delegate.close();}finally{cleanup(work);}}
    }
}
