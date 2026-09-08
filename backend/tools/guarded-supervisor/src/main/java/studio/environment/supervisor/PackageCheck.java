package studio.environment.supervisor;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import studio.environment.server.export.*;

final class PackageCheck {
    record Regenerated(PackageAdmission.Result.Accepted inputs,TransactionTemplates.Result.Candidate program,String archiveDigest) { @Override public String toString(){return "Regenerated[redacted,unqualified]";} }
    static Regenerated read(byte[] bytes,String expectedDigest){
        Refusal.require(bytes.length<=160*1024*1024+438,"RESOURCE_LIMIT");
        Refusal.require(AdmittedFiles.sha256(bytes).equals(expectedDigest),"ARCHIVE_HASH_MISMATCH");
        var checked=new GuardedPackageInspector().inspect(bytes);Refusal.require(checked instanceof GuardedPackageInspector.Result.Unqualified,"PACKAGE_REJECTED");
        var decoded=new StrictPackageZip().read(bytes);Refusal.require(decoded instanceof StrictPackageZip.ReadResult.Decoded,"PACKAGE_REJECTED");var members=((StrictPackageZip.ReadResult.Decoded)decoded).members();
        var manifest=ClosedJson.read(members.get(0).bytes(),65536,12,16384);
        var admitted=new PackageAdmission().read(JsonMapper.builder().build().writeValueAsBytes(manifest.get("execution")),members.get(1).bytes());Refusal.require(admitted instanceof PackageAdmission.Result.Accepted,"PACKAGE_REJECTED");var inputs=(PackageAdmission.Result.Accepted)admitted;
        var result=new TransactionTemplates().generate(inputs);Refusal.require(result instanceof TransactionTemplates.Result.Candidate,"TEMPLATE_UNAVAILABLE");var program=(TransactionTemplates.Result.Candidate)result;
        Refusal.require(Arrays.equals(program.bytes(),members.get(2).bytes()),"PROGRAM_MISMATCH");return new Regenerated(inputs,program,expectedDigest);
    }
}
