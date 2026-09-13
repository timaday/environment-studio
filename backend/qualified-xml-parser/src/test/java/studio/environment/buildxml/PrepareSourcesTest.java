package studio.environment.buildxml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PrepareSourcesTest {
    @TempDir Path temporary;
    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
        }
        return bytes.toByteArray();
    }
    @Test void checksumMismatchRefusesBeforeCreatingAnyOutput() throws Exception {
        Path source = temporary.resolve("source.zip"); Files.write(source, zip(Map.of("com/ctc/wstx/A.java", new byte[0])));
        Path output = temporary.resolve("generated");
        assertThrows(IllegalStateException.class, () -> PrepareSources.prepare(source, output)); assertFalse(Files.exists(output));
        Files.write(source, new byte[PrepareSources.MAX_ARCHIVE + 1]);
        assertThrows(IllegalStateException.class, () -> PrepareSources.prepare(source, output)); assertFalse(Files.exists(output));
    }
    @Test void unexpectedAbsoluteTraversalAliasAndBackslashPathsRefuse() throws Exception {
        for (String name : new String[] {"../outside.java", "/absolute.java", "com/ctc/wstx/../outside.java", "com/ctc/wstx//A.java",
                "com/ctc/wstx/./A.java", "com\\ctc\\wstx\\A.java", "other/A.java", "C:/A.java", "META-INF/services/../evil", "com/ctc/wstx/bad\u0000.java", "com/ctc/wstx/bad\n.java"}) {
            byte[] bytes = zip(Map.of(name, new byte[0]));
            assertThrows(IllegalStateException.class, () -> PrepareSources.inspectArchive(bytes));
        }
    }
    @Test void duplicateEntriesAndArchiveExpansionLimitsRefuse() throws Exception {
        byte[] bytes = zip(Map.of("com/ctc/wstx/A.java", new byte[0], "com/ctc/wstx/B.java", new byte[0]));
        // Names are outside the CRC; make two same-name local/central entries independently.
        for (int i = 0; i < bytes.length - 6; i++) if (bytes[i] == 'B' && bytes[i + 1] == '.' && bytes[i + 2] == 'j') bytes[i] = 'A';
        byte[] duplicate = bytes; assertThrows(IllegalStateException.class, () -> PrepareSources.inspectArchive(duplicate));
        bytes = zip(Map.of("com/ctc/wstx/A.java", new byte[PrepareSources.MAX_ENTRY + 1]));
        byte[] hugeEntry = bytes; assertThrows(IllegalStateException.class, () -> PrepareSources.inspectArchive(hugeEntry));
        Map<String, byte[]> many = new LinkedHashMap<>();
        for (int i = 0; i <= PrepareSources.MAX_ENTRIES; i++) many.put("com/ctc/wstx/A" + i + ".java", new byte[0]);
        byte[] hugeCount = zip(many); assertThrows(IllegalStateException.class, () -> PrepareSources.inspectArchive(hugeCount));
        many.clear(); for (int i = 0; i < 9; i++) many.put("com/ctc/wstx/A" + i + ".java", new byte[PrepareSources.MAX_ENTRY]);
        byte[] hugeExpansion = zip(many); assertThrows(IllegalStateException.class, () -> PrepareSources.inspectArchive(hugeExpansion));
    }
    @Test void exactSourceBuildPreservesEverythingOutsideTwoFunctionsAndDropsProviders() throws Exception {
        byte[] archive = Files.readAllBytes(Path.of("target/upstream/woodstox-7.2.2-sources.jar"));
        var sources = PrepareSources.inspectArchive(archive);
        assertEquals(179, sources.size()); assertTrue(sources.keySet().stream().noneMatch(name -> name.contains("services/")));
        String original = new String(sources.get(PrepareSources.CLASSIFIER), StandardCharsets.UTF_8);
        String patched = PrepareSources.patch(original);
        int before = original.indexOf("\tpublic final static boolean is10NameStartChar");
        int after = original.indexOf("    public final static boolean is11NameStartChar");
        assertTrue(patched.startsWith(original.substring(0, before))); assertTrue(patched.endsWith(original.substring(after)));
        assertThrows(IllegalStateException.class, () -> PrepareSources.patch(original.replace("is10NameStartChar", "changedName")));
        assertThrows(IllegalStateException.class, () -> PrepareSources.patch(original.replace("0x312C", "0x312D")));
        assertThrows(IllegalStateException.class, () -> PrepareSources.patch(original + "\tpublic final static boolean is10NameStartChar"));
        Path output = temporary.resolve("generated"); PrepareSources.prepare(Path.of("target/upstream/woodstox-7.2.2-sources.jar"), output);
        for (var source : sources.entrySet()) if (!source.getKey().equals(PrepareSources.CLASSIFIER)) assertArrayEquals(source.getValue(), Files.readAllBytes(output.resolve(source.getKey())));
        assertEquals(patched, Files.readString(output.resolve(PrepareSources.CLASSIFIER)));
        Files.writeString(output.resolve("unregistered.java"), "untrusted");
        assertThrows(IllegalStateException.class, () -> PrepareSources.prepare(Path.of("target/upstream/woodstox-7.2.2-sources.jar"), output));
    }
    @Test void outputSymlinkRefusesWithoutWritingOutsideGeneratedTree() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside")); Path link = temporary.resolve("link"); Files.createSymbolicLink(link, outside);
        assertThrows(IllegalStateException.class, () -> PrepareSources.prepare(Path.of("target/upstream/woodstox-7.2.2-sources.jar"), link));
        try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
    }
}
