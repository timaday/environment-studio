package studio.environment.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyClosureTest {
    @Test void partialReuseIncludesDependenciesButNotUnrelatedSiblings() {
        var graph = Map.of("work", Set.of("node"), "node", Set.of("type"),
                "type", Set.<String>of(), "unselected", Set.of("node"));
        assertEquals(List.of("node", "type", "work"), DependencyClosure.of(Set.of("work"), graph));
    }

    @Test void cyclesTerminateAndSharedDependenciesAppearOnce() {
        var graph = Map.of("a", Set.of("b"), "b", Set.of("a"), "c", Set.of("b"));
        assertEquals(List.of("a", "b", "c"), DependencyClosure.of(Set.of("c"), graph));
    }

    @Test void missingRootOrRequiredDependencyIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> DependencyClosure.of(Set.of("unknown"), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> DependencyClosure.of(Set.of("a"), Map.of("a", Set.of("missing"))));
    }

    @Test void aNewSelectionDoesNotRetainOldClosure() {
        var graph = Map.of("a", Set.of("shared"), "b", Set.<String>of(), "shared", Set.<String>of());
        DependencyClosure.of(Set.of("a"), graph);
        assertEquals(List.of("b"), DependencyClosure.of(Set.of("b"), graph));
        assertEquals(List.of(), DependencyClosure.of(Set.of(), graph));
    }
}
