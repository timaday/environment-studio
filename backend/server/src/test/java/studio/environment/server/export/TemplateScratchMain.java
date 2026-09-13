package studio.environment.server.export;

import java.nio.file.Files;
import java.nio.file.Path;

/** Test-only bridge for separately owned disposable qualification; never launches a client. */
public final class TemplateScratchMain {
    private TemplateScratchMain() { }
    public static void main(String[] args) throws Exception {
        var admitted=new PackageAdmission().read(Files.readAllBytes(Path.of(args[0])),Files.readAllBytes(Path.of(args[1])));
        if(!(admitted instanceof PackageAdmission.Result.Accepted accepted))throw new IllegalStateException("SCRATCH_ADMISSION_REFUSED");
        var result=new TransactionTemplates().generate(accepted);
        if(!(result instanceof TransactionTemplates.Result.Candidate candidate))throw new IllegalStateException("SCRATCH_GENERATION_REFUSED");
        Files.write(Path.of(args[2]),candidate.bytes());
        var bounds=candidate.blocks().stream().map(b->b.offset()+":"+b.length()).collect(java.util.stream.Collectors.joining("\n"));
        Files.writeString(Path.of(args[2]+".bounds"),bounds+"\n");
        Files.writeString(Path.of(args[2]+".digest"),accepted.programDigest()+"\n");
    }
}
