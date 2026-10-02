package net.optionfactory.spring.upstream.digest;

import net.optionfactory.spring.upstream.auth.digest.DigestAuth;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DigestAuthTest {

    @Test
    public void canGenerateSameResultsAsRfc2617() {
        final DigestAuth da = new DigestAuth("Mufasa", "Circle Of Life", () -> 172953915);
        String got = da.authHeader("GET", "/dir/index.html", "Digest asd=123,realm=\"testrealm@host.com\",qop=\"auth,auth-int\",nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\",opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"");
        Assertions.assertEquals("Digest username=\"Mufasa\", realm=\"testrealm@host.com\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", uri=\"/dir/index.html\", qop=auth, nc=00000001, cnonce=\"0a4f113b\", response=\"6629fae49393a05397450978507c4ef1\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"", got, "the response must match the RFC 2617 worked example, ignoring unknown parameters");
    }    

    @Test
    public void nonDigestChallengeIsRejected() {
        final var da = new DigestAuth("Mufasa", "Circle Of Life", () -> 1);
        Assertions.assertThrows(IllegalStateException.class, () -> da.authHeader("GET", "/", "Basic realm=\"r\""), "a non digest challenge must be rejected");
    }

    @Test
    public void challengeWithoutRealmIsRejected() {
        final var da = new DigestAuth("Mufasa", "Circle Of Life", () -> 1);
        final var ex = Assertions.assertThrows(IllegalStateException.class, () -> da.authHeader("GET", "/", "Digest nonce=\"n\""), "a challenge without realm must be rejected with an IllegalStateException");
        Assertions.assertTrue(ex.getMessage().contains("realm"), "the rejection must name the missing realm: " + ex.getMessage());
    }

    @Test
    public void challengeWithoutNonceIsRejected() {
        final var da = new DigestAuth("Mufasa", "Circle Of Life", () -> 1);
        final var ex = Assertions.assertThrows(IllegalStateException.class, () -> da.authHeader("GET", "/", "Digest realm=\"r\""), "a challenge without nonce must be rejected with an IllegalStateException");
        Assertions.assertTrue(ex.getMessage().contains("nonce"), "the rejection must name the missing nonce: " + ex.getMessage());
    }

    @Test
    public void opaqueIsOmittedWhenTheServerSendsNone() {
        final var da = new DigestAuth("Mufasa", "Circle Of Life", () -> 1);
        final var got = da.authHeader("GET", "/", "Digest realm=\"r\", nonce=\"n\"");
        Assertions.assertFalse(got.contains("opaque"), "no opaque must be echoed when the server sent none");
        Assertions.assertTrue(got.contains("cnonce=\"00000001\""), "the client nonce must be rendered as 8 hex digits");
    }

    @Test
    public void quotesInValuesAreEscaped() {
        final var da = new DigestAuth("Mu\"fasa", "Circle Of Life", () -> 1);
        final var got = da.authHeader("GET", "/", "Digest realm=\"r\", nonce=\"n\"");
        Assertions.assertTrue(got.startsWith("Digest username=\"Mu\\\"fasa\""), "a quote in the username must be escaped");
    }

    @Test
    public void clientNonceIsDrawnForEveryHeader() {
        final var nonces = new java.util.concurrent.atomic.AtomicInteger();
        final var da = new DigestAuth("Mufasa", "Circle Of Life", nonces::incrementAndGet);
        final var first = da.authHeader("GET", "/", "Digest realm=\"r\", nonce=\"n\"");
        final var second = da.authHeader("GET", "/", "Digest realm=\"r\", nonce=\"n\"");
        Assertions.assertNotEquals(first, second, "every header must use a fresh client nonce");
        Assertions.assertEquals(2, nonces.get(), "one client nonce must be drawn per header");
    }
}
