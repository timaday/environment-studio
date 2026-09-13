package studio.environment.core.profile;

import java.math.BigInteger;
import java.util.List;

/** Closed portable structure. No observed identity, literal values or source references. */
public record Profile(String id, BigInteger revision, String logicalDefinitionDigest, List<Entity> entities, List<Relation> relations) {
    public static final int MAX_ENTITIES = 20_000;
    public static final int MAX_RELATIONS = 50_000;
    public Profile { bound(entities.size(), MAX_ENTITIES); bound(relations.size(), MAX_RELATIONS);
        entities = List.copyOf(entities); relations = List.copyOf(relations); }
    public record Entity(String id, String type, String label, List<String> requiredInputs) {
        public Entity { bound(requiredInputs.size(), 20_000); requiredInputs = List.copyOf(requiredInputs); }
        @Override public String toString() { return "ProfileEntity[redacted]"; }
    }
    public record Relation(String type, String from, String to) { }
    @Override public String toString() { return "Profile[redacted]"; }
    static void bound(int size, int maximum) { if (size > maximum) throw new IllegalArgumentException("Profile collection exceeds its bound."); }
}
