package studio.environment.core;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Computes a proposal; application code must present dependencies/conflicts before applying reuse. */
public final class DependencyClosure {
    private DependencyClosure() { }

    public static List<String> of(Set<String> selected, Map<String, Set<String>> dependencies) {
        Objects.requireNonNull(selected);
        Objects.requireNonNull(dependencies);
        var pending = new ArrayDeque<>(new TreeSet<>(selected));
        var visited = new TreeSet<String>();
        while (!pending.isEmpty()) {
            var entity = pending.removeFirst();
            if (!dependencies.containsKey(entity)) {
                throw new IllegalArgumentException("MISSING_PROFILE_DEPENDENCY");
            }
            if (visited.add(entity)) {
                pending.addAll(new TreeSet<>(Objects.requireNonNull(dependencies.get(entity))));
            }
        }
        return List.copyOf(visited);
    }
}
