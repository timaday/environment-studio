package studio.environment.server.xml;

/** Internal control flow deliberately contains no uploaded text or parser exception. */
final class XmlRefusal extends RuntimeException {
    private final String code;
    XmlRefusal(String code) { super(null, null, false, false); this.code = code; }
    String code() { return code; }
}
