package studio.environment.supervisor;

import java.nio.file.Path;
import java.util.*;
import com.networknt.schema.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import studio.environment.server.export.PackageData;

record Configuration(Configuration.Pinned sessionLauncher,Configuration.Pinned terminalControl,List<Configuration.Client> clients,List<Configuration.Destination> destinations) {
    record Pinned(Path path,String sha256) { @Override public String toString(){return "Pinned[redacted]";} }
    record Client(String family,String version,Path root,String identity,String executableSha256,Optional<String> orapkiSha256) { @Override public String toString(){return "Client[redacted]";} }
    record Destination(PackageData.Engine engine,PackageData.Destination binding,Pinned trust) { @Override public String toString(){return "Destination[redacted]";} }
    Configuration { clients=List.copyOf(clients);destinations=List.copyOf(destinations); }
    private static final Schema SHAPE=shape();
    private static Schema shape(){try(var in=Configuration.class.getResourceAsStream("/schemas/guarded-supervisor-config-v1.schema.json")){if(in==null)throw new Refusal("SCHEMA_UNAVAILABLE");return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7,b->b.schemaLoader(l->l.fetchRemoteResources(false).block(iri->true))).getSchema(JsonMapper.builder().build().readTree(in));}catch(Exception e){throw new Refusal("SCHEMA_UNAVAILABLE");}}
    static boolean accepts(byte[] bytes){try{parse(bytes);return true;}catch(Refusal e){return false;}}
    static Configuration parse(byte[] bytes) {
        JsonNode n=ClosedJson.read(bytes,1_048_576,8,10_000);Refusal.require(SHAPE.validate(n).isEmpty(),"INVALID_CONFIGURATION");
        List<Client> clients=new ArrayList<>();Set<String> families=new HashSet<>();
        for(var c:n.get("clients")){String family=text(c,"family");Refusal.require(families.add(family),"DUPLICATE_CLIENT");clients.add(new Client(family,text(c,"version"),Invocation.path(text(c,"runtimeRoot")),text(c,"runtimeIdentity"),text(c,"executableSha256"),c.has("orapkiSha256")?Optional.of(text(c,"orapkiSha256")):Optional.empty()));}
        List<Destination> destinations=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(var d:n.get("destinations")){
            String id=text(d,"id"),host=text(d,"host");Refusal.require(ids.add(id),"DUPLICATE_DESTINATION");host(host);
            var engine=PackageData.Engine.valueOf(text(d,"engine").toUpperCase(Locale.ROOT));Refusal.require(families.contains(engine==PackageData.Engine.POSTGRESQL?"psql":"sqlplus"),"CLIENT_MISSING");
            var i=d.get("expectedPhysicalIdentity");i.properties().forEach(e->{String value=e.getValue().asString();Refusal.require(value.codePoints().noneMatch(Character::isISOControl),"INVALID_IDENTITY");});
            PackageData.PhysicalIdentity physical=engine==PackageData.Engine.POSTGRESQL?new PackageData.PhysicalIdentity.Postgres(text(i,"systemIdentifier"),text(i,"databaseOid"),text(i,"databaseName")):new PackageData.PhysicalIdentity.Oracle(text(i,"dbid"),text(i,"dbUniqueName"),text(i,"conId"),text(i,"conUid"),text(i,"conName"),text(i,"pdbGuid"));
            destinations.add(new Destination(engine,new PackageData.Destination(id,host,d.get("port").intValue(),text(d,"database"),"verified-tls",text(d,"transportIdentity"),text(d,"provisioningPolicyVersion"),physical),pinned(d.get("trustMaterial"))));
        }
        return new Configuration(pinned(n.get("sessionLauncher")),pinned(n.get("terminalControl")),clients,destinations);
    }
    private static void host(String host){if(host.matches("[0-9.]+")){String[] pieces=host.split("\\.",-1);Refusal.require(pieces.length==4,"INVALID_HOST");for(String piece:pieces)Refusal.require(piece.matches("0|[1-9][0-9]{0,2}")&&Integer.parseInt(piece)<=255,"INVALID_HOST");}else for(String label:host.split("\\.",-1))Refusal.require(label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"),"INVALID_HOST");}
    private static Pinned pinned(JsonNode n){return new Pinned(Invocation.path(text(n,"path")),text(n,"sha256"));}
    private static String text(JsonNode n,String field){return n.get(field).asString();}
    @Override public String toString(){return "Configuration[redacted]";}
}
