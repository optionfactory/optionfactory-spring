package net.optionfactory.spring.upstream.auth.digest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// Computes the `Authorization` header answering an HTTP digest challenge.
///
/// Only the `MD5` algorithm with `qop=auth` is implemented, whatever the challenge offers, and each
/// challenge is answered once: the nonce count is always `00000001`. The `opaque` of the challenge is
/// echoed when present.
public class DigestAuth {
    
    private final String clientId;
    private final String clientSecret;
    private final Supplier<Integer> clientNonceFactory;

    /// @param clientId the username
    /// @param clientSecret the password
    /// @param clientNonceFactory supplies a client nonce for each header, rendered as 8 hex digits;
    /// fixed values are only suitable for tests
    public DigestAuth(String clientId, String clientSecret, Supplier<Integer> clientNonceFactory) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clientNonceFactory = clientNonceFactory;
    }

    /// @param clientId the username
    /// @param clientSecret the password
    /// @return a `DigestAuth` drawing its client nonces from a `SecureRandom`
    public static DigestAuth fromCredentials(String clientId, String clientSecret) {
        final SecureRandom sr = new SecureRandom();
        return new DigestAuth(clientId, clientSecret, sr::nextInt);
    }

    /// @param method the method of the request to authenticate
    /// @param requestUri the request-target to digest: the raw path, followed by the raw query if any
    /// @param serverChallenge the `WWW-Authenticate` header value
    /// @return the `Authorization` header value
    /// @throws IllegalStateException when the challenge is missing or not a `Digest` one
    /// @throws NullPointerException when the challenge has no `realm` or no `nonce`
    public String authHeader(String method, String requestUri, String serverChallenge) {
        final AuthenticationChallengeParser.AuthenticationChallenge challenge = new AuthenticationChallengeParser().parse(serverChallenge);
        if (!"digest".equalsIgnoreCase(challenge.scheme())) {
            throw new IllegalStateException("Not a Digest challenge: " + serverChallenge);
        }
        final String serverRealm = challenge.params().get("realm");
        final String serverNonce = challenge.params().get("nonce");
        final String serverOpaque = challenge.params().get("opaque");
        final String nc = "00000001";
        final String clientNonce = String.format("%08x", clientNonceFactory.get());
        final String ha1 = md5LowercaseHex(String.format("%s:%s:%s", clientId, serverRealm, clientSecret));
        final String ha2 = md5LowercaseHex(String.format("%s:%s", method, requestUri));
        final String response = md5LowercaseHex(String.format("%s:%s:%s:%s:%s:%s", ha1, serverNonce, nc, clientNonce, "auth", ha2));
        final Map<String, String> digestParams = new LinkedHashMap<>();
        digestParams.put("username", quoted(clientId));
        digestParams.put("realm", quoted(serverRealm));
        digestParams.put("nonce", quoted(serverNonce));
        digestParams.put("uri", quoted(requestUri));
        digestParams.put("qop", "auth");
        digestParams.put("nc", nc);
        digestParams.put("cnonce", quoted(clientNonce));
        digestParams.put("response", quoted(response));
        if (serverOpaque != null) {
            digestParams.put("opaque", quoted(serverOpaque));
        }
        final String digestParamsValue = digestParams.entrySet().stream().map((e) -> String.format("%s=%s", e.getKey(), e.getValue())).collect(Collectors.joining(", "));
        return String.format("Digest %s", digestParamsValue);
    }

    private static String quoted(String v) {
        return String.format("\"%s\"", v.replace("\"", "\\\""));
    }

    private static String md5LowercaseHex(String v) {
        try {
            final byte[] md5 = MessageDigest.getInstance("MD5").digest(v.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md5);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
    
}
