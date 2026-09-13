package studio.environment.core.definitionv3;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import studio.environment.core.definitionv2.NativeDefinition.Binding;
import studio.environment.core.definitionv2.NativeDefinition.ChildProperty;

/** Required compatibility vector only. No v3 mechanism is advertised as available. */
public final class NativeMechanisms {
    private static final Map<String, BigInteger> BASE = Map.of("native-compiler-v3", BigInteger.ONE,
            "xml-path-v1", BigInteger.ONE, "xml-span-v1", BigInteger.ONE,
            "generic-graph-v1", BigInteger.ONE, "derived-graph-v1", BigInteger.ONE);
    private NativeMechanisms() { }
    public static Map<String, BigInteger> required(Binding binding) {
        var result = new TreeMap<>(BASE);
        if (binding.documents().stream().flatMap(d -> d.entities().stream()).flatMap(p -> p.fields().stream())
                .anyMatch(f -> f.locator() instanceof ChildProperty)) result.put("xml-child-property-v1", BigInteger.ONE);
        return Collections.unmodifiableMap(result);
    }
    public static Map<String, BigInteger> required(NativeDefinition definition) {
        var result = new TreeMap<>(BASE);
        definition.bindings().forEach(binding -> result.putAll(required(binding)));
        return Collections.unmodifiableMap(result);
    }
}
