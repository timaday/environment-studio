package studio.environment.core.session;

public record Owner(String issuer, String subject) {
    public Owner {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("INVALID_OWNER");
        }
    }
}
