package studio.environment.supervisor;
import java.nio.charset.*;
import java.security.cert.*;
import java.util.*;

final class TrustCertificates {
    private final byte[] pem;
    private final List<X509Certificate> certificates;
    private TrustCertificates(byte[] pem,List<X509Certificate> certificates){this.pem=pem.clone();this.certificates=List.copyOf(certificates);}
    static TrustCertificates read(Configuration.Pinned input){byte[] bytes=AdmittedFiles.read(input.path(),1_048_576);Refusal.require(AdmittedFiles.sha256(bytes).equals(input.sha256()),"TRUST_HASH_MISMATCH");return parse(bytes);}
    static TrustCertificates parse(byte[] bytes){
        Refusal.require(bytes.length<=1_048_576,"RESOURCE_LIMIT");for(byte b:bytes)Refusal.require((b&255)<128,"INVALID_TRUST");String pem=new String(bytes,StandardCharsets.US_ASCII);
        var matcher=java.util.regex.Pattern.compile("\\G[ \\t\\r\\n]*-----BEGIN CERTIFICATE-----\\r?\\n([A-Za-z0-9+/=\\r\\n]+)-----END CERTIFICATE-----").matcher(pem);int end=0;List<X509Certificate> certs=new ArrayList<>();Set<String> hashes=new HashSet<>();
        try{while(matcher.find()){Refusal.require(certs.size()<128,"RESOURCE_LIMIT");byte[] der=Base64.getMimeDecoder().decode(matcher.group(1));var stream=new java.io.ByteArrayInputStream(der);var cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(stream);Refusal.require(stream.available()==0&&Arrays.equals(der,cert.getEncoded()),"INVALID_TRUST");cert.checkValidity();certs.add(cert);end=matcher.end();}Refusal.require(!certs.isEmpty()&&pem.substring(end).matches("[ \\t\\r\\n]*"),"INVALID_TRUST");return new TrustCertificates(bytes,certs);}catch(Refusal e){throw e;}catch(Exception e){throw new Refusal("INVALID_TRUST");}
    }
    byte[] bytes(){return pem.clone();}
    List<X509Certificate> certificates(){return certificates;}
    @Override public String toString(){return "TrustCertificates[redacted]";}
}
