package studio.environment.server.planning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.xml.LosslessXmlAdapter;
import studio.environment.server.xml.XmlDocument;
import studio.environment.server.xml.XmlEdit;
import studio.environment.server.xml.XmlResult;
import static studio.environment.server.planning.PlanningXml.fail;

/** Tracks tool-owned element provenance independently of changed source offsets. */
final class XmlAssembly {
    sealed interface Symbol {
        record Original(String document, int index) implements Symbol { @Override public String toString() { return "OriginalPosition[redacted]"; } }
        record Created(TargetIntent.Ref.Fresh entity) implements Symbol { @Override public String toString() { return "CreatedPosition[redacted]"; } }
    }
    record Fragment(XmlDocument document, List<Symbol> symbols) {
        Fragment { if (symbols.size() > 20_000 || symbols.size() != document.elements().size()) fail("ELEMENT_PROVENANCE_MISMATCH"); symbols = List.copyOf(symbols); }
        @Override public String toString() { return "Fragment[redacted]"; }
    }
    record Edit(XmlEdit edit, Optional<Fragment> insertion) {
        @Override public String toString() { return "PlannedXmlEdit[redacted]"; }
        static Edit simple(XmlEdit edit) { return new Edit(edit, Optional.empty()); }
        static Edit insert(XmlDocument.ElementRef parent, Fragment fragment) { return new Edit(new XmlEdit.InsertElement(parent, Optional.empty(), fragment.document().source()), Optional.of(fragment)); }
    }
    static final class Edits {
        private final List<Edit> values = new ArrayList<>();
        private long insertedCharacters;
        void add(Edit edit) {
            long added = edit.insertion().map(f -> (long)f.document().source().length()).orElse(0L);
            if (values.size() == 100_000 || insertedCharacters + added > PlanningXml.MAX_CHARS) fail("RESOURCE_LIMIT");
            insertedCharacters += added; values.add(edit);
        }
        List<Edit> values() { return List.copyOf(values); }
    }
    private record Insertion(int position, Fragment fragment) { }
    private final LosslessXmlAdapter xml = new LosslessXmlAdapter();
    Fragment original(String document, XmlDocument source) {
        List<Symbol> symbols = new ArrayList<>(); source.elements().forEach(e -> symbols.add(new Symbol.Original(document, e.index()))); return new Fragment(source, symbols);
    }
    Fragment apply(Fragment source, List<Edit> edits) {
        if (edits.size() > 100_000) fail("RESOURCE_LIMIT");
        XmlDocument target = parsed(xml.apply(source.document(), source.document().digest(), edits.stream().map(Edit::edit).toList()));
        Set<Integer> removed = new HashSet<>(); List<Insertion> insertions = new ArrayList<>();
        for (var edit : edits) {
            if (edit.edit() instanceof XmlEdit.RemoveElement removal) removed.add(removal.element().index());
            if (edit.edit() instanceof XmlEdit.InsertElement insertion) {
                if (edit.insertion().isEmpty()) fail("ELEMENT_PROVENANCE_MISMATCH");
                insertions.add(new Insertion(insertion.before().map(e -> e.span().start()).orElse(insertion.parent().endTagStart()), edit.insertion().get()));
            }
        }
        insertions.sort(Comparator.comparingInt(Insertion::position));
        List<Symbol> symbols = new ArrayList<>(); int insertion = 0;
        for (var element : source.document().elements()) {
            while (insertion < insertions.size() && insertions.get(insertion).position() <= element.span().start()) append(symbols, insertions.get(insertion++).fragment().symbols());
            if (!removed.contains(element.index()) && element.ancestry().stream().noneMatch(removed::contains)) { if (symbols.size() == 20_000) fail("RESOURCE_LIMIT"); symbols.add(source.symbols().get(element.index())); }
        }
        while (insertion < insertions.size()) append(symbols, insertions.get(insertion++).fragment().symbols());
        return new Fragment(target, symbols);
    }
    private static void append(List<Symbol> symbols, List<Symbol> inserted) { if ((long)symbols.size() + inserted.size() > 20_000) fail("RESOURCE_LIMIT"); symbols.addAll(inserted); }
    static XmlDocument parsed(XmlResult result) { if (result instanceof XmlResult.Rejected refused) fail(refused.diagnostics().getFirst().code()); return ((XmlResult.Accepted)result).document(); }
}
