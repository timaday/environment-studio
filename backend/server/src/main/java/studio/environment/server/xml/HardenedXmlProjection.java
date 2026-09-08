package studio.environment.server.xml;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.Locator2;
import org.xml.sax.helpers.DefaultHandler;
import static studio.environment.server.xml.XmlDocument.*;

final class HardenedXmlProjection {
    private static final Set<String> UNSUPPORTED_NAMESPACES = Set.of("http://www.w3.org/2001/XInclude",
            "http://www.w3.org/2000/09/xmldsig#", "http://www.w3.org/2009/xmldsig11#",
            "http://www.w3.org/2001/04/xmlenc#", "http://www.w3.org/2009/xmlenc11#");
    HardenedXmlProjection() { reader(); }

    XmlDocument project(String source) {
        var lexical = new XmlLexicalScanner(source).scan();
        String digest = digest(source);
        var handler = new ProjectionHandler(digest, lexical);
        XMLReader reader = reader();
        reader.setContentHandler(handler); reader.setErrorHandler(handler);
        reader.setEntityResolver((publicId, systemId) -> { throw new XmlRefusal("UNSUPPORTED_XML"); });
        try {
            reader.parse(new InputSource(new StringReader(source)));
            if (handler.elements.size() != lexical.size()) throw new XmlRefusal("INVALID_XML");
            return new XmlDocument(source, digest, handler.elements);
        } catch (SAXException | IOException exception) { throw new XmlRefusal("INVALID_XML"); }
    }
    private static XMLReader reader() {
        try {
            SAXParserFactory factory = SAXParserFactory.newDefaultInstance();
            factory.setNamespaceAware(true); factory.setValidating(false); factory.setXIncludeAware(false);
            if (factory.isXIncludeAware()) throw new IllegalStateException("Required XML hardening is unavailable.");
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            feature(reader, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            feature(reader, "http://apache.org/xml/features/disallow-doctype-decl", true);
            feature(reader, "http://xml.org/sax/features/external-general-entities", false);
            feature(reader, "http://xml.org/sax/features/external-parameter-entities", false);
            feature(reader, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            feature(reader, "http://xml.org/sax/features/namespaces", true);
            feature(reader, "http://xml.org/sax/features/namespace-prefixes", true);
            feature(reader, "http://xml.org/sax/features/xmlns-uris", true);
            property(reader, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            property(reader, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            property(reader, "http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "128");
            property(reader, "http://www.oracle.com/xml/jaxp/properties/elementAttributeLimit", "256");
            property(reader, "http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit", "100000");
            property(reader, "http://www.oracle.com/xml/jaxp/properties/maxXMLNameLimit", "1048576");
            return reader;
        } catch (ParserConfigurationException | SAXException | UnsupportedOperationException exception) {
            throw new IllegalStateException("Required XML hardening is unavailable.");
        }
    }
    private static void feature(XMLReader reader, String name, boolean value) throws SAXException {
        reader.setFeature(name, value);
        if (reader.getFeature(name) != value) throw new IllegalStateException("Required XML hardening is unavailable.");
    }
    private static void property(XMLReader reader, String name, String value) throws SAXException {
        reader.setProperty(name, value);
        if (!String.valueOf(reader.getProperty(name)).equals(value))
            throw new IllegalStateException("Required XML hardening is unavailable.");
    }
    private static String digest(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("Required source digest is unavailable."); }
    }
    private static final class ProjectionHandler extends DefaultHandler {
        private final String digest;
        private final List<XmlLexicalScanner.Element> lexical;
        private final List<ElementRef> elements = new ArrayList<>();
        private Locator locator;
        ProjectionHandler(String digest, List<XmlLexicalScanner.Element> lexical) { this.digest = digest; this.lexical = lexical; }
        @Override public void setDocumentLocator(Locator locator) { this.locator = locator; }
        @Override public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (!(locator instanceof Locator2 version) || !"1.0".equals(version.getXMLVersion()))
                throw new XmlRefusal("UNSUPPORTED_XML");
            if (UNSUPPORTED_NAMESPACES.contains(uri)) throw new XmlRefusal("UNSUPPORTED_XML");
            if (elements.size() >= lexical.size()) throw new XmlRefusal("INVALID_XML");
            var element = lexical.get(elements.size());
            if (!element.qualifiedName.equals(qName) || element.attributes.size() != attributes.getLength())
                throw new XmlRefusal("INVALID_XML");
            List<AttributeRef> projectedAttributes = new ArrayList<>();
            for (var attribute : element.attributes) {
                int index = attributes.getIndex(attribute.qualifiedName());
                if (index < 0) throw new XmlRefusal("INVALID_XML");
                projectedAttributes.add(new AttributeRef(digest, element.index,
                        new ExpandedName(attributes.getURI(index), attributes.getLocalName(index)), attribute.qualifiedName(),
                        attributes.getValue(index), attribute.valueSpan(), attribute.quote()));
            }
            elements.add(new ElementRef(digest, element.index, new ExpandedName(uri, localName), qName, element.ancestry,
                    new Span(element.start, element.end), new Span(element.start, element.startTagEnd), element.endTagStart,
                    element.selfClosing, projectedAttributes));
        }
        @Override public void warning(SAXParseException exception) { throw new XmlRefusal("INVALID_XML"); }
        @Override public void error(SAXParseException exception) { throw new XmlRefusal("INVALID_XML"); }
        @Override public void fatalError(SAXParseException exception) { throw new XmlRefusal("INVALID_XML"); }
    }
}
