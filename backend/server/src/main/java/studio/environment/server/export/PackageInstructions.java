package studio.environment.server.export;

import java.nio.charset.StandardCharsets;

/** Exact fixed text from the external supervisor contract; contains no credential or test-mode option. */
public final class PackageInstructions {
    private PackageInstructions() { }
    public static byte[] bytes(){return TEXT.getBytes(StandardCharsets.UTF_8);}
    private static final String TEXT="""
Environment Studio guarded package v1
Use the separately installed, verified Environment Studio guarded supervisor.
environment-studio-guarded apply --package /absolute/package.zip --sha256 REVIEWED_ARCHIVE_SHA256 --configuration /absolute/approved-client.json --destination APPROVED_DESTINATION_ID
Replace placeholders from the reviewed export and independently approved client configuration.
Provide credentials only when the supervisor requests them. Never add them to this command or the package.
Do not execute transaction.sql directly. The supervisor owns transaction control and commit acknowledgement.
""";
}
