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
}
