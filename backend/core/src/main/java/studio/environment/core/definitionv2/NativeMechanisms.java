package studio.environment.core.definitionv2;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import static studio.environment.core.definitionv2.NativeDefinition.*;

/** Server-owned dependency contracts; uploaded declarations never assert availability. */
public final class NativeMechanisms {
    public static final String CHILD_PROPERTY = "xml-child-property-v1";
    private static final Map<String, BigInteger> BASE = Map.of("native-compiler-v2", BigInteger.TWO,
            "xml-path-v1", BigInteger.ONE, "xml-span-v1", BigInteger.ONE, "generic-graph-v1", BigInteger.ONE);
    private static final Map<String, BigInteger> CHILD = childDependencies();
    private NativeMechanisms() { }
    private static Map<String, BigInteger> childDependencies() {
        var result = new TreeMap<>(BASE);
        result.put(CHILD_PROPERTY, BigInteger.ONE);
        return Collections.unmodifiableMap(result);
    }
    public static Map<String, BigInteger> required(Binding binding) {
        var result = new TreeMap<>(BASE);
        if (binding.documents().stream().flatMap(d -> d.entities().stream()).flatMap(p -> p.fields().stream())
                .anyMatch(f -> f.locator() instanceof ChildProperty)) result.put(CHILD_PROPERTY, BigInteger.ONE);
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
    public static boolean qualified(Map<String, BigInteger> dependencies) {
        return dependencies.equals(BASE) || dependencies.equals(CHILD);
    }
    public static boolean eligible(NativeCompilationResult.Checked checked) {
        return matchesDependencies(checked) && qualified(checked.mechanisms());
    }
}
