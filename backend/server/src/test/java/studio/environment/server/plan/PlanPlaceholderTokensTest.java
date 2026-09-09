package studio.environment.server.plan;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import static studio.environment.server.plan.PlanBindingLocationsTest.*;
class PlanPlaceholderTokensTest {
    final PlanBindingLocationsTest fixture=new PlanBindingLocationsTest();
    static final String ALPHA_TOKEN="[[value:00000000-0000-4000-8000-000000000011:";
    static final String BETA_TOKEN="[[value:00000000-0000-4000-8000-000000000012:";
    static final String PALETTE_TOKEN="[[value:"+PALETTE_HANDLE+":";
    @Test void exactPlaceholderDocumentsUseSameProvenanceTokensAcrossIdentityRename() throws Exception {
        var view=fixture.snapshot(rename());
        String expected="<?xml version=\"1.0\"?>\r\n<t:tiles xmlns:t=\"urn:mock:tiles\">\r\n  <!-- repeated shared values must remain exact -->\r\n  <t:glyph id=\""+ALPHA_TOKEN+"tag]]\" tone=\""+ALPHA_TOKEN+"tone]]\" palette=\""+PALETTE_TOKEN+"tag]]\"/>\r\n  <?mock keep?>\r\n  <t:glyph id=\""+BETA_TOKEN+"tag]]\" tone=\""+BETA_TOKEN+"tone]]\" palette=\""+PALETTE_TOKEN+"tag]]\"/>\r\n  <t:unused><![CDATA[<glyph palette=\"shared\"/>]]></t:unused>\r\n</t:tiles>\r\n";
        for(boolean target:List.of(false,true)) {
            var glyphs=fixture.fixture.adapter.compare(view,target,"glyph-sheet",ViewMode.PLACEHOLDERS);assertEquals(expected,glyphs.text());assertFalse(glyphs.exact());assertTrue(glyphs.redacted());assertTrue(glyphs.unmappedConcreteMayRemain());
            assertEquals("<tiles xmlns='urn:mock:tiles'>\n  <palette id='"+PALETTE_TOKEN+"tag]]' shade='"+PALETTE_TOKEN+"shade]]'/>\n</tiles>\n",fixture.fixture.adapter.compare(view,target,"palette-sheet",ViewMode.PLACEHOLDERS).text());
            var raw=fixture.fixture.adapter.compare(view,target,"glyph-sheet",ViewMode.RAW);assertEquals(source(view.selected(target),"glyph-sheet").xml(),raw.text());
        }
    }
    @Test void missingIdentityAndIncompleteSideCannotRenderSuccessfulPlaceholders() throws Exception {
        var view=fixture.snapshot(Draft.empty());var handles=new HashMap<>(view.displayHandles());handles.remove(PALETTE);
        var invalid=new HostedPlanService.ViewSnapshot(view.revision(),view.definition(),view.binding(),view.current(),view.target(),view.draft(),view.references(),handles);
        assertThrows(PlanRefusal.class,()->fixture.fixture.adapter.compare(invalid,false,"glyph-sheet",ViewMode.PLACEHOLDERS));
        var incomplete=new HostedPlanService.ViewSnapshot(view.revision(),view.definition(),view.binding(),view.current(),Optional.empty(),view.draft(),view.references(),view.displayHandles());
        assertEquals(PlanRefusal.Code.INCOMPLETE_TARGET,assertThrows(PlanRefusal.class,()->fixture.fixture.adapter.compare(incomplete,true,"glyph-sheet",ViewMode.PLACEHOLDERS)).code());
    }
}
