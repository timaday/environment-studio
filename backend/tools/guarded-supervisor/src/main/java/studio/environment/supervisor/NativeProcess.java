package studio.environment.supervisor;

/** Single-owner transient channel. No application or public caller receives this port. */
interface NativeProcess extends AutoCloseable {
    void write(byte[] bytes,long deadline);
    int read(long deadline);
    int exit(long deadline);
    boolean alive();
    void terminate();
    @Override void close();
}
