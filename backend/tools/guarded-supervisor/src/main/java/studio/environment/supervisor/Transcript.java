package studio.environment.supervisor;
import java.nio.charset.StandardCharsets;
final class Transcript {
    private final NativeProcess child;private int total;private boolean failed;
    Transcript(NativeProcess child){this.child=child;}
    private int next(long deadline){Refusal.require(!failed,"TRANSCRIPT_FAILED");try{Refusal.require(++total<=1_048_576,"TRANSCRIPT_LIMIT");return child.read(deadline);}catch(RuntimeException e){failed=true;throw e;}}
    private void require(boolean condition){if(!condition){failed=true;throw new Refusal("UNEXPECTED_TRANSCRIPT");}}
    void exact(String expected,long deadline){byte[] frame=expected.getBytes(StandardCharsets.US_ASCII);Refusal.require(frame.length<=65_536,"FRAME_LIMIT");for(byte b:frame)require(next(deadline)==(b&255));}
    String line(long deadline){for(;;){var line=new StringBuilder();for(;;){int b=next(deadline);require(b>=0&&b<128);if(b=='\n')break;require(line.length()<65_535&&b!='\r');line.append((char)b);}if(!line.isEmpty())return line.toString();}}
    void line(String expected,long deadline){require(line(deadline).equals(expected));}
    void oracleBanner(long deadline){String banner=line(deadline);require(banner.matches("SQL\\*Plus: Release 23\\.26\\.3\\.0\\.0 - Production on (Mon|Tue|Wed|Thu|Fri|Sat|Sun) (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) ([1-9]|[12][0-9]|3[01]) ([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9] [0-9]{4}"));line("Version 23.26.3.0.0",deadline);line("Copyright (c) 1982, 2026, Oracle.  All rights reserved.",deadline);exact("\nSQL> ",deadline);}
    void eof(long deadline){for(;;){int b=next(deadline);if(b==-1)return;require(b=='\n');}}
    boolean failed(){return failed;}
    @Override public String toString(){return "Transcript[redacted]";}
}
