package studio.environment.core.definitionv2;

import java.util.Set;

/** Element-vocabulary limits shared by static publication checks and the qualified XML adapter. */
public final class NativeXmlCapabilities {
    private static final Set<String> UNSUPPORTED_ELEMENT_NAMESPACES = Set.of(
        "http://www.w3.org/2001/XInclude", "http://www.w3.org/2000/09/xmldsig#",
        "http://www.w3.org/2009/xmldsig11#", "http://www.w3.org/2001/04/xmlenc#",
        "http://www.w3.org/2009/xmlenc11#");
    private NativeXmlCapabilities() { }
    public static boolean supportsElementNamespace(String namespace) {
        return namespace != null && !UNSUPPORTED_ELEMENT_NAMESPACES.contains(namespace);
    }
}
