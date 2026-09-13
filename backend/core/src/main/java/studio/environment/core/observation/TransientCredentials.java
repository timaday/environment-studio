package studio.environment.core.observation;

import java.util.Arrays;
import java.util.Objects;

/** One operation owns these buffers. Drivers may retain immutable copies beyond this boundary. */
public final class TransientCredentials implements AutoCloseable {
    private char[] user;
    private char[] password;
    public TransientCredentials(char[] user, char[] password) { this.user = Objects.requireNonNull(user).clone(); this.password = Objects.requireNonNull(password).clone(); }
    public synchronized char[] copyUser() { if (user == null) throw new IllegalStateException("CREDENTIALS_CLOSED"); return user.clone(); }
    public synchronized char[] copyPassword() { if (password == null) throw new IllegalStateException("CREDENTIALS_CLOSED"); return password.clone(); }
    public synchronized boolean closed() { return password == null; }
    @Override public synchronized void close() { if (user != null) Arrays.fill(user, '\0'); if (password != null) Arrays.fill(password, '\0'); user = null; password = null; }
    @Override public String toString() { return "TransientCredentials[REDACTED]"; }
}
