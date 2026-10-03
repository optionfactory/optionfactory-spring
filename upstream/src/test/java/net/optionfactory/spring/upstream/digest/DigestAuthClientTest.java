package net.optionfactory.spring.upstream.digest;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.auth.digest.DigestAuth;
import net.optionfactory.spring.upstream.auth.digest.DigestAuthClient;
import net.optionfactory.spring.upstream.auth.digest.DigestAuthenticator;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public class DigestAuthClientTest {

    private static final String NONCE = "dcd98b7102dd2f0e8b11d0f600bfb0c093";
    private static final String OPAQUE = "5ccc069c403ebaf9f0171e9517f40e41";

    private static final DigestAuthClient CLIENT = UpstreamBuilder.create(DigestAuthClient.class)
            .requestFactoryMock(c -> {
            })
            .build();

    private static String md5(String v) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(v.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    public void authenticateDigestsTheActualMethodAndTheRequestTargetIncludingQuery() {
        final var da = new DigestAuth("Mufasa", "Circle Of Life", () -> 172953915);
        final var got = CLIENT.authenticate(da, HttpMethod.GET, URI.create("http://example.com/dir/page?x=1"));
        final var ha1 = md5("Mufasa:test:Circle Of Life");
        final var ha2 = md5("GET:/dir/page?x=1");
        final var response = md5(String.format("%s:%s:%s:%s:%s:%s", ha1, NONCE, "00000001", "0a4f113b", "auth", ha2));
        Assertions.assertEquals(
                "Digest username=\"Mufasa\", realm=\"test\", nonce=\"%s\", uri=\"/dir/page?x=1\", qop=auth, nc=00000001, cnonce=\"0a4f113b\", response=\"%s\", opaque=\"%s\""
                        .formatted(NONCE, response, OPAQUE),
                got, "the digest must cover the actual method and the path with its query, answering the mocked 401 challenge");
    }

    @Test
    public void authenticatorPassesTheRequestMethodAndUriToTheClient() throws IOException {
        final var seenMethod = new HttpMethod[1];
        final var seenUri = new URI[1];
        final DigestAuthClient stub = new DigestAuthClient() {
            @Override
            public HttpHeaders challenge(URI uri) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String authenticate(DigestAuth da, HttpMethod method, URI uri) {
                seenMethod[0] = method;
                seenUri[0] = uri;
                return "Digest test";
            }
        };
        final var authenticator = new DigestAuthenticator("id", "secret", stub);
        final var request = new RequestContext(Instant.EPOCH, HttpMethod.GET, URI.create("http://example.com/dir/page?x=1"), new HttpHeaders(), Map.of(), new byte[0]);
        final var proceeded = new boolean[1];
        authenticator.intercept(null, request, (invocation, r) -> {
            proceeded[0] = true;
            return null;
        });
        Assertions.assertEquals(HttpMethod.GET, seenMethod[0], "the authenticated request method must be digested");
        Assertions.assertEquals(URI.create("http://example.com/dir/page?x=1"), seenUri[0], "the authenticated request uri must be digested");
        Assertions.assertEquals("Digest test", request.headers().getFirst("Authorization"), "the computed header must be set on the request");
        Assertions.assertTrue(proceeded[0], "the authenticated request must proceed down the chain");
    }

    @Test
    public void pathWithoutQueryIsDigestedAsIs() {
        final var da = new DigestAuth("Mufasa", "Circle Of Life", () -> 172953915);
        final var got = CLIENT.authenticate(da, HttpMethod.PUT, URI.create("http://example.com/dir/page"));
        Assertions.assertTrue(got.contains("uri=\"/dir/page\""), "a uri without query must be digested without a trailing question mark");
        final var response = md5(String.format("%s:%s:%s:%s:%s:%s", md5("Mufasa:test:Circle Of Life"), NONCE, "00000001", "0a4f113b", "auth", md5("PUT:/dir/page")));
        Assertions.assertTrue(got.contains("response=\"%s\"".formatted(response)), "the digest must cover the PUT method");
    }
}
