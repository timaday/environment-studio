package studio.environment.server.plan;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Loopback-only HTTP test transport; retains synthetic cookies only in memory and never prints bodies. */
public final class PlanHttpSocketClient {
    public record Response(int status,Map<String,String> headers,String body) {
        @Override public String toString(){return "MockHttpResponse[redacted]";}
    }
    private final int port;
    private String cookie;
    private String csrfHeader,csrfValue;
    public PlanHttpSocketClient(int port){this.port=port;}
    public void csrf(String name,String value){csrfHeader=name;csrfValue=value;}
    public Response get(String path) throws IOException {return request("GET",path,null,true);}
    public Response request(String method,String path,String body,boolean csrf) throws IOException {
        byte[] bytes=body==null?new byte[0]:body.getBytes(StandardCharsets.UTF_8);
        try(var pending=begin(method,path,bytes.length,csrf)) {pending.write(bytes);return pending.response();}
    }
    public Pending begin(String method,String path,int length,boolean csrf) throws IOException {
        var socket=new Socket();socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),port),5000);socket.setSoTimeout(15_000);
        var output=socket.getOutputStream();
        var headers=new StringBuilder(method+" "+path+" HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n");
        if(cookie!=null) headers.append("Cookie: ").append(cookie).append("\r\n");
        if(!method.equals("GET")) {
            headers.append("Origin: http://localhost\r\nContent-Type: application/json\r\nContent-Length: ").append(length).append("\r\n");
            if(csrf && csrfHeader!=null) headers.append(csrfHeader).append(": ").append(csrfValue).append("\r\n");
        }
        headers.append("\r\n");output.write(headers.toString().getBytes(StandardCharsets.US_ASCII));output.flush();
        return new Pending(socket);
    }
    public final class Pending implements AutoCloseable {
        private final Socket socket;
        private Pending(Socket socket){this.socket=socket;}
        public void write(byte[] bytes) throws IOException {socket.getOutputStream().write(bytes);socket.getOutputStream().flush();}
        public Response response() throws IOException {
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
                    if(body.size()+size>1_048_576)throw new IOException("MOCK_RESPONSE_LIMIT");
                    byte[] bytes=input.readNBytes(size);if(bytes.length!=size)throw new IOException("MOCK_RESPONSE_TRUNCATED");body.write(bytes);
                    if(!line(input).isEmpty())throw new IOException("MOCK_CHUNK_INVALID");
                }
            } else if(headers.containsKey("content-length")) {
                int size=Integer.parseInt(headers.get("content-length"));if(size>1_048_576)throw new IOException("MOCK_RESPONSE_LIMIT");body.write(input.readNBytes(size));
            } else {body.write(input.readNBytes(1_048_577));if(body.size()>1_048_576)throw new IOException("MOCK_RESPONSE_LIMIT");}
            return new Response(Integer.parseInt(status[1]),Map.copyOf(headers),body.toString(StandardCharsets.UTF_8));
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
