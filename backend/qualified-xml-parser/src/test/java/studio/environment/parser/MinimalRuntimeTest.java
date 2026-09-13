package studio.environment.parser;

import com.ctc.wstx.stax.WstxInputFactory;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import org.codehaus.stax2.XMLInputFactory2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinimalRuntimeTest {
    @Test void completeParserRunsWithOnlyStax2AndDoesNotInstallGlobalProviders() throws Exception {
        URL parser = WstxInputFactory.class.getProtectionDomain().getCodeSource().getLocation();
        URL stax = XMLInputFactory2.class.getProtectionDomain().getCodeSource().getLocation();
        assertEquals("7c805f36129ea9fa42b696093b7ae1eb20bb6ccec65c8280d6f33db5609ca5e1",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(stax.toURI())))));
        try (var isolated = new URLClassLoader(new URL[] {parser, stax}, ClassLoader.getPlatformClassLoader())) {
            assertThrows(ClassNotFoundException.class, () -> isolated.loadClass("com.sun.msv.grammar.Expression"));
            assertThrows(ClassNotFoundException.class, () -> isolated.loadClass("org.osgi.framework.BundleActivator"));
            for (String service : new String[] {"javax.xml.stream.XMLInputFactory", "javax.xml.stream.XMLOutputFactory",
                    "javax.xml.stream.XMLEventFactory", "org.codehaus.stax2.validation.XMLValidationSchemaFactory"})
                assertFalse(isolated.getResources("META-INF/services/" + service).hasMoreElements());
            XMLInputFactory factory = (XMLInputFactory) isolated.loadClass("com.ctc.wstx.stax.WstxInputFactory").getConstructor().newInstance();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory2.P_LAZY_PARSING, false);
            var reader = factory.createXMLStreamReader(new StringReader("<Ϳ:𐀀 xmlns:Ϳ='urn:mock' 豈='a&#13;&#10;&#9;'/>") );
            try {
                assertEquals(XMLStreamConstants.START_ELEMENT, reader.next());
                assertEquals("𐀀", reader.getLocalName()); assertEquals("urn:mock", reader.getNamespaceURI());
                assertEquals("a\r\n\t", reader.getAttributeValue(0));
                while (reader.hasNext()) reader.next();
            } finally { reader.close(); }
        }
        assertFalse(XMLInputFactory.newFactory().getClass().getName().startsWith("com.ctc.wstx."));
    }
}
