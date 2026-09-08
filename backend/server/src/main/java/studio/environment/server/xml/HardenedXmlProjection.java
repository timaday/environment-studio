package studio.environment.server.xml;

import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.stax.WstxInputFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.codehaus.stax2.XMLInputFactory2;
import static studio.environment.server.xml.XmlDocument.*;

final class HardenedXmlProjection {
    private static final Set<String> UNSUPPORTED_NAMESPACES = Set.of("http://www.w3.org/2001/XInclude",
            "http://www.w3.org/2000/09/xmldsig#", "http://www.w3.org/2009/xmldsig11#",
            "http://www.w3.org/2001/04/xmlenc#", "http://www.w3.org/2009/xmlenc11#");
    HardenedXmlProjection() { factory(); }

    XmlDocument project(String source) {
        // Mandatory: the char-level name scanner alone does not reject unpaired surrogates.
        var lexical = new XmlLexicalScanner(source).scan();
        String digest = digest(source);
        List<ElementRef> elements = new ArrayList<>();
        XMLStreamReader reader = null;
        try {
            reader = factory().createXMLStreamReader(new StringReader(source));
            if (reader.getVersion() != null && !reader.getVersion().equals("1.0")) throw new XmlRefusal("UNSUPPORTED_XML");
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD) throw new XmlRefusal("UNSUPPORTED_XML");
                if (event == XMLStreamConstants.START_ELEMENT) projectElement(reader, digest, lexical, elements);
            }
            if (elements.size() != lexical.size()) throw new XmlRefusal("INVALID_XML");
            return new XmlDocument(source, digest, elements);
        } catch (XMLStreamException exception) { throw new XmlRefusal("INVALID_XML"); }
        finally {
            if (reader != null) try { reader.close(); }
            catch (XMLStreamException exception) { throw new XmlRefusal("INVALID_XML"); }
        }
    }
    static WstxInputFactory factory() { return configure(new WstxInputFactory()); }
    static WstxInputFactory configure(WstxInputFactory factory) {
        try {
            property(factory, XMLInputFactory.IS_NAMESPACE_AWARE, true);
            property(factory, XMLInputFactory.SUPPORT_DTD, false);
            property(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            property(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
            property(factory, XMLInputFactory.IS_VALIDATING, false);
            property(factory, XMLInputFactory2.P_LAZY_PARSING, false);
            property(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            property(factory, WstxInputProperties.P_MAX_ELEMENT_DEPTH, 128);
            property(factory, WstxInputProperties.P_MAX_ATTRIBUTES_PER_ELEMENT, 256);
            property(factory, WstxInputProperties.P_MAX_ATTRIBUTE_SIZE, XmlLexicalScanner.MAX_CHARS);
            property(factory, WstxInputProperties.P_MAX_ELEMENT_COUNT, 20_000L);
            property(factory, WstxInputProperties.P_MAX_CHARACTERS, 1_048_576L);
            factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> { throw new XMLStreamException("External XML resolution is unavailable."); });
            return factory;
        } catch (IllegalArgumentException exception) { throw new IllegalStateException("Required XML hardening is unavailable."); }
    }
    private static void property(WstxInputFactory factory, String name, Object value) {
        factory.setProperty(name, value);
        if (!value.equals(factory.getProperty(name))) throw new IllegalStateException("Required XML hardening is unavailable.");
    }
    private record ParsedAttribute(ExpandedName name, String value) {
        @Override public String toString() { return "ParsedAttribute[redacted]"; }
    }
    private static void projectElement(XMLStreamReader reader, String digest, List<XmlLexicalScanner.Element> lexical,
            List<ElementRef> elements) {
        String uri = empty(reader.getNamespaceURI());
        if (UNSUPPORTED_NAMESPACES.contains(uri)) throw new XmlRefusal("UNSUPPORTED_XML");
        if (elements.size() >= lexical.size()) throw new XmlRefusal("INVALID_XML");
        var element = lexical.get(elements.size());
        String qualifiedName = qualified(reader.getPrefix(), reader.getLocalName());
        if (!element.qualifiedName.equals(qualifiedName)) throw new XmlRefusal("INVALID_XML");
        Map<String, ParsedAttribute> attributes = new HashMap<>();
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String name = qualified(reader.getAttributePrefix(i), reader.getAttributeLocalName(i));
            if (attributes.put(name, new ParsedAttribute(new ExpandedName(empty(reader.getAttributeNamespace(i)), reader.getAttributeLocalName(i)),
                    reader.getAttributeValue(i))) != null) throw new XmlRefusal("INVALID_XML");
        }
        for (int i = 0; i < reader.getNamespaceCount(); i++) {
            String prefix = empty(reader.getNamespacePrefix(i));
            String name = prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
            if (attributes.put(name, new ParsedAttribute(new ExpandedName(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix.isEmpty() ? "xmlns" : prefix),
                    empty(reader.getNamespaceURI(i)))) != null) throw new XmlRefusal("INVALID_XML");
        }
        // Woodstox validates explicit xmlns:xml but omits the fixed binding from its
        // namespace declaration iterator. Preserve its lexical reference using the
        // parser's independently checked namespace context, never raw source decoding.
        if (element.attributes.stream().anyMatch(attribute -> attribute.qualifiedName().equals("xmlns:xml")))
            attributes.putIfAbsent("xmlns:xml", new ParsedAttribute(new ExpandedName(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xml"),
                    reader.getNamespaceURI("xml")));
        if (element.attributes.size() != attributes.size()) throw new XmlRefusal("INVALID_XML");
        List<AttributeRef> projectedAttributes = new ArrayList<>();
        for (var attribute : element.attributes) {
            ParsedAttribute parsed = attributes.get(attribute.qualifiedName());
            if (parsed == null) throw new XmlRefusal("INVALID_XML");
            projectedAttributes.add(new AttributeRef(digest, element.index, parsed.name(), attribute.qualifiedName(), parsed.value(), attribute.valueSpan(), attribute.quote()));
        }
        elements.add(new ElementRef(digest, element.index, new ExpandedName(uri, reader.getLocalName()), qualifiedName, element.ancestry,
                new Span(element.start, element.end), new Span(element.start, element.startTagEnd), element.endTagStart,
                element.selfClosing, projectedAttributes));
    }
    private static String qualified(String prefix, String local) { return prefix == null || prefix.isEmpty() ? local : prefix + ":" + local; }
    private static String empty(String value) { return value == null ? "" : value; }
    private static String digest(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("Required source digest is unavailable."); }
    }
}
