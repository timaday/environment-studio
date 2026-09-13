package studio.environment.server.projection;

import java.util.*;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.server.xml.XmlDocument;

/** One parsed source, one operation. Selection never grants publication or write authority. */
public final class FieldLocatorResolver {
    public sealed interface Result permits Located, Absent, Refused { }
    public record Selector(XmlDocument.ElementRef child, XmlDocument.AttributeRef discriminator) {
        @Override public String toString(){return "Selector[redacted]";}
    }
    public record Located(XmlDocument.ElementRef entity, XmlDocument.AttributeRef attribute, Optional<Selector> selector) implements Result {
        @Override public String toString(){return "LocatedField[redacted]";}
    }
    public record Absent() implements Result { }
    public record Refused(String code) implements Result { }
    private record Selection(int parent,NativeDefinition.ExpandedName element,NativeDefinition.ExpandedName discriminator,String value) { }
    private record Slot(int element,XmlDocument.ExpandedName name) { }
    private final XmlDocument document;
    private final Map<Integer,Map<XmlDocument.ExpandedName,XmlDocument.AttributeRef>> attributes=new HashMap<>();
    private final Map<Integer,Map<XmlDocument.ExpandedName,List<XmlDocument.ElementRef>>> children=new HashMap<>();
    private final Map<Selection,List<Selector>> selections=new HashMap<>();
    private final Map<List<XmlDocument.ExpandedName>,List<XmlDocument.ElementRef>> paths=new HashMap<>();
    public FieldLocatorResolver(XmlDocument document) {
        this.document=Objects.requireNonNull(document);
        for(var element:document.elements()) {
            var attrs=new HashMap<XmlDocument.ExpandedName,XmlDocument.AttributeRef>();
            element.attributes().forEach(a->attrs.put(a.name(),a));attributes.put(element.index(),attrs);
            if(!element.ancestry().isEmpty())children.computeIfAbsent(element.ancestry().getLast(),k->new HashMap<>()).computeIfAbsent(element.name(),k->new ArrayList<>()).add(element);
            var path=new ArrayList<XmlDocument.ExpandedName>();element.ancestry().forEach(i->path.add(document.elements().get(i).name()));path.add(element.name());
            paths.computeIfAbsent(List.copyOf(path),k->new ArrayList<>()).add(element);
        }
    }
    public List<XmlDocument.ElementRef> elements(NativeDefinition.Projection projection) {
        return List.copyOf(paths.getOrDefault(projection.path().stream().map(FieldLocatorResolver::name).toList(),List.of()));
    }
    public Result resolve(XmlDocument.ElementRef entity,NativeDefinition.FieldLocator locator) {
        if(!owned(entity) || locator==null)return new Refused("STALE_SOURCE");
        return switch(locator) {
            case NativeDefinition.DirectAttribute direct -> direct(entity,direct.attribute());
            case NativeDefinition.ChildProperty child -> {
                var key=new Selection(entity.index(),child.element(),child.discriminatorAttribute(),child.discriminatorValue());
                var matches=selections.computeIfAbsent(key,k->{
                    var result=new ArrayList<Selector>();
                    for(var candidate:children.getOrDefault(entity.index(),Map.of()).getOrDefault(name(child.element()),List.of())) {
                        var discriminator=attributes.get(candidate.index()).get(name(child.discriminatorAttribute()));
                        if(discriminator!=null && discriminator.value().equals(child.discriminatorValue())) {
                            result.add(new Selector(candidate,discriminator));if(result.size()==2)break;
                        }
                    }
                    return List.copyOf(result);
                });
                if(matches.size()>1)yield new Refused("AMBIGUOUS_CHILD_PROPERTY");
                if(matches.isEmpty())yield new Absent();
                var selected=matches.getFirst();var attribute=attributes.get(selected.child().index()).get(name(child.valueAttribute()));
                yield attribute==null?new Absent():new Located(entity,attribute,Optional.of(selected));
            }
        };
    }
    public Result direct(XmlDocument.ElementRef entity,NativeDefinition.ExpandedName name) {
        if(!owned(entity) || name==null)return new Refused("STALE_SOURCE");
        var attribute=attributes.get(entity.index()).get(name(name));
        return attribute==null?new Absent():new Located(entity,attribute,Optional.empty());
    }
    /** Complete declaration scan, including protected discriminators on nonmatching children. */
    public Optional<Refused> validate(NativeDefinition.Document declaration) {
        var protectedSlots=new HashSet<Slot>();var claimed=new HashSet<Slot>();
        for(var projection:declaration.entities())for(var entity:elements(projection))for(var mapping:projection.fields())
            if(mapping.locator() instanceof NativeDefinition.ChildProperty child)
                for(var candidate:children.getOrDefault(entity.index(),Map.of()).getOrDefault(name(child.element()),List.of()))
                    protectedSlots.add(new Slot(candidate.index(),name(child.discriminatorAttribute())));
        for(var projection:declaration.entities())for(var entity:elements(projection)) {
            for(var mapping:projection.fields()) {
                var result=resolve(entity,mapping.locator());var failure=claim(result,claimed,protectedSlots);if(failure.isPresent())return failure;
            }
            for(var mapping:projection.references()) {
                var failure=claim(direct(entity,mapping.attribute()),claimed,protectedSlots);if(failure.isPresent())return failure;
            }
        }
        return Optional.empty();
    }
    private Optional<Refused> claim(Result result,Set<Slot> claimed,Set<Slot> protectedSlots) {
        if(result instanceof Refused refused)return Optional.of(refused);
        if(result instanceof Located located) {
            var attribute=located.attribute();var slot=new Slot(attribute.elementIndex(),attribute.name());
            if(protectedSlots.contains(slot))return Optional.of(new Refused("SELECTOR_ATTRIBUTE_CONFLICT"));
            if(!claimed.add(slot))return Optional.of(new Refused("ATTRIBUTE_ALIAS"));
        }
        return Optional.empty();
    }
    private boolean owned(XmlDocument.ElementRef element) {
        return element!=null && element.index()>=0 && element.index()<document.elements().size()
            && element.sourceDigest().equals(document.digest()) && document.elements().get(element.index()).equals(element);
    }
    private static XmlDocument.ExpandedName name(NativeDefinition.ExpandedName name){return new XmlDocument.ExpandedName(name.namespaceUri(),name.localName());}
}
