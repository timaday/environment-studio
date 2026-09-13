package studio.environment.core.definitionv3;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import studio.environment.core.definitionv2.NativeDefinition.Binding;
import studio.environment.core.definitionv2.NativeDefinition.ChildProperty;

/** Required compatibility vector and explicitly qualified v3 publication subsets. */
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
    public static boolean matchesDependencies(NativeCompilationResult.Checked checked) {
        return checked != null && required(checked.definition()).equals(checked.mechanisms());
    }
    public static boolean qualified(NativeDefinition definition) {
        return definition.bindings().stream().allMatch(binding -> binding.engine() == studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL
                && binding.storage() == studio.environment.core.definitionv2.NativeDefinition.Storage.TEXT)
                && (required(definition).equals(BASE) || required(definition).equals(childDependencies()));
    }
    public static boolean eligible(NativeCompilationResult.Checked checked) {
        return matchesDependencies(checked) && qualified(checked.definition());
    }
    private static Map<String, BigInteger> childDependencies() {
        var result = new TreeMap<>(BASE);
        result.put("xml-child-property-v1", BigInteger.ONE);
        return Collections.unmodifiableMap(result);
    }
}
