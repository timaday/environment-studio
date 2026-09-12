package studio.environment.server.plan;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Loopback-only HTTP test transport; retains synthetic cookies only in memory and never prints bodies. */
public final class V3WorkflowHttpSocketClient {
    public record Response(int status,Map<String,String> headers,String body) {
        @Override public String toString(){return "MockHttpResponse[redacted]";}
    }
    public record RawResponse(int status,Map<String,String> headers,byte[] body) {
        public RawResponse { body=body.clone(); }
        @Override public byte[] body(){return body.clone();}
        @Override public String toString(){return "MockRawHttpResponse[redacted]";}
    }
    private final int port;
    private String cookie;
    private String csrfHeader,csrfValue;
    public V3WorkflowHttpSocketClient(int port){this.port=port;}
    public void csrf(String name,String value){csrfHeader=name;csrfValue=value;}
    public Response get(String path) throws IOException {return request("GET",path,null,true);}
    public Response request(String method,String path,String body,boolean csrf) throws IOException {
        byte[] bytes=body==null?new byte[0]:body.getBytes(StandardCharsets.UTF_8);
        try(var pending=begin(method,path,bytes.length,csrf)) {pending.write(bytes);return pending.response();}
    }
    public Pending begin(String method,String path,int length,boolean csrf) throws IOException {
        return begin(method,path,length,csrf,0);
    }
    public Pending begin(String method,String path,int length,boolean csrf,int receiveBuffer) throws IOException {
        var socket=new Socket();if(receiveBuffer>0)socket.setReceiveBufferSize(receiveBuffer);socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),port),5000);socket.setSoTimeout(15_000);
        var output=socket.getOutputStream();
        var headers=new StringBuilder(method+" "+path+" HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n");
        if(cookie!=null) headers.append("Cookie: ").append(cookie).append("\r\n");
        if(!method.equals("GET")) {
            headers.append("Origin: http://localhost\r\nContent-Type: application/json\r\nContent-Length: ").append(length).append("\r\n");
            if(csrf && csrfHeader!=null) headers.append(csrfHeader).append(": ").append(csrfValue).append("\r\n");
        }
        headers.append("\r\n");output.write(headers.toString().getBytes(StandardCharsets.US_ASCII));output.flush();
        return new Pending(socket,path);
    }
    public final class Pending implements AutoCloseable {
        private final Socket socket;private final String path;
        private Pending(Socket socket,String path){this.socket=socket;this.path=path;}
        public void timeout(int milliseconds)throws java.net.SocketException{socket.setSoTimeout(milliseconds);}
        public void write(byte[] bytes) throws IOException {socket.getOutputStream().write(bytes);socket.getOutputStream().flush();}
        public Response headersOnly()throws IOException {
            var input=socket.getInputStream();String[] first=line(input).split(" ",3);if(first.length<2)throw new IOException("MOCK_STATUS_MISSING");
            var headers=new LinkedHashMap<String,String>();String header;
            while(!(header=line(input)).isEmpty()) {
                if(headers.size()>=100)throw new IOException("MOCK_HEADER_LIMIT");int colon=header.indexOf(':');if(colon<1)throw new IOException("MOCK_HEADER_INVALID");
                headers.put(header.substring(0,colon).toLowerCase(Locale.ROOT),header.substring(colon+1).trim());
            }
            return new Response(Integer.parseInt(first[1]),Map.copyOf(headers),"");
        }
        public Response response() throws IOException {
            var response=readResponse();
            V3WorkflowWireAssertions.verify(path,response.status(),response.body());
            return response;
        }
        public RawResponse rawResponse() throws IOException {
            return readRawResponse();
        }
        /** A cancelled transfer is checked as an abort, never counted as a schema-valid success. */
        public Response responseAfterCancellation() throws IOException {
            var response=readResponse();
            org.junit.jupiter.api.Assertions.assertEquals("",response.body(),"Cancelled transfer emitted a body");
            org.junit.jupiter.api.Assertions.assertFalse(response.headers().getOrDefault("content-type","").startsWith("application/json"),"Cancelled transfer claimed a JSON result");
            return response;
        }
        private Response readResponse() throws IOException {
            var raw=readRawResponse();
            return new Response(raw.status(),raw.headers(),new String(raw.body(),StandardCharsets.UTF_8));
        }
        private RawResponse readRawResponse() throws IOException {
            var input=socket.getInputStream();String first=line(input);String[] status=first.split(" ",3);
            if(status.length<2) throw new IOException("MOCK_HTTP_STATUS_UNAVAILABLE");
            var headers=new LinkedHashMap<String,String>();String header;
            while(!(header=line(input)).isEmpty()) {
                if(headers.size()>=100) throw new IOException("MOCK_HEADER_LIMIT");
                int colon=header.indexOf(':');if(colon<1)throw new IOException("MOCK_HEADER_INVALID");
                String name=header.substring(0,colon).toLowerCase(Locale.ROOT),value=header.substring(colon+1).trim();headers.put(name,value);
                if(name.equals("set-cookie") && value.startsWith("JSESSIONID="))cookie=value.split(";",2)[0];
            }
            var body=new ByteArrayOutputStream();
            if("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
                while(true) {
                    int size=Integer.parseInt(line(input).split(";",2)[0],16);
                    if(size==0){while(!line(input).isEmpty()){}break;}
                    if(body.size()+size>4_194_304)throw new IOException("MOCK_RESPONSE_LIMIT");
                    byte[] bytes=input.readNBytes(size);if(bytes.length!=size)throw new IOException("MOCK_RESPONSE_TRUNCATED");body.write(bytes);
                    if(!line(input).isEmpty())throw new IOException("MOCK_CHUNK_INVALID");
                }
            } else if(headers.containsKey("content-length")) {
                int size=Integer.parseInt(headers.get("content-length"));if(size>4_194_304)throw new IOException("MOCK_RESPONSE_LIMIT");byte[] bytes=input.readNBytes(size);if(bytes.length!=size)throw new IOException("MOCK_RESPONSE_TRUNCATED");body.write(bytes);
            } else {body.write(input.readNBytes(4_194_305));if(body.size()>4_194_304)throw new IOException("MOCK_RESPONSE_LIMIT");}
            return new RawResponse(Integer.parseInt(status[1]),Map.copyOf(headers),body.toByteArray());
        }
        public void close() throws IOException {socket.close();}
    }
    private static String line(InputStream input) throws IOException {
        var bytes=new ByteArrayOutputStream();int previous=-1;
        while(true) {
            int next=input.read();if(next<0)throw new IOException("MOCK_RESPONSE_TRUNCATED");
            if(previous=='\r' && next=='\n') {byte[] line=bytes.toByteArray();return new String(line,0,line.length-1,StandardCharsets.US_ASCII);}
            bytes.write(next);previous=next;if(bytes.size()>8192)throw new IOException("MOCK_HEADER_LIMIT");
        }
    }
}
