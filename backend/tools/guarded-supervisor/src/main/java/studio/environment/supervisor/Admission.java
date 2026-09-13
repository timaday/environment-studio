package studio.environment.supervisor;

final class Admission {
    interface Registry { boolean permits(Configuration configuration,Configuration.Client client,Configuration.Destination destination); }
    static final Registry COMPILED_REGISTRY=(configuration,client,destination)->false;
    record Checked(Configuration configuration,Configuration.Client client,Configuration.Destination destination,TrustCertificates trust,PackageCheck.Regenerated archive) { @Override public String toString(){return "Checked[redacted]";} }
    static Checked admit(Invocation invocation,Registry registry) {
        Configuration configuration=Configuration.parse(AdmittedFiles.read(invocation.configuration(),1_048_576));
        var destination=configuration.destinations().stream().filter(d->d.binding().id().equals(invocation.destination())).findFirst().orElseThrow(()->new Refusal("DESTINATION_UNAVAILABLE"));
        String family=destination.engine()==studio.environment.server.export.PackageData.Engine.POSTGRESQL?"psql":"sqlplus";
        var client=configuration.clients().stream().filter(c->c.family().equals(family)).findFirst().orElseThrow(()->new Refusal("CLIENT_MISSING"));
        Refusal.require(registry.permits(configuration,client,destination),"RUNTIME_UNQUALIFIED");
        hash(configuration.sessionLauncher());hash(configuration.terminalControl());hash(new Configuration.Pinned(client.root().resolve("bin").resolve(family),client.executableSha256()));client.orapkiSha256().ifPresent(h->hash(new Configuration.Pinned(client.root().resolve("bin/orapki"),h)));
        var trust=TrustCertificates.read(destination.trust());var archive=PackageCheck.read(AdmittedFiles.read(invocation.archive(),160*1024*1024+438),invocation.digest());
        Refusal.require(destination.engine()==archive.inputs().execution().engine()&&destination.binding().equals(archive.inputs().execution().destination()),"DESTINATION_MISMATCH");
        return new Checked(configuration,client,destination,trust,archive);
    }
    private static void hash(Configuration.Pinned input){Refusal.require(AdmittedFiles.sha256(AdmittedFiles.read(input.path(),128*1024*1024)).equals(input.sha256()),"RUNTIME_HASH_MISMATCH");}
}
