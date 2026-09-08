package studio.environment.server.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Entirely invented local OIDC provider. Key material and tokens remain in test memory. */
final class MockIssuer implements AutoCloseable {
    enum TokenMode { VALID, WRONG_NONCE, WRONG_ISSUER, WRONG_AUDIENCE, WRONG_SIGNATURE }
    private final HttpServer server;
    private final RSAKey key;
    private final RSAKey otherKey;
    private final Map<String, Map<String, String>> codes = new ConcurrentHashMap<>();
    final java.util.List<String> issuedTokens = new java.util.concurrent.CopyOnWriteArrayList<>();
    volatile TokenMode mode = TokenMode.VALID;
    volatile String subject = "invented-owner";
    volatile boolean verifiedPkce;
    MockIssuer() {
        try {
            key = new RSAKeyGenerator(2048).keyID("mock-key").generate();
            otherKey = new RSAKeyGenerator(2048).keyID("mock-key").generate();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/.well-known/openid-configuration", exchange -> {
                String base = issuer();
                reply(exchange, 200, "{\"issuer\":\"" + base + "\",\"authorization_endpoint\":\"" + base
                        + "/authorize\",\"token_endpoint\":\"" + base + "/token\",\"jwks_uri\":\"" + base
                        + "/jwks\",\"response_types_supported\":[\"code\"],\"subject_types_supported\":[\"public\"],"
                        + "\"id_token_signing_alg_values_supported\":[\"RS256\"],\"token_endpoint_auth_methods_supported\":[\"client_secret_basic\"]}");
            });
            server.createContext("/jwks", exchange -> reply(exchange, 200, "{\"keys\":[" + key.toPublicJWK().toJSONString() + "]}"));
            server.createContext("/authorize", exchange -> {
                var params = parameters(exchange.getRequestURI().getRawQuery());
                if (!"S256".equals(params.get("code_challenge_method")) || !params.containsKey("nonce") || !params.containsKey("state")) {
                    reply(exchange, 400, "{}"); return;
                }
                String code = UUID.randomUUID().toString();
                codes.put(code, params);
                exchange.getResponseHeaders().add("Location", params.get("redirect_uri") + "?code=" + code + "&state=" + params.get("state"));
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            });
            server.createContext("/token", exchange -> {
                var params = parameters(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                var request = codes.remove(params.get("code"));
                try {
                    if (request == null || params.get("code_verifier") == null) { reply(exchange, 400, "{}"); return; }
                    String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                            .digest(params.get("code_verifier").getBytes(StandardCharsets.US_ASCII)));
                    verifiedPkce = challenge.equals(request.get("code_challenge"));
                    if (!verifiedPkce || !Objects.equals(params.get("redirect_uri"), request.get("redirect_uri"))) { reply(exchange, 400, "{}"); return; }
                    String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("mock-client:mock-platform-secret".getBytes(StandardCharsets.UTF_8));
                    if (!expectedAuth.equals(exchange.getRequestHeaders().getFirst("Authorization"))) { reply(exchange, 401, "{}"); return; }
                    var now = Instant.now();
                    var claims = new JWTClaimsSet.Builder().issuer(mode == TokenMode.WRONG_ISSUER ? "https://wrong.invalid" : issuer())
                            .subject(subject).audience(mode == TokenMode.WRONG_AUDIENCE ? "wrong-client" : "mock-client")
                            .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                            .claim("nonce", mode == TokenMode.WRONG_NONCE ? "wrong-nonce" : request.get("nonce")).build();
                    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("mock-key").build(), claims);
                    jwt.sign(new RSASSASigner(mode == TokenMode.WRONG_SIGNATURE ? otherKey : key));
                    issuedTokens.add(jwt.serialize());
                    reply(exchange, 200, "{\"access_token\":\"mock-access-canary\",\"token_type\":\"Bearer\",\"expires_in\":300,\"id_token\":\"" + jwt.serialize() + "\"}");
                } catch (Exception exception) { throw new IllegalStateException("MOCK_PROVIDER_FAILED"); }
            });
            server.start();
        } catch (Exception exception) { throw new IllegalStateException("MOCK_PROVIDER_START_FAILED"); }
    }
    String issuer() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    static Map<String, String> parameters(String encoded) {
        var values = new LinkedHashMap<String, String>();
        for (String part : encoded.split("&")) {
            var entry = part.split("=", 2);
            values.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8), URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
        }
        return values;
    }
    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
    public void close() { server.stop(0); }
}
