package studio.environment.server.workspace;

import java.util.*;
import java.util.function.Predicate;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import studio.environment.core.session.Owner;

/** Deployment-owned exact OIDC identities. Absent means nobody may publish definitions. */
final class DefinitionPublishers implements Predicate<Owner> {
    private final Set<Owner> owners;
    DefinitionPublishers(Environment environment) {
        try {
            var entries=Binder.get(environment).bind("studio.workspace.definition-publishers",Bindable.listOf(Publisher.class),new org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler(org.springframework.boot.context.properties.bind.BindHandler.DEFAULT)).orElse(List.of());
            if(entries.size()>64)throw new IllegalArgumentException();
            var allowed=new HashSet<Owner>();
            for(var entry:entries) {
                if(entry.issuer()==null||entry.subject()==null||entry.issuer().isBlank()||entry.subject().isBlank()
                    ||entry.issuer().length()>4096||entry.subject().length()>4096)throw new IllegalArgumentException();
                StrictUtf8.encode(entry.issuer());StrictUtf8.encode(entry.subject());
                if(!allowed.add(new Owner(entry.issuer(),entry.subject())))throw new IllegalArgumentException();
            }owners=Set.copyOf(allowed);
        }catch(RuntimeException invalid){throw new IllegalStateException("INVALID_DEFINITION_PUBLISHERS");}
    }
    record Publisher(String issuer,String subject) { @Override public String toString(){return "Publisher[redacted]";} }
    @Override public boolean test(Owner owner){return owners.contains(owner);}
}
