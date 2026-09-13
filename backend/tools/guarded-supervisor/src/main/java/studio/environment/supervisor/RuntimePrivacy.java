package studio.environment.supervisor;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import com.sun.management.HotSpotDiagnosticMXBean;

/** Fail-closed process diagnostic prerequisites, independent of runtime qualification. */
final class RuntimePrivacy {
    static boolean accepts(String limits,String errorFile,String createCore,String heapDump){
        if(!"/dev/null".equals(errorFile)||!"false".equals(createCore)||!"false".equals(heapDump)||limits.length()>16_384)return false;
        var rows=limits.lines().filter(line->line.startsWith("Max core file size")).toList();
        return rows.size()==1&&rows.getFirst().matches("Max core file size +0 +0 +bytes *");
    }
    static boolean fileCorePolicy(String pattern){return pattern!=null&&!pattern.isBlank()&&pattern.length()<=4096&&!pattern.startsWith("|")&&pattern.stripTrailing().chars().noneMatch(c->c<32);}
    static boolean established(){
        try(var input=Files.newInputStream(Path.of("/proc/self/limits"))){
            byte[] bytes=input.readNBytes(16_385);if(bytes.length>16_384)return false;
            try(var policy=Files.newInputStream(Path.of("/proc/sys/kernel/core_pattern"))){byte[] pattern=policy.readNBytes(4097);if(pattern.length>4096||!fileCorePolicy(new String(pattern,StandardCharsets.US_ASCII)))return false;}
            var bean=ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            return accepts(new String(bytes,StandardCharsets.US_ASCII),bean.getVMOption("ErrorFile").getValue(),bean.getVMOption("CreateCoredumpOnCrash").getValue(),bean.getVMOption("HeapDumpOnOutOfMemoryError").getValue());
        }catch(Throwable failure){return false;}
    }
}
