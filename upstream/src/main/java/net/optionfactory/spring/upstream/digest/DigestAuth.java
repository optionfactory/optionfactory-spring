package net.optionfactory.spring.upstream.digest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.codec.binary.Hex;

public class DigestAuth {
    
    private final String clientId;
    private final String clientSecret;
    private final Supplier<Integer> clientNonceFactory;

    public DigestAuth(String clientId, String clientSecret, Supplier<Integer> clientNonceFactory) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clientNonceFactory = clientNonceFactory;
    }

    public static DigestAuth fromCredentials(String clientId, String clientSecret) {
        final SecureRandom sr = new SecureRandom();
        return new DigestAuth(clientId, clientSecret, sr::nextInt);
    }

    public String authHeader(String method, String uriPath, String serverChallenge) {
        final AuthenticationChallengeParser.AuthenticationChallenge challenge = new AuthenticationChallengeParser().parse(serverChallenge);
        if (!"digest".equalsIgnoreCase(challenge.scheme)) {
            throw new IllegalStateException("Not a Digest challenge: " + serverChallenge);
        }
        final String serverRealm = challenge.params.get("realm");
        final String serverNonce = challenge.params.get("nonce");
        final String serverOpaque = challenge.params.get("opaque");
        final String nc = "00000001";
        final String clientNonce = String.format("%08x", clientNonceFactory.get());
        final String ha1 = md5LowercaseHex(String.format("%s:%s:%s", clientId, serverRealm, clientSecret));
        final String ha2 = md5LowercaseHex(String.format("%s:%s", method, uriPath));
        final String response = md5LowercaseHex(String.format("%s:%s:%s:%s:%s:%s", ha1, serverNonce, nc, clientNonce, "auth", ha2));
        final Map<String, String> digestParams = new LinkedHashMap<>();
        digestParams.put("username", quoted(clientId));
        digestParams.put("realm", quoted(serverRealm));
        digestParams.put("nonce", quoted(serverNonce));
        digestParams.put("uri", quoted(uriPath));
        digestParams.put("qop", "auth");
        digestParams.put("nc", nc);
        digestParams.put("cnonce", quoted(clientNonce));
        digestParams.put("response", quoted(response));
        digestParams.put("opaque", quoted(serverOpaque));
        final String digestParamsValue = digestParams.entrySet().stream().map((e) -> String.format("%s=%s", e.getKey(), e.getValue())).collect(Collectors.joining(", "));
        return String.format("Digest %s", digestParamsValue);
    }

    private static String quoted(String v) {
        return String.format("\"%s\"", v.replace("\"", "\\\""));
    }

    private static String md5LowercaseHex(String v) {
        try {
            final byte[] md5 = MessageDigest.getInstance("MD5").digest(v.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(md5, true);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
    
}
