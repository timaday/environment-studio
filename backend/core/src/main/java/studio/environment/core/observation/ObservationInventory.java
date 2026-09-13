package studio.environment.core.observation;

import java.util.*;
import studio.environment.core.definitionv2.NativeDefinition.Binding;

public final class ObservationInventory {
    private ObservationInventory() { }
    public static boolean complete(Binding binding, List<ObservationResult.Document> documents) {
        if (documents.size() != binding.documents().size() || documents.size() > 128) return false;
        var expected = new HashMap<String, String>();
        for (var document : binding.documents()) if (expected.put(document.id(), document.key()) != null) return false;
        var keys = new HashSet<ObservationResult.Key>();
        for (var document : documents) {
            if (!document.key().type().equals(binding.keyType().name().toLowerCase(Locale.ROOT))
                    || !keys.add(document.key()) || !document.key().value().equals(expected.remove(document.documentId()))) return false;
        }
        return expected.isEmpty();
    }
}
