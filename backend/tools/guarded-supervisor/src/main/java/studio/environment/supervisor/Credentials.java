package studio.environment.supervisor;
import java.util.Arrays;
final class Credentials implements AutoCloseable {
    private char[] username;private final BoundedSecret password;
    Credentials(char[] username,BoundedSecret password,boolean pg){try{Refusal.require(username.length<=128,"AUTHENTICATION_UNSUPPORTED");ClientProtocol.account(pg,new String(username));this.username=username.clone();this.password=password;}catch(RuntimeException e){password.close();throw e;}}
    String username(){Refusal.require(username!=null,"CREDENTIAL_CLOSED");return new String(username);}
    BoundedSecret password(){return password;}
    @Override public void close(){if(username!=null){Arrays.fill(username,'\0');username=null;}password.close();}
    @Override public String toString(){return "Credentials[redacted]";}
}
