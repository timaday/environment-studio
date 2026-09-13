package studio.environment.server.xml;

import java.util.Objects;
import java.util.Optional;
import static studio.environment.server.xml.XmlDocument.*;

public sealed interface XmlEdit permits XmlEdit.ReplaceAttribute, XmlEdit.RemoveElement, XmlEdit.InsertElement {
    record ReplaceAttribute(AttributeRef attribute, String expectedValue, String replacement) implements XmlEdit {
        public ReplaceAttribute { Objects.requireNonNull(attribute); Objects.requireNonNull(expectedValue); Objects.requireNonNull(replacement); }
    }
    record RemoveElement(ElementRef element) implements XmlEdit {
        public RemoveElement { Objects.requireNonNull(element); }
    }
    record InsertElement(ElementRef parent, Optional<ElementRef> before, String markup) implements XmlEdit {
        public InsertElement { Objects.requireNonNull(parent); Objects.requireNonNull(before); Objects.requireNonNull(markup); }
    }
}
