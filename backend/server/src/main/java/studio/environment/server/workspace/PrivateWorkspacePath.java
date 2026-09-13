package studio.environment.server.workspace;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import studio.environment.core.workspace.WorkspaceRefusal;

final class PrivateWorkspacePath {
    static final String DATABASE = "studio-workspace.db";
    static final long BUDGET = 256L * 1024 * 1024;
    private final Path directory;
    PrivateWorkspacePath(Path directory) {
        if (!directory.isAbsolute() || !directory.normalize().equals(directory)) throw unavailable();
        this.directory = directory;
        validateDirectory();
    }
    Path database() { return directory.resolve(DATABASE); }
    void validateDirectory() {
        try {
            Path walked = directory.getRoot();
            for (Path component : directory) {
                walked = walked.resolve(component);
                if (Files.isSymbolicLink(walked)) throw unavailable();
            }
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw unavailable();
            verifyPrivate(directory, "rwx------");
            // Source/image directories are never eligible runtime stores.
            Path ancestor = directory;
            while (ancestor != null) {
                if (Files.exists(ancestor.resolve(".git"), LinkOption.NOFOLLOW_LINKS)
                        || Files.exists(ancestor.resolve("backend/pom.xml"), LinkOption.NOFOLLOW_LINKS)) throw unavailable();
                ancestor = ancestor.getParent();
            }
        } catch (IOException | java.io.UncheckedIOException | UnsupportedOperationException failure) { throw unavailable(); }
    }
    void validateFiles(boolean requireDatabase) {
        validateDirectory();
        try {
            if (requireDatabase && !Files.isRegularFile(database(), LinkOption.NOFOLLOW_LINKS)) throw unavailable();
            try (var entries = Files.list(directory)) { validateEntries(entries); }
        } catch (IOException | java.io.UncheckedIOException | UnsupportedOperationException | ArithmeticException failure) { throw unavailable(); }
    }
    void validateEntries(java.util.stream.Stream<Path> entries) {
        try {
            long total = 0;
            int count = 0;
            var iterator = entries.iterator();
            while (iterator.hasNext()) {
                if (++count > 2) throw unavailable();
                Path entry = iterator.next();
                String name = entry.getFileName().toString();
                if (!Set.of(DATABASE, DATABASE + "-journal").contains(name)) throw unavailable();
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(entry)) throw unavailable();
                verifyPrivate(entry, "rw-------");
                if (((Number) Files.getAttribute(entry, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue() != 1) throw unavailable();
                total = Math.addExact(total, Files.size(entry));
                if (total > BUDGET) throw unavailable();
                if (name.endsWith("-journal")) verifyJournal(entry);
            }
        } catch (IOException | java.io.UncheckedIOException | UnsupportedOperationException | ArithmeticException failure) { throw unavailable(); }
    }
    void createDatabaseFile() {
        validateFiles(false);
        try {
            try (var entries = Files.list(directory)) { if (entries.findAny().isPresent()) throw unavailable(); }
            Files.createFile(database(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (IOException | java.io.UncheckedIOException | UnsupportedOperationException failure) { throw unavailable(); }
    }
    private static void verifyJournal(Path journal) throws IOException {
        if (Files.size(journal) == 0) return; // Crash between private creation and first header write.
        byte[] header;
        try (var input = Files.newInputStream(journal, LinkOption.NOFOLLOW_LINKS)) { header = input.readNBytes(28); }
        if (header.length != 28) throw unavailable();
        byte[] magic = java.util.HexFormat.of().parseHex("d9d505f920a163d7");
        boolean active = java.util.Arrays.equals(java.util.Arrays.copyOf(header, 8), magic);
        boolean notYetActive = java.util.Arrays.equals(java.util.Arrays.copyOf(header, 8), new byte[8]);
        if (!active && !notYetActive) throw unavailable();
    }
    private static void verifyPrivate(Path path, String permission) throws IOException {
        if (!Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString(permission))) throw unavailable();
        long uid = ((Number) Files.getAttribute(path, "unix:uid", LinkOption.NOFOLLOW_LINKS)).longValue();
        if (uid != new com.sun.security.auth.module.UnixSystem().getUid()) throw unavailable();
    }
    static WorkspaceRefusal unavailable() { return new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE); }
}
