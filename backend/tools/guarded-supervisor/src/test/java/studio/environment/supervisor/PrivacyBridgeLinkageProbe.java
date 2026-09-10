package studio.environment.supervisor;

/** A deliberately mismatched isolated class cannot retain partial registration. */
public final class PrivacyBridgeLinkageProbe {
    public static void load(String path){System.load(path);}
    public static void main(String[] args){
        boolean loadFailed=false,methodFailed=false;
        try{System.load(args[0]);}catch(LinkageError expected){loadFailed=true;}
        try{PrivacyBridge.armFork(513);}catch(UnsatisfiedLinkError expected){methodFailed=true;}
        if(!loadFailed||!methodFailed)throw new AssertionError("PARTIAL_NATIVE_REGISTRATION");
    }
}
