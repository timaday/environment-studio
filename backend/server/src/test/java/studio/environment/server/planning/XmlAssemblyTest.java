package studio.environment.server.planning;

import java.util.List;
import org.junit.jupiter.api.Test;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.xml.LosslessXmlAdapter;
import studio.environment.server.xml.XmlEdit;
import studio.environment.server.xml.XmlResult;
import static org.junit.jupiter.api.Assertions.*;

class XmlAssemblyTest {
    @Test void cumulativeInsertionBudgetRejectsBeforeRetainingAnOversizedBatch() {
        var document = assertInstanceOf(XmlResult.Accepted.class, new LosslessXmlAdapter().project("<x>" + "a".repeat(600_000) + "</x>")).document();
        var fragment = new XmlAssembly.Fragment(document, List.of(new XmlAssembly.Symbol.Created(new TargetIntent.Ref.Fresh("slot", "mock"))));
        var edits = new XmlAssembly.Edits(); var insertion = XmlAssembly.Edit.insert(document.elements().getFirst(), fragment);
        edits.add(insertion);
        assertEquals("RESOURCE_LIMIT", assertThrows(PlanningXml.Refusal.class, () -> edits.add(insertion)).code);
        assertEquals(1, edits.values().size());
    }
    @Test void editCountRejectsAtCollectionTime() {
        var document = assertInstanceOf(XmlResult.Accepted.class, new LosslessXmlAdapter().project("<x><y/></x>")).document();
        var edit = XmlAssembly.Edit.simple(new XmlEdit.RemoveElement(document.elements().get(1))); var edits = new XmlAssembly.Edits();
        for (int i = 0; i < 100_000; i++) edits.add(edit);
        assertEquals("RESOURCE_LIMIT", assertThrows(PlanningXml.Refusal.class, () -> edits.add(edit)).code);
        assertEquals(100_000, edits.values().size());
    }
}
