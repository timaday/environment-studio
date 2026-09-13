package studio.environment.supervisor;
import java.nio.file.*;
import java.nio.channels.*;
import java.nio.*;
import java.util.*;
import java.security.*;

final class AdmittedFiles {
    static byte[] read(Path path,int limit) {
        trusted(path);
        try(var root=Files.newDirectoryStream(path.getRoot())) {
            Refusal.require(root instanceof SecureDirectoryStream<?>,"SECURE_FILES_UNAVAILABLE");
            @SuppressWarnings("unchecked") var current=(SecureDirectoryStream<Path>)root;
            List<SecureDirectoryStream<Path>> children=new ArrayList<>();
            try {
                for(int i=0;i<path.getNameCount()-1;i++){current=current.newDirectoryStream(path.getName(i),LinkOption.NOFOLLOW_LINKS);children.add(current);}
                Path leaf=path.getFileName();var attrs=current.getFileAttributeView(leaf,java.nio.file.attribute.BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS).readAttributes();
                Refusal.require(attrs.isRegularFile()&&attrs.size()<=limit,"INVALID_FILE");
                try(var channel=current.newByteChannel(leaf,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))){
                    Refusal.require(channel.size()<=limit,"RESOURCE_LIMIT");var out=new java.io.ByteArrayOutputStream((int)Math.min(channel.size(),65536));var buffer=ByteBuffer.allocate(65536);int total=0,n;
                    while((n=channel.read(buffer))!=-1){Refusal.require(n<=limit-total,"RESOURCE_LIMIT");total+=n;out.write(buffer.array(),0,n);buffer.clear();}return out.toByteArray();
                }
            } finally {for(int i=children.size()-1;i>=0;i--)children.get(i).close();}
        }catch(Refusal e){throw e;}catch(Exception e){throw new Refusal("FILE_UNAVAILABLE");}
    }
    private static void trusted(Path path){
        Refusal.require(System.getProperty("os.name").equals("Linux"),"FILE_POLICY_UNAVAILABLE");
        try{Refusal.require(Set.of("ext4","ext3","ext2","tmpfs","overlay").contains(Files.getFileStore(path.getRoot()).type()),"FILE_POLICY_UNAVAILABLE");}catch(java.io.IOException e){throw new Refusal("FILE_POLICY_UNAVAILABLE");}
        long uid=new com.sun.security.auth.module.UnixSystem().getUid();Path component=path.getRoot();
        try{
            for(int index=-1;index<path.getNameCount();index++){
                if(index>=0)component=component.resolve(path.getName(index));
                Refusal.require(Set.of("ext4","ext3","ext2","tmpfs","overlay").contains(Files.getFileStore(component).type()),"FILE_POLICY_UNAVAILABLE");
                var attrs=Files.readAttributes(component,"unix:uid,mode,isSymbolicLink,isDirectory,isRegularFile",LinkOption.NOFOLLOW_LINKS);
                long owner=((Number)attrs.get("uid")).longValue();int mode=((Number)attrs.get("mode")).intValue();
                Refusal.require(owner==0||owner==uid,"UNTRUSTED_FILE_OWNER");Refusal.require(!(Boolean)attrs.get("isSymbolicLink"),"INVALID_FILE");
                boolean last=index==path.getNameCount()-1;
                Refusal.require(last?(Boolean)attrs.get("isRegularFile"):(Boolean)attrs.get("isDirectory"),"INVALID_FILE");
                // Linux POSIX ACL write permissions are capped by the group-class mode mask.
                // Sticky ancestry is accepted only with a following root/operator-owned component.
                boolean sticky=!last&&(mode&01000)!=0;
                Refusal.require((mode&0022)==0||sticky,"UNTRUSTED_FILE_WRITE");
            }
        }catch(Refusal e){throw e;}catch(Exception e){throw new Refusal("FILE_POLICY_UNAVAILABLE");}
    }
    static String sha256(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
}
