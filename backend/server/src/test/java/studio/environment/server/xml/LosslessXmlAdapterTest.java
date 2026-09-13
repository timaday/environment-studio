package studio.environment.server.xml;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.xml.XmlEdit.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** XML and exact expected outputs invented independently for generic lexical mechanisms. */
class LosslessXmlAdapterTest {
    private final LosslessXmlAdapter adapter = new LosslessXmlAdapter();
    private XmlDocument project(String source) {
        return assertInstanceOf(XmlResult.Accepted.class, adapter.project(source)).document();
    }
    private XmlDocument apply(XmlDocument source, XmlEdit... edits) {
        return assertInstanceOf(XmlResult.Accepted.class, adapter.apply(source, source.digest(), List.of(edits))).document();
    }
    private void rejected(XmlResult result, String code) {
        var refusal = assertInstanceOf(XmlResult.Rejected.class, result);
        assertEquals(code, refusal.diagnostics().getFirst().code());
    }
    @Test void exactNoOpRetainsCommentsCdataPiCrLfEntitiesAndAstralCharacters() {
        String xml = "<?xml version='1.0'?>\r\n<?mock <angle>?>\r\n<o:orb xmlns:o='urn:mock' tone=\"&#65;&amp;\">"
                + "<!-- <fake a='>'/> --><![CDATA[<raw>😀]]><o:arc tone='A&amp;'/></o:orb>\r\n";
        var source = project(xml);
        assertEquals(xml, apply(source).source());
        assertEquals("A&", source.elements().getFirst().attributes().get(1).value());
        assertEquals("urn:mock", source.elements().get(1).name().namespaceUri());
        assertEquals(List.of(0), source.elements().get(1).ancestry());
        assertEquals(source.digest(), project(xml).digest());
        assertThrows(UnsupportedOperationException.class, () -> source.elements().clear());
        assertThrows(UnsupportedOperationException.class, () -> source.elements().get(1).ancestry().clear());
    }
    @Test void attributeReplacementPreservesOtherEqualValuesQuotesWhitespaceAndEntities() {
        var source = project("<orb tone = 'same' other=\"same\"><arc tone='same'/></orb>");
        var target = apply(source, new ReplaceAttribute(source.elements().getFirst().attributes().getFirst(), "same", "A'&<\r\n\t😀"));
        assertEquals("<orb tone = 'A&apos;&amp;&lt;&#13;&#10;&#9;😀' other=\"same\"><arc tone='same'/></orb>", target.source());
        assertEquals("A'&<\r\n\t😀", target.elements().getFirst().attributes().getFirst().value());
    }
    @Test void removalAndOrderedInsertionPreserveUntouchedCharacters() {
        var source = project("<orb>\r\n <arc id='one'/><!--keep--> <arc id='two'/>\r\n</orb>");
        var target = apply(source, new RemoveElement(source.elements().get(1)),
                new InsertElement(source.elements().getFirst(), Optional.of(source.elements().get(2)), "<new-a/>"),
                new InsertElement(source.elements().getFirst(), Optional.of(source.elements().get(2)), "<new-b/>"));
        assertEquals("<orb>\r\n <!--keep--> <new-a/><new-b/><arc id='two'/>\r\n</orb>", target.source());
    }
    @Test void insertsIntoSelfClosingParentWithQualifiedDelimiterExpansion() {
        var source = project("<orb><arc tone='stay' /></orb>");
        var parent = source.elements().get(1);
        assertEquals("<orb><arc tone='stay' ><new-a/><new-b/></arc></orb>", apply(source,
                new InsertElement(parent, Optional.empty(), "<new-a/>"),
                new InsertElement(parent, Optional.empty(), "<new-b/>")).source());
    }
    @ParameterizedTest @ValueSource(strings = {"<", "<a><b></a>", "<!DOCTYPE a [<!ENTITY e 'private-canary'>]><a>&e;</a>",
            "<a xmlns:i='http://www.w3.org/2001/XInclude'><i:include href='private-canary'/></a>",
            "<s:Signature xmlns:s='http://www.w3.org/2000/09/xmldsig#'/>",
            "<e:EncryptedData xmlns:e='http://www.w3.org/2001/04/xmlenc#'/>", "<?xml version='1.1'?><a/>"})
    void rejectsUnsupportedOrMalformedXmlWithoutSourceLeak(String xml) {
        var result = assertInstanceOf(XmlResult.Rejected.class, adapter.project(xml));
        assertFalse(result.toString().contains("private-canary"));
    }
    @Test void staleWrongExpectedOverlappingRootRemovalAndNamespaceChangesRejectAtomically() {
        var source = project("<orb><arc tone='old'><dot/></arc></orb>");
        var arc = source.elements().get(1);
        rejected(adapter.apply(source, "stale", List.of()), "STALE_SOURCE");
        rejected(adapter.apply(source, source.digest(), List.of(new ReplaceAttribute(arc.attributes().getFirst(), "wrong", "new"))), "EXPECTED_VALUE_MISMATCH");
        rejected(adapter.apply(source, source.digest(), List.of(new RemoveElement(arc), new RemoveElement(source.elements().get(2)))), "CONFLICTING_EDITS");
        rejected(adapter.apply(source, source.digest(), List.of(new RemoveElement(source.elements().getFirst()))), "ROOT_REMOVAL");
        var namespaced = project("<orb xmlns='urn:mock'/>");
        rejected(adapter.apply(namespaced, namespaced.digest(), List.of(new InsertElement(namespaced.elements().getFirst(), Optional.empty(), "<arc/>"))), "INSERTION_NAMESPACE_MISMATCH");
    }
    @Test void sourceBoundsAreExactAndNoParserDefaultNarrowsTheContract() {
        project("<a>" + "x".repeat(1048576 - 7) + "</a>");
        rejected(adapter.project("<a>" + "x".repeat(1048576 - 6) + "</a>"), "RESOURCE_LIMIT");
        project("<a>".repeat(128) + "</a>".repeat(128));
        rejected(adapter.project("<a>".repeat(129) + "</a>".repeat(129)), "RESOURCE_LIMIT");
        project("<a>" + "<b/>".repeat(19999) + "</a>");
        rejected(adapter.project("<a>" + "<b/>".repeat(20000) + "</a>"), "RESOURCE_LIMIT");
        StringBuilder attributes = new StringBuilder();
        for (int i = 0; i < 256; i++) attributes.append(" a").append(i).append("='x'");
        project("<a" + attributes + "/>");
        rejected(adapter.project("<a" + attributes + " last='x'/>"), "RESOURCE_LIMIT");
        project("<a>" + "<!---->".repeat(99998) + "</a>");
        rejected(adapter.project("<a>" + "<!---->".repeat(99999) + "</a>"), "RESOURCE_LIMIT");
        project("<" + "a".repeat(1001) + "/>");
    }
    @Test void invalidUnicodeAndUndeclaredEntitiesNeverProduceATarget() {
        for (String invalid : List.of("<a>" + (char) 0 + "</a>", "<a>" + (char) 0xd800 + "</a>",
                "<a>" + (char) 0xdc00 + "</a>", "<a>&undefined;</a>", "<a>&#0;</a>", "<a x='1'x='2'/>", "<a/><b/>"))
            assertInstanceOf(XmlResult.Rejected.class, adapter.project(invalid));
        var source = project("<a x='old'/>");
        rejected(adapter.apply(source, source.digest(), List.of(new ReplaceAttribute(source.elements().getFirst().attributes().getFirst(),
                "old", "new" + (char) 0))), "INVALID_XML");
    }
    @Test void namespaceAliasesAndExplicitDefaultResetKeepInsertionMeaning() {
        var source = project("<p:orb xmlns:p='urn:mock' xmlns='urn:parent'><p:arc/></p:orb>");
        var target = apply(source, new InsertElement(source.elements().getFirst(), Optional.empty(),
                "<q:arc xmlns:q='urn:mock' xmlns=''><dot note='&quot;'/></q:arc>"));
        assertEquals("<p:orb xmlns:p='urn:mock' xmlns='urn:parent'><p:arc/><q:arc xmlns:q='urn:mock' xmlns=''><dot note='&quot;'/></q:arc></p:orb>", target.source());
        assertEquals("", target.elements().get(3).name().namespaceUri());
        rejected(adapter.apply(source, source.digest(), List.of(new InsertElement(source.elements().getFirst(), Optional.empty(),
                "<q:arc xmlns:q='urn:mock'><dot/></q:arc>"))), "INSERTION_NAMESPACE_MISMATCH");
        assertInstanceOf(XmlResult.Rejected.class, adapter.apply(source, source.digest(), List.of(new InsertElement(
                source.elements().getFirst(), Optional.empty(), "<p:arc/>"))));
        rejected(adapter.apply(source, source.digest(), List.of(new ReplaceAttribute(source.elements().getFirst().attributes().getFirst(),
                "urn:mock", "urn:other"))), "NAMESPACE_ATTRIBUTE");
    }
    @Test void foreignForgedMissingAndWrongParentReferencesReject() {
        var source = project("<a><b x='1'><c/></b><d/></a>");
        var foreign = project("<a><b x='2'><c/></b><d/></a>");
        rejected(adapter.apply(source, source.digest(), List.of(new RemoveElement(foreign.elements().get(1)))), "STALE_REFERENCE");
        var attr = source.elements().get(1).attributes().getFirst();
        var forged = new XmlDocument.AttributeRef(attr.sourceDigest(), 999, attr.name(), attr.qualifiedName(), attr.value(), attr.valueSpan(), attr.quote());
        rejected(adapter.apply(source, source.digest(), List.of(new ReplaceAttribute(forged, "1", "2"))), "STALE_REFERENCE");
        rejected(adapter.apply(source, source.digest(), List.of(new InsertElement(source.elements().getFirst(), Optional.of(source.elements().get(2)), "<z/>"))), "INVALID_ANCHOR");
        rejected(adapter.apply(source, source.digest(), List.of(new InsertElement(source.elements().get(1), Optional.of(source.elements().get(3)), "<z/>"))), "INVALID_ANCHOR");
    }
    @Test void allConflictFormsAndLastOperationFailureAreAtomic() {
        var source = project("<a><b x='1'><c/></b><d/></a>");
        var b = source.elements().get(1);
        var replace = new ReplaceAttribute(b.attributes().getFirst(), "1", "2");
        for (List<XmlEdit> edits : List.of(List.<XmlEdit>of(replace, replace),
                List.<XmlEdit>of(new RemoveElement(b), replace),
                List.<XmlEdit>of(new RemoveElement(b), new InsertElement(b, Optional.empty(), "<z/>")),
                List.<XmlEdit>of(new RemoveElement(b), new InsertElement(source.elements().getFirst(), Optional.of(b), "<z/>")),
                List.<XmlEdit>of(new RemoveElement(b), new RemoveElement(b))))
            rejected(adapter.apply(source, source.digest(), edits), "CONFLICTING_EDITS");
        rejected(adapter.apply(source, source.digest(), List.of(replace,
                new ReplaceAttribute(b.attributes().getFirst(), "wrong", "final"))), "EXPECTED_VALUE_MISMATCH");
        assertEquals("<a><b x='1'><c/></b><d/></a>", source.source());
    }
    @Test void fullTargetValidationRefusesCombinedDepthAndSizeOverflows() {
        var source = project("<a>".repeat(128) + "</a>".repeat(128));
        rejected(adapter.apply(source, source.digest(), List.of(new InsertElement(source.elements().getLast(), Optional.empty(), "<b/>"))), "RESOURCE_LIMIT");
        var full = project("<a>" + "x".repeat(1048576 - 7) + "</a>");
        rejected(adapter.apply(full, full.digest(), List.of(new InsertElement(full.elements().getFirst(), Optional.empty(), "<b/>"))), "RESOURCE_LIMIT");
    }
    @Test void twoDocumentBuildingBlocksAndIndependentSemanticOracle() throws Exception {
        var left = project("<orb><arc tone=\"one\"/><arc tone=\"two\"/></orb>");
        var right = project("<orb/>");
        String leftExpected = "<orb><arc tone=\"one\"/></orb>";
        String rightExpected = "<orb><arc tone=\"two\"/><arc tone=\"three&amp;&quot;\"/></orb>";
        assertEquals(leftExpected, apply(left, new RemoveElement(left.elements().get(2))).source());
        var target = apply(right, new InsertElement(right.elements().getFirst(), Optional.empty(), "<arc tone=\"two\"/>"),
                new InsertElement(right.elements().getFirst(), Optional.empty(), "<arc tone=\"three&amp;&quot;\"/>"));
        assertEquals(rightExpected, target.source());
        var factory = javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var parsed = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(target.source())));
        assertEquals("three&\"", ((org.w3c.dom.Element) parsed.getElementsByTagName("arc").item(1)).getAttribute("tone"));
    }
    @Test void doubleQuotedReplacementAndEmptyAttributeRetainExactBoundaries() {
        var source = project("<a blank=\"\" x=\"old\" y='old'/>");
        assertEquals("<a blank=\"&quot;'&amp;&lt;>\" x=\"new\" y='old'/>", apply(source,
                new ReplaceAttribute(source.elements().getFirst().attributes().getFirst(), "", "\"'&<>"),
                new ReplaceAttribute(source.elements().getFirst().attributes().get(1), "old", "new")).source());
    }
    @Test void oversizedEditListIsRefusedBeforeCopyingOrIteration() {
        var source = project("<a x='old'/>");
        List<XmlEdit> virtual = new java.util.AbstractList<>() {
            @Override public int size() { return Integer.MAX_VALUE; }
            @Override public XmlEdit get(int index) {
                throw new AssertionError("An oversized edit list must never be accessed.");
            }
            @Override public Object[] toArray() {
                throw new AssertionError("An oversized edit list must never be copied.");
            }
            @Override public <T> T[] toArray(T[] destination) {
                throw new AssertionError("An oversized edit list must never be copied.");
            }
        };
        rejected(adapter.apply(source, source.digest(), virtual), "RESOURCE_LIMIT");
    }
    @Test void fifthEditionNamesWorkInElementsAttributesPrefixesAndPiAtBothPositions() {
        int cases = 0;
        for (int cp : new int[] {0xC0,0xD6,0xD8,0xF6,0xF8,0x2FF,0x370,0x37D,0x37F,0x1FFF,0x200C,0x200D,
                0x2070,0x218F,0x2C00,0x2FEF,0x3001,0xD7FF,0xF900,0xFDCF,0xFDF0,0xFFFD,0x10000,0x1F600,0xEFFFF}) {
            String name = new String(Character.toChars(cp));
            for (String xml : namePlacements(name)) { assertEquals(xml, project(xml).source()); cases++; }
        }
        assertEquals(200, cases);
        for (int cp : new int[] {0xD7,0xF7,0x37E,0x200B,0x200E,0x206F,0x2190,0x2BFF,0x2FF0,0x3000,0xF8FF,0xFDD0,0xFDEF,0xFFFE,0xFFFF,0xF0000,0x10FFFF}) {
            String name = new String(Character.toChars(cp));
            for (String xml : namePlacements(name)) rejected(adapter.project(xml), "INVALID_XML");
        }
        for (String name : List.of("\ud800", "\udc00", "\ud800a", "\udc00\ud800"))
            for (String xml : namePlacements(name)) rejected(adapter.project(xml), "INVALID_XML");
        for (int cp : new int[] {0xB7,0x300,0x36F,0x203F,0x2040}) {
            String name = new String(Character.toChars(cp));
            project("<a" + name + "/>"); rejected(adapter.project("<" + name + "/>"), "INVALID_XML");
        }
    }
    private static List<String> namePlacements(String name) {
        return List.of("<" + name + "/>", "<r " + name + "='v'/>", "<" + name + ":r xmlns:" + name + "='urn:mock'/>",
                "<?" + name + " data?><r/>", "<r a" + name + "='v'/>", "<a" + name + "/>",
                "<a" + name + ":r xmlns:a" + name + "='urn:mock'/>", "<?a" + name + " data?><r/>");
    }
    @Test void namespaceDeclarationsRetainEstablishedExpandedNamesValuesAndSpans() {
        String xml = "<r xmlns='urn:mock:default' xmlns:p='urn:mock:prefix' xmlns:xml='http://www.w3.org/XML/1998/namespace' xml:space='preserve' p:tag='value'/>";
        var root = project(xml).elements().getFirst();
        assertEquals(5, root.attributes().size());
        assertEquals(new XmlDocument.ExpandedName("http://www.w3.org/2000/xmlns/", "xmlns"), root.attributes().get(0).name());
        assertEquals(new XmlDocument.ExpandedName("http://www.w3.org/2000/xmlns/", "p"), root.attributes().get(1).name());
        assertEquals(new XmlDocument.ExpandedName("http://www.w3.org/2000/xmlns/", "xml"), root.attributes().get(2).name());
        assertEquals(new XmlDocument.ExpandedName("http://www.w3.org/XML/1998/namespace", "space"), root.attributes().get(3).name());
        assertEquals(new XmlDocument.ExpandedName("urn:mock:prefix", "tag"), root.attributes().get(4).name());
        for (var attribute : root.attributes()) assertEquals(attribute.value(), xml.substring(attribute.valueSpan().start(), attribute.valueSpan().end()));
        assertEquals(xml, apply(project(xml)).source());
    }
    @Test void xml10NormalizationAndFifthEditionWriterOutcomesRemainExact() {
        String source = "<Ϳ 豈='x\r\ny\rz\tw&#13;&#10;&#9;\u0085\u2028'>A\r\nB\rC\u0085\u2028</Ϳ>";
        var document = project(source); var attribute = document.elements().getFirst().attributes().getFirst();
        assertEquals("x y z w\r\n\t\u0085\u2028", attribute.value());
        var target = apply(document, new ReplaceAttribute(attribute, attribute.value(), "𐀀\r\n\t\u0085\u2028"));
        assertEquals("<Ϳ 豈='𐀀&#13;&#10;&#9;\u0085\u2028'>A\r\nB\rC\u0085\u2028</Ϳ>", target.source());
        for (String xml : List.of("<r>&#1;</r>", "<r a='&#1;'/>", "<r>&#xFFFE;</r>", "<r>&#xD800;</r>", "<r>&#x110000;</r>", "<r>&#0;</r>"))
            rejected(adapter.project(xml), "INVALID_XML");
        project("<r>" + new String(Character.toChars(0xF0000)) + "</r>");
        // A valid supplementary name crossing the parser input-buffer boundary stays exact.
        String longName = "a".repeat(3999) + "𐀀"; project("<" + longName + "/>");
    }
    @Test void requiredFactorySettingsAreVerifiedAndExternalResolutionNeverRuns() throws Exception {
        var factory = HardenedXmlProjection.factory();
        assertEquals(false, factory.getProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD));
        assertEquals(false, factory.getProperty(javax.xml.stream.XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES));
        assertEquals("", factory.getProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD));
        assertThrows(javax.xml.stream.XMLStreamException.class, () -> factory.getXMLResolver().resolveEntity(null, "file:///mock-canary", null, null));
        var ignoresSetting = new com.ctc.wstx.stax.WstxInputFactory() {
            @Override public Object getProperty(String name) {
                if (name.equals(javax.xml.stream.XMLInputFactory.SUPPORT_DTD)) return true;
                return super.getProperty(name);
            }
        };
        assertThrows(IllegalStateException.class, () -> HardenedXmlProjection.configure(ignoresSetting));
        var unavailable = new com.ctc.wstx.stax.WstxInputFactory() {
            @Override public void setProperty(String name, Object value) { throw new IllegalArgumentException("unavailable-canary"); }
        };
        assertFalse(assertThrows(IllegalStateException.class, () -> HardenedXmlProjection.configure(unavailable)).toString().contains("canary"));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        factory.setXMLResolver((a, b, c, d) -> { calls.incrementAndGet(); throw new javax.xml.stream.XMLStreamException("denied"); });
        for (String xml : List.of("<!DOCTYPE r SYSTEM 'file:///mock-canary'><r/>",
                "<!DOCTYPE r [<!ENTITY x SYSTEM 'http://invalid.invalid/mock'>]><r>&x;</r>")) {
            var reader = factory.createXMLStreamReader(new java.io.StringReader(xml));
            try { while (reader.hasNext()) { if (reader.next() == javax.xml.stream.XMLStreamConstants.DTD) break; } }
            finally { reader.close(); }
        }
        assertEquals(0, calls.get());
        project("<r xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:noNamespaceSchemaLocation='https://invalid.invalid/mock.xsd'/>");
    }

    @Test void qualifiedParserPreservesAllPerDocumentResourceBoundaries() {
        String source = "<a>".repeat(128) + "</a>".repeat(128); project(source);
        rejected(adapter.project("<a>" + source + "</a>"), "RESOURCE_LIMIT");
        source = "<a>" + "x".repeat(1_048_576 - 7) + "</a>"; project(source);
        rejected(adapter.project(source + " "), "RESOURCE_LIMIT");
        var attributes = new StringBuilder("<a");
        for (int i = 0; i < 256; i++) attributes.append(" a").append(i).append("=''");
        project(attributes + "/>"); rejected(adapter.project(attributes + " extra=''/>") , "RESOURCE_LIMIT");
        String elements = "<a>" + "<b/>".repeat(19_999) + "</a>"; project(elements);
        rejected(adapter.project(elements.replace("</a>", "<b/></a>")), "RESOURCE_LIMIT");
        // Opening + closing tags use two tokens; each comment adds one.
        String tokens = "<a>" + "<!---->".repeat(99_998) + "</a>"; project(tokens);
        rejected(adapter.project(tokens.replace("</a>", "<!----></a>")), "RESOURCE_LIMIT");
        rejected(adapter.project("<a xmlns:xml='urn:wrong'/>"), "INVALID_XML");
        rejected(adapter.project("<a xmlns:p='http://www.w3.org/XML/1998/namespace'/>"), "INVALID_XML");
        rejected(adapter.project("<a xmlns:xml='http://www.w3.org/XML/1998/namespace' xmlns:xml='http://www.w3.org/XML/1998/namespace'/>"), "INVALID_XML");
    }

    @Test void longAttributesUseTheApprovedWholeSourceBoundWithoutHiddenParserLimit() {
        for (int length : new int[] {524_288, 524_289, 1_048_576 - 9}) {
            String value = "x".repeat(length); String xml = "<a x='" + value + "'/>";
            var document = project(xml);
            assertEquals(value, document.elements().getFirst().attributes().getFirst().value());
            assertEquals(xml, apply(document).source());
        }
        rejected(adapter.project("<a x='" + "x".repeat(1_048_576 - 8) + "'/>"), "RESOURCE_LIMIT");
    }

}
