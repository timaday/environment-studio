package studio.environment.server.plan;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.function.LongSupplier;
import studio.environment.core.plan.PlanPorts.CredentialLengths;
import static studio.environment.server.plan.PlanBodyFailure.Code.*;

/** One-shot decoding into caller-owned arrays. The transport must also bound blocked reads. */
final class CredentialJsonReader {
    private final InputStream input;
    private final LongSupplier nanoTime;
    private final long deadline;
    private int bytes;
    private CredentialJsonReader(InputStream input,LongSupplier nanoTime,long deadline) {
        this.input=input; this.nanoTime=nanoTime; this.deadline=deadline;
    }
    static CredentialLengths read(InputStream input,char[] user,char[] password,LongSupplier nanoTime,long deadline) {
        boolean accepted=false;
        char[] key=new char[8];
        try {
            var reader=new CredentialJsonReader(input,nanoTime,deadline);
            reader.expect('{');
            int usernameLength=-1,passwordLength=-1;
            for(int index=0;index<2;index++) {
                if(index>0) reader.expect(',');
                int length=reader.string(key,8,8);
                reader.expect(':');
                if(matches(key,length,"username") && usernameLength<0) usernameLength=reader.string(user,128,512);
                else if(matches(key,length,"password") && passwordLength<0) passwordLength=reader.string(password,1024,4096);
                else throw new PlanBodyFailure(MALFORMED_BODY);
                Arrays.fill(key,'\0');
            }
            reader.expect('}');
            if(reader.nonWhitespace()!=-1 || usernameLength<=0 || passwordLength<=0) throw new PlanBodyFailure(MALFORMED_BODY);
            accepted=true;
            return new CredentialLengths(usernameLength,passwordLength);
        } finally {
            Arrays.fill(key,'\0');
            if(!accepted) { Arrays.fill(user,'\0'); Arrays.fill(password,'\0'); }
        }
    }
    private static boolean matches(char[] value,int length,String constant) {
        if(length!=constant.length()) return false;
        for(int i=0;i<length;i++) if(value[i]!=constant.charAt(i)) return false;
        return true;
    }
    private int string(char[] output,int maxCodePoints,int maxBytes) {
        expect('"');
        int count=0,codePoints=0,decodedBytes=0;
        while(true) {
            int cp=take();
            if(cp=='"') return count;
            if(cp<0x20) throw new PlanBodyFailure(MALFORMED_BODY);
            if(cp=='\\') {
                cp=take();
                cp=switch(cp) {
                    case '"','\\','/' -> cp;
                    case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    case 'u' -> escapedScalar();
                    default -> throw new PlanBodyFailure(MALFORMED_BODY);
                };
            } else if(cp>=0x80) cp=utf8Scalar(cp);
            if(cp==0 || ++codePoints>maxCodePoints) throw new PlanBodyFailure(MALFORMED_BODY);
            decodedBytes+=cp<0x80?1:cp<0x800?2:cp<0x10000?3:4;
            int units=Character.charCount(cp);
            if(decodedBytes>maxBytes || count+units>output.length) throw new PlanBodyFailure(MALFORMED_BODY);
            count+=Character.toChars(cp,output,count);
        }
    }
    private int escapedScalar() {
        int first=hexUnit();
        if(first>=0xdc00 && first<=0xdfff) throw new PlanBodyFailure(MALFORMED_BODY);
        if(first<0xd800 || first>0xdbff) return first;
        if(take()!='\\' || take()!='u') throw new PlanBodyFailure(MALFORMED_BODY);
        int second=hexUnit();
        if(second<0xdc00 || second>0xdfff) throw new PlanBodyFailure(MALFORMED_BODY);
        return Character.toCodePoint((char)first,(char)second);
    }
    private int hexUnit() {
        int value=0;
        for(int i=0;i<4;i++) {
            int next=take();
            int digit=next>='0' && next<='9'?next-'0':next>='a' && next<='f'?next-'a'+10:next>='A' && next<='F'?next-'A'+10:-1;
            if(digit<0) throw new PlanBodyFailure(MALFORMED_BODY);
            value=value*16+digit;
        }
        return value;
    }
    private int utf8Scalar(int first) {
        int length=first>=0xc2 && first<=0xdf?2:first>=0xe0 && first<=0xef?3:first>=0xf0 && first<=0xf4?4:0;
        if(length==0) throw new PlanBodyFailure(MALFORMED_BODY);
        int value=first & (0x7f>>length);
        for(int i=1;i<length;i++) {
            int next=take();
            if(next<0x80 || next>0xbf) throw new PlanBodyFailure(MALFORMED_BODY);
            value=(value<<6)|(next&0x3f);
        }
        if(value<(length==2?0x80:length==3?0x800:0x10000) || value>0x10ffff || value>=0xd800 && value<=0xdfff) throw new PlanBodyFailure(MALFORMED_BODY);
        return value;
    }
    private void expect(int expected) { if(nonWhitespace()!=expected) throw new PlanBodyFailure(MALFORMED_BODY); }
    private int nonWhitespace() {
        int next;
        do { next=take(); } while(next==' ' || next=='\t' || next=='\r' || next=='\n');
        return next;
    }
    private int take() {
        checkDeadline();
        try {
            int next=input.read();
            checkDeadline();
            if(next>=0 && ++bytes>16_384) throw new PlanBodyFailure(BODY_TOO_LARGE);
            return next;
        } catch(IOException failure) { throw new PlanBodyFailure(MALFORMED_BODY); }
    }
    private void checkDeadline() { if(nanoTime.getAsLong()-deadline>=0) throw new PlanBodyFailure(BODY_DEADLINE); }
}
