package studio.environment.buildxml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

/** Build-only Java 21 source launcher. Upstream sources never enter the checkout. */
public final class PrepareSources {
    static final String SOURCE_SHA256 = "24669b0269917ca63270e81222c038b3ce737eba12bdb4a159e48e8b1d5a4822";
    static final String BLOCK_SHA256 = "6857f432f98525b8669f31ea2af94c40cbaa7852c1d6a1fdf1c55b585df528f7";
    static final int MAX_ARCHIVE = 2 * 1024 * 1024;
    static final int MAX_ENTRY = 512 * 1024;
    static final int MAX_EXPANDED = 4 * 1024 * 1024;
    static final int MAX_ENTRIES = 512;
    static final String CLASSIFIER = "com/ctc/wstx/util/XmlChars.java";
    private static final String START = "\tpublic final static boolean is10NameStartChar";
    private static final String END = "    public final static boolean is11NameStartChar";
    private static final String REPLACEMENT = """
            // Modified for Environment Studio: XML 1.0 Fifth Edition name productions.
            // The qualified adapter also requires whole-source paired-surrogate validation.
            public final static boolean is10NameStartChar(char c)
            {
                return c == ':' || c == '_' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'
                    || c >= 0xC0 && c <= 0xD6 || c >= 0xD8 && c <= 0xF6 || c >= 0xF8 && c <= 0x2FF
                    || c >= 0x370 && c <= 0x37D || c >= 0x37F && c <= 0x1FFF
                    || c >= 0x200C && c <= 0x200D || c >= 0x2070 && c <= 0x218F
                    || c >= 0x2C00 && c <= 0x2FEF || c >= 0x3001 && c <= 0xD7FF
                    || c >= 0xF900 && c <= 0xFDCF || c >= 0xFDF0 && c <= 0xFFFD
                    || c >= 0xD800 && c <= 0xDB7F;
            }

            public final static boolean is10NameChar(char c)
            {
                return is10NameStartChar(c) || c == '-' || c == '.' || c >= '0' && c <= '9'
                    || c == 0xB7 || c >= 0x300 && c <= 0x36F || c >= 0x203F && c <= 0x2040
                    || c >= 0xDC00 && c <= 0xDFFF;
            }

        """;
    private PrepareSources() { }
    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw refusal("Supply the source archive and generated-source directory.");
        prepare(Path.of(args[0]), Path.of(args[1]));
    }
    static void prepare(Path archive, Path output) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(archive)) { bytes = input.readNBytes(MAX_ARCHIVE + 1); }
        if (bytes.length > MAX_ARCHIVE) throw refusal("Upstream archive exceeds the byte limit.");
        if (!hash(bytes).equals(SOURCE_SHA256)) throw refusal("Upstream source checksum mismatch.");
        Map<String, byte[]> selected = inspectArchive(bytes);
        byte[] classifier = selected.get(CLASSIFIER);
        if (classifier == null || !selected.containsKey("META-INF/LICENSE")) throw refusal("Required upstream sources or license are missing.");
        selected.put(CLASSIFIER, patch(new String(classifier, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
        Path root = output.toAbsolutePath().normalize();
        // Reject unexpected old sources and symlinks; never silently compile leftover files.
        for (Path parent = root; parent != null; parent = parent.getParent())
            if (Files.isSymbolicLink(parent)) throw refusal("Generated-source path contains a symbolic link.");
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            try (var existing = Files.walk(root)) {
                var iterator = existing.iterator(); int count = 0;
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (++count > MAX_ENTRIES || Files.isSymbolicLink(path)) throw refusal("Unexpected generated-source tree.");
                    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !selected.containsKey(root.relativize(path).toString().replace('\\', '/')))
                        throw refusal("Unexpected generated-source file.");
                }
            }
        }
        for (var entry : selected.entrySet()) {
            Path target = root.resolve(entry.getKey()); Files.createDirectories(target.getParent());
            try (var channel = Files.newByteChannel(target, Set.of(java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))) {
                var buffer = java.nio.ByteBuffer.wrap(entry.getValue()); while (buffer.hasRemaining()) channel.write(buffer);
            }
        }
    }
    static Map<String, byte[]> inspectArchive(byte[] archive) throws IOException {
        if (archive.length > MAX_ARCHIVE) throw refusal("Upstream archive exceeds the byte limit.");
        Map<String, byte[]> selected = new LinkedHashMap<>(); Set<String> names = new HashSet<>();
        long total = 0; int count = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++count > MAX_ENTRIES) throw refusal("Upstream archive exceeds the entry limit.");
                String name = entry.getName(); validatePath(name);
                if (!names.add(name.endsWith("/") ? name.substring(0, name.length() - 1) : name)) throw refusal("Upstream archive contains duplicate entries.");
                if (entry.getSize() > MAX_ENTRY) throw refusal("Upstream entry exceeds its byte limit.");
                byte[] content = zip.readNBytes(MAX_ENTRY + 1);
                if (content.length > MAX_ENTRY || (total += content.length) > MAX_EXPANDED) throw refusal("Upstream expansion exceeds its byte limit.");
                if (!entry.isDirectory() && (name.endsWith(".java") || name.equals("META-INF/LICENSE"))) selected.put(name, content);
            }
        }
        if (count == 0) throw refusal("Upstream archive has no entries.");
        return selected;
    }
    private static void validatePath(String name) {
        if (name.isEmpty() || name.length() > 256 || !name.matches("[A-Za-z0-9_./$-]+") || name.startsWith("/") || name.contains("\\") || name.contains(":")) throw refusal("Unexpected upstream archive path.");
        String plain = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        for (String part : plain.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..")) throw refusal("Unexpected upstream archive path.");
        boolean directory = name.endsWith("/");
        boolean allowed = directory && (name.equals("annotations/") || name.equals("com/") || name.equals("com/ctc/") || name.startsWith("com/ctc/wstx/") || name.startsWith("META-INF/"))
                || name.startsWith("com/ctc/wstx/") && (name.endsWith(".java") || name.endsWith("/package.html"))
                || name.equals("META-INF/LICENSE") || name.equals("META-INF/MANIFEST.MF")
                || name.startsWith("META-INF/services/") && name.substring(18).matches("[A-Za-z0-9_.]+")
                || name.equals("META-INF/maven/com.fasterxml.woodstox/woodstox-core/pom.xml")
                || name.equals("META-INF/maven/com.fasterxml.woodstox/woodstox-core/pom.properties");
        if (!allowed) throw refusal("Unexpected upstream archive path.");
    }
    static String patch(String source) {
        int start = source.indexOf(START), end = source.indexOf(END);
        if (start < 0 || end < start || source.indexOf(START, start + 1) >= 0 || source.indexOf(END, end + 1) >= 0
                || !hash(source.substring(start, end).getBytes(StandardCharsets.UTF_8)).equals(BLOCK_SHA256))
            throw refusal("Upstream classifier patch anchors changed.");
        return source.substring(0, start) + REPLACEMENT + source.substring(end);
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException unavailable) { throw refusal("Required SHA-256 is unavailable."); }
    }
    private static IllegalStateException refusal(String message) { return new IllegalStateException(message); }
}
