package studio.environment.core.definitionv3;

/** Fixed compatibility limits, not uploaded settings or a runtime qualification assertion. */
public final class DerivedSemantics {
    public static final int VERSION = 1;
    public static final int MAX_DERIVATIONS = 32;
    public static final int MAX_COOCCURRENCES = 32;
    public static final int MAX_TOTAL_NODES = 20_000;
    public static final int MAX_TOTAL_EDGES = 50_000;
    public static final int MAX_CONTRIBUTOR_LINKS = 100_000;
    public static final int MAX_IDENTITY_UTF8_BYTES = 8_388_608;
    private DerivedSemantics() { }
}
