package studio.environment.supervisor;
import java.nio.file.*;
import java.util.*;
record Invocation(Path archive,String digest,Path configuration,String destination) {
    static Invocation parse(String[] arguments) {
        Refusal.require(arguments!=null&&arguments.length==9&&arguments[0].equals("apply"),"INVALID_ARGUMENTS");
        Map<String,String> values=new HashMap<>();for(int i=1;i<9;i+=2){Refusal.require(Set.of("--package","--sha256","--configuration","--destination").contains(arguments[i])&&values.putIfAbsent(arguments[i],arguments[i+1])==null,"INVALID_ARGUMENTS");}
        Refusal.require(values.get("--sha256").matches("[0-9a-f]{64}")&&values.get("--destination").matches("[a-z][a-z0-9.-]{0,63}"),"INVALID_ARGUMENTS");
        return new Invocation(path(values.get("--package")),values.get("--sha256"),path(values.get("--configuration")),values.get("--destination"));
    }
    static Path path(String text) {ClosedJson.scalar(text);Refusal.require(!text.isEmpty()&&text.length()<=4096&&text.indexOf('\0')<0&&text.indexOf('\r')<0&&text.indexOf('\n')<0,"INVALID_PATH");try{var path=Path.of(text);Refusal.require(path.isAbsolute()&&path.normalize().equals(path),"INVALID_PATH");return path;}catch(InvalidPathException e){throw new Refusal("INVALID_PATH");}}
    @Override public String toString(){return "Invocation[redacted]";}
}
