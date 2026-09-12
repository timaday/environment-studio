package studio.environment.server.plan;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.*;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.core.env.Environment;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.session.Owner;
import studio.environment.server.observation.ObservationDestination;

/** Trusted immutable configuration. Certificate/mount policy is independent of observed database evidence. */
final class PlanDestinations {
    record Display(String id,String engine,String host,int port,String database) { }
    record Configured(ObservationDestination destination,Set<Owner> owners,ExportClient exportClient) {
        Configured { owners=Set.copyOf(owners); }
        @Override public String toString() { return "ConfiguredDestination[redacted]"; }
    }
    record Entry(String id,String engine,String host,Integer port,String database,String trustMaterial,String transportIdentity,
            Map<String,String> expectedPhysicalIdentity,String provisioningPolicyVersion,String operationPolicyVersion,ExportClient exportClient,List<Identity> owners) {
        @Override public String toString() { return "DestinationEntry[redacted]"; }
    }
    record ExportClient(String serverVersion,String family,String version,String platform,String templateVersion) {
        @Override public String toString() { return "ExportClient[redacted]"; }
    }
    record Identity(String issuer,String subject) { @Override public String toString() { return "DestinationOwner[redacted]"; } }
    private final List<Configured> entries;
    PlanDestinations(Environment environment) {
        try {
            var bound=Binder.get(environment).bind("studio.plans.destinations",Bindable.listOf(Entry.class),new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElse(List.of());
            if(bound.size()>32) invalid();
            var configured=new ArrayList<Configured>(); var ids=new HashSet<String>();
            for(var entry:bound) {
                if(entry.id()==null || !entry.id().matches("[a-z][a-z0-9.-]{0,63}") || !ids.add(entry.id())) invalid();
                Engine engine=switch(entry.engine()) { case "postgresql" -> Engine.POSTGRESQL; case "oracle" -> Engine.ORACLE; default -> throw new IllegalArgumentException(); };
                if(entry.transportIdentity()==null || !entry.transportIdentity().matches("[a-f0-9]{64}")
                        || entry.provisioningPolicyVersion()==null || !entry.provisioningPolicyVersion().matches("[A-Za-z0-9.\\-:_]{1,256}")) invalid();
                String policy=entry.operationPolicyVersion();
                if(policy==null || !(engine==Engine.POSTGRESQL?policy.equals("postgresql-read-operation-v1"):policy.equals("oracle-read-operation-v1"))) invalid();
                physicalIdentity(engine,entry.expectedPhysicalIdentity());
                if(entry.exportClient()!=null) exportClient(engine,entry.exportClient());
                trust(engine,entry.trustMaterial());
                if(entry.owners()==null || entry.owners().isEmpty() || entry.owners().size()>64) invalid();
                var owners=new HashSet<Owner>();
                for(var identity:entry.owners()) {
                    scalar(identity.issuer(),2048); scalar(identity.subject(),1024);
                    URI issuer=URI.create(identity.issuer());
                    if(!"https".equals(issuer.getScheme()) || issuer.getHost()==null || issuer.getUserInfo()!=null || issuer.getRawQuery()!=null || issuer.getRawFragment()!=null
                            || !owners.add(new Owner(identity.issuer(),identity.subject()))) invalid();
                }
                configured.add(new Configured(new ObservationDestination(entry.id(),engine,entry.host(),entry.port(),entry.database(),ObservationDestination.Transport.VERIFIED_TLS,
                        entry.trustMaterial(),entry.transportIdentity(),entry.expectedPhysicalIdentity(),entry.provisioningPolicyVersion(),policy),owners,entry.exportClient()));
            }
            entries=List.copyOf(configured);
        } catch(Exception failure) { throw new IllegalStateException("INVALID_PLAN_DESTINATIONS"); }
    }
    List<Configured> configured() { return entries; }
    List<Display> visible(Owner owner) {
        return entries.stream().filter(entry->entry.owners().contains(owner)).map(entry->{
            var destination=entry.destination();
            return new Display(destination.id(),destination.engine().name().toLowerCase(Locale.ROOT),destination.host(),destination.port(),destination.database());
        }).toList();
    }
    boolean allows(Owner owner,String id) { return entries.stream().anyMatch(entry->entry.destination().id().equals(id) && entry.owners().contains(owner)); }
    private static void exportClient(Engine engine,ExportClient client) {
        if(client.serverVersion()==null || client.family()==null || client.version()==null || client.platform()==null || client.templateVersion()==null) invalid();
        boolean postgresql16=engine==Engine.POSTGRESQL && client.serverVersion().equals("16.11") && client.family().equals("psql")
                && client.version().equals("16.11") && client.platform().equals("linux-amd64") && client.templateVersion().equals("postgresql16-text-v1");
        boolean postgresql18=engine==Engine.POSTGRESQL && client.serverVersion().equals("18.6") && client.family().equals("psql")
                && client.version().equals("18.6") && client.platform().equals("linux-amd64") && client.templateVersion().equals("postgresql-text-v1");
        boolean oracle=engine==Engine.ORACLE && client.serverVersion().equals("23.26.3.0.0") && client.family().equals("sqlplus")
                && client.version().equals("23.26.3.0.0") && client.platform().equals("linux-amd64") && client.templateVersion().equals("oracle-clob-v1");
        if(!(postgresql16 || postgresql18 || oracle)) invalid();
    }
    private static void physicalIdentity(Engine engine,Map<String,String> identity) {
        var numeric=engine==Engine.POSTGRESQL?Set.of("systemIdentifier","databaseOid"):Set.of("dbid","conId","conUid");
        var names=engine==Engine.POSTGRESQL?Set.of("databaseName"):Set.of("dbUniqueName","conName");
        var keys=new HashSet<>(numeric); keys.addAll(names); if(engine==Engine.ORACLE) keys.add("pdbGuid");
        if(identity==null || !identity.keySet().equals(keys)) invalid();
        numeric.forEach(key->{if(identity.get(key)==null || !identity.get(key).matches("[1-9][0-9]{0,19}")) invalid();});
        names.forEach(key->scalar(identity.get(key),128));
        if(engine==Engine.ORACLE && (identity.get("pdbGuid")==null || !identity.get("pdbGuid").matches("[a-f0-9]{32}"))) invalid();
    }
    private static void scalar(String value,int maximum) {
        if(value==null || value.isBlank() || value.codePointCount(0,value.length())>maximum) invalid();
        for(int i=0;i<value.length();) {
            int cp=value.codePointAt(i);
            if(Character.isISOControl(cp) || cp>=0xd800 && cp<=0xdfff) invalid();
            i+=Character.charCount(cp);
        }
    }
    private static void trust(Engine engine,String location) throws Exception {
        Path path=Path.of(location);
        if(!path.isAbsolute() || !path.equals(path.normalize()) || !Files.isReadable(path) || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) invalid();
        for(Path component=path;component!=null;component=component.getParent()) if(Files.isSymbolicLink(component)) invalid();
        byte[] bytes;
        try(var stream=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) { bytes=stream.readNBytes(1_048_577); }
        if(bytes.length==0 || bytes.length>1_048_576) invalid();
        if(engine==Engine.POSTGRESQL) {
            String pem=new String(bytes,StandardCharsets.US_ASCII);
            String remainder=pem.replaceAll("-----BEGIN CERTIFICATE-----[A-Za-z0-9+/=\\r\\n]+-----END CERTIFICATE-----","");
            if(!remainder.isBlank() || !remainder.chars().allMatch(c->c==' ' || c=='\n' || c=='\r' || c=='\t')) invalid();
            var certificates=CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(bytes));
            if(certificates.isEmpty()) invalid();
        } else {
            String filename=path.getFileName().toString();
            String format=filename.endsWith(".jks")?"JKS":filename.endsWith(".p12") || filename.endsWith(".pfx")?"PKCS12":null;
            if(format==null) invalid();
            boolean jks=bytes.length>=4 && bytes[0]==(byte)0xfe && bytes[1]==(byte)0xed && bytes[2]==(byte)0xfe && bytes[3]==(byte)0xed;
            if(format.equals("JKS")!=jks) invalid();
            var store=KeyStore.getInstance(format);
            store.load(new ByteArrayInputStream(bytes),null);
            if(store.size()==0) invalid();
            for(var names=store.aliases();names.hasMoreElements();) {
                String alias=names.nextElement();
                if(store.isKeyEntry(alias) || !store.isCertificateEntry(alias) || store.getCertificate(alias)==null) invalid();
            }
        }
    }
    private static void invalid() { throw new IllegalArgumentException(); }
}
