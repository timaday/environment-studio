package studio.environment.core.workspace;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Predicate;
import studio.environment.core.session.Owner;
import studio.environment.core.definitionv2.NativeCompilationResult;

/** Publication decisions are independent of HTTP, parser and storage frameworks. */
public final class NativeWorkspace {
    public interface Compiler {
        NativeRevision.Definition definition(NativeCommand.SaveDefinition command);
        NativeRevision.Profile profile(NativeCommand.SaveProfile command, NativeCompilationResult.ReadyToPublish definition);
    }
    private final NativeStore store;
    private final Compiler compiler;
    private final Predicate<Owner> publishers;
    public NativeWorkspace(NativeStore store, Compiler compiler, Predicate<Owner> publishers) {
        this.store=Objects.requireNonNull(store);this.compiler=compiler;this.publishers=Objects.requireNonNull(publishers);
    }
    public NativeRevision mutate(Owner owner, NativeCommand command) {
        if(command instanceof NativeCommand.PublishDefinition && !publishers.test(owner)) throw new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN);
        var replay=store.replay(owner,command);if(replay.isPresent()) return replay.orElseThrow();
        NativeRevision result=switch(command) {
            case NativeCommand.SaveDefinition c -> draft(c, c.format(), c.source(), compiler.definition(c));
            case NativeCommand.SaveProfile c -> {
                var definition=publishedDefinition(owner,c.definition());
                yield draft(c,c.format(),c.source(),compiler.profile(c,new NativeCompilationResult.ReadyToPublish(definition.checked())));
            }
            case NativeCommand.PublishDefinition c -> {
                var current=store.read(owner,c.objectId(),Optional.empty(),false);
                eligibleDraft(current);var definition=(NativeRevision.Definition)current.content();
                eligibleDefinition(definition);validatePolicies(definition,c.exportPolicies());
                yield published(current,c.exportPolicies());
            }
            case NativeCommand.PublishProfile c -> {
                var current=store.read(owner,c.objectId(),Optional.empty(),true);eligibleDraft(current);
                var profile=(NativeRevision.Profile)current.content();
                var definition=publishedDefinition(owner,profile.definition());eligibleDefinition(definition);
                if(!profile.checked().profile().logicalDefinitionDigest().equals(definition.checked().logicalDigest())) throw WorkspaceRejection.publication("INCOMPATIBLE_DEFINITION");
                yield published(current,List.of());
            }
        };
        return store.append(owner,command,result);
    }
    private NativeRevision.Definition publishedDefinition(Owner owner,NativeCommand.Reference reference) {
        var revision=store.read(owner,reference.objectId(),Optional.of(reference.workspaceRevision()),false);
        if(revision.publication().isEmpty()) throw WorkspaceRejection.publication("DEFINITION_NOT_PUBLISHED");
        var definition=(NativeRevision.Definition)revision.content();
        if(!definition.ready()) throw WorkspaceRejection.publication("DEFINITION_INCOMPLETE");
        return definition;
    }
    private static NativeRevision draft(NativeCommand command,DraftCommand.Format format,String source,NativeRevision.Content content) {
        return new NativeRevision(command.objectId(),new BigInteger(command.expectedRevision()).add(BigInteger.ONE).toString(),format,source,NativeWorkspaceDigests.source(source),
            command.profile()?"profile-compiler-v2":"native-compiler-v2","2",content,Optional.empty());
    }
    private static NativeRevision published(NativeRevision current,List<NativeCommand.Policy> policies) {
        var next=new NativeRevision(current.objectId(),new BigInteger(current.workspaceRevision()).add(BigInteger.ONE).toString(),current.format(),current.source(),current.sourceDigest(),
            current.compilerVersion(),current.schemaVersion(),current.content(),Optional.empty());
        return new NativeRevision(next.objectId(),next.workspaceRevision(),next.format(),next.source(),next.sourceDigest(),next.compilerVersion(),next.schemaVersion(),next.content(),
            Optional.of(new NativeRevision.Publication(NativeWorkspaceDigests.publication(next,current.workspaceRevision(),policies),current.workspaceRevision(),policies)));
    }
    private static void eligibleDraft(NativeRevision current) {
        if(current.publication().isPresent()) throw new WorkspaceRefusal(WorkspaceRefusal.Code.CONFLICT);
        if(!"2".equals(current.schemaVersion()) || !(current.profile()?"profile-compiler-v2":"native-compiler-v2").equals(current.compilerVersion())) throw WorkspaceRejection.publication("UNSUPPORTED_COMPILER");
    }
    public static void eligibleDefinition(NativeRevision.Definition definition) {
        if(!definition.ready()) throw WorkspaceRejection.publication("DEFINITION_INCOMPLETE");
        if(!definition.checked().mechanisms().equals(Map.of("native-compiler-v2",BigInteger.ONE,"xml-path-v1",BigInteger.ONE,"xml-span-v1",BigInteger.ONE,"generic-graph-v1",BigInteger.ONE)))
            throw WorkspaceRejection.publication("UNSUPPORTED_MECHANISM");
    }
    public static void validatePolicies(NativeRevision.Definition definition,List<NativeCommand.Policy> policies) {
        var expected=new HashSet<List<String>>();definition.checked().definition().bindings().forEach(b->b.documents().forEach(d->expected.add(List.of(b.id(),d.id()))));
        var actual=new HashSet<List<String>>();
        for(var policy:policies) if(!actual.add(List.of(policy.bindingId(),policy.documentId()))) throw WorkspaceRejection.publication("DUPLICATE_DOCUMENT_POLICY");
        if(!actual.equals(expected)) throw WorkspaceRejection.publication("INCOMPLETE_DOCUMENT_POLICY");
    }
}
