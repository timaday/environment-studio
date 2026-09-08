import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Build-time deterministic distribution; never used for package execution. */
class Assemble {
    public static void main(String[] args)throws Exception {
        Path target=Path.of(args[0]),home=target.resolve("environment-studio-guarded-"+args[1]),lib=home.resolve("lib"),licenses=home.resolve("licenses");Files.createDirectories(lib);Files.createDirectories(licenses);
        Files.copy(target.resolve("guarded-supervisor-"+args[1]+".jar"),lib.resolve("guarded-supervisor-"+args[1]+".jar"),StandardCopyOption.REPLACE_EXISTING);
        List<Path> jars;try(var stream=Files.list(lib)){jars=stream.filter(p->p.toString().endsWith(".jar")).sorted().toList();}
        Set<String> expected=Set.of("guarded-supervisor-"+args[1]+".jar","server-"+args[1]+"-classes.jar","core-"+args[1]+".jar","qualified-xml-parser-"+args[1]+".jar","stax2-api-4.3.0.jar","json-schema-validator-3.0.7.jar","jackson-databind-3.1.5.jar","jackson-core-3.1.5.jar","jackson-annotations-2.21.jar","itu-1.14.0.jar","slf4j-api-2.0.18.jar","slf4j-nop-2.0.18.jar");
        if(!jars.stream().map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(expected))throw new IllegalStateException("UNEXPECTED_DISTRIBUTION_DEPENDENCY");
        for(Path jar:jars){String name=jar.getFileName().toString();if(name.matches("(?i).*(spring|servlet|security|jdbc|sqlite|postgresql|ojdbc).*"))throw new IllegalStateException("FORBIDDEN_DEPENDENCY");
            try(var zip=new ZipFile(jar.toFile())){if(name.startsWith("server-")&&zip.stream().anyMatch(e->e.getName().startsWith("static/")||e.getName().startsWith("BOOT-INF/")))throw new IllegalStateException("NOT_THIN_SERVER_CLASSES");var entries=zip.stream().filter(e->!e.isDirectory()&&e.getName().matches("(?i)META-INF/[^/]*(LICENSE|NOTICE|COPYING)[^/]*")).sorted(Comparator.comparing(ZipEntry::getName)).toList();int i=0;for(var entry:entries){try(var in=zip.getInputStream(entry)){byte[] text=in.readNBytes(1_048_577);if(text.length>1_048_576)throw new IllegalStateException("LICENSE_TOO_LARGE");Files.write(licenses.resolve(name+"-"+(i++)+".txt"),text);}}}
        }
        String cp=String.join(":",jars.stream().map(p->"$base/lib/"+p.getFileName()).toList());
        String launcher="#!/bin/sh\nset -eu\nbase=$(CDPATH= cd -- \"${0%/*}\" && pwd -P)\nexec /usr/bin/env -i LANG=C.UTF-8 LC_ALL=C.UTF-8 /usr/bin/java -cp \""+cp+"\" studio.environment.supervisor.Main \"$@\"\n";
        Files.writeString(home.resolve("environment-studio-guarded"),launcher);Files.setPosixFilePermissions(home.resolve("environment-studio-guarded"),java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.writeString(home.resolve("README.txt"),"Environment Studio guarded supervisor candidate. Runtime qualification registry is empty. No execution combination is enabled. Native clients are not included. Dependency license notices are retained under licenses/ and inside their JARs.\n");
        List<Path> files;try(var stream=Files.walk(home)){files=stream.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().equals("SHA256SUMS")).sorted().toList();}StringBuilder hashes=new StringBuilder();for(var file:files)hashes.append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))).append("  ").append(home.relativize(file)).append('\n');Files.writeString(home.resolve("SHA256SUMS"),hashes);
        try(var zip=new ZipOutputStream(Files.newOutputStream(target.resolve(home.getFileName()+".zip")))){try(var stream=Files.walk(home)){for(Path file:stream.filter(Files::isRegularFile).sorted().toList()){var entry=new ZipEntry(home.getFileName()+"/"+home.relativize(file));entry.setTimeLocal(java.time.LocalDateTime.of(1980,1,1,0,0));zip.putNextEntry(entry);Files.copy(file,zip);zip.closeEntry();}}}
    }
}
