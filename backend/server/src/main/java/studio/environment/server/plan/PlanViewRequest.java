package studio.environment.server.plan;

import java.util.List;
import studio.environment.core.workspace.NativeCommand;

sealed interface PlanViewRequest {
    String revision();
    enum Route { MATERIALIZATION, DOCUMENTS, ENTITIES, RELATIONS, DRAFT, CONTAINMENT, PLACEMENTS, DOCUMENT, CAPTURE, PREVIEW, VALIDATION;
        boolean collection() {return this==CAPTURE || this==PREVIEW;}
    }
    enum Side { CURRENT, TARGET }
    enum Mode { RAW, PLACEHOLDERS, FORMATTED }
    enum Section { INCLUDED, DEPENDENCIES, RELATIONS, CONFLICTS }
    record Revision(String revision) implements PlanViewRequest { }
    record GraphPage(String revision,Side side,int offset,int limit) implements PlanViewRequest { }
    record DraftPage(String revision,int offset,int limit) implements PlanViewRequest { }
    record Placements(String revision,String documentId,String projectionId,int offset,int limit) implements PlanViewRequest { }
    record Document(String revision,Side side,String documentId,Mode mode,boolean disclosed) implements PlanViewRequest { }
    record Mapping(String handle,String slotId,String label) { @Override public String toString(){return "CaptureMapping[redacted]";} }
    record Capture(String revision,String profileId,String profileRevision,List<Mapping> mappings) implements PlanViewRequest {
        public Capture {mappings=List.copyOf(mappings);} @Override public String toString(){return "CaptureRequest[redacted]";}
    }
    record Preview(String revision,NativeCommand.Reference profile,boolean all,List<String> roots,Section section,int offset,int limit) implements PlanViewRequest {
        public Preview {roots=List.copyOf(roots);} @Override public String toString(){return "PreviewRequest[redacted]";}
    }
}
