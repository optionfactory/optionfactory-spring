package net.optionfactory.spring.upstream.digest;

import net.optionfactory.spring.upstream.auth.digest.AuthenticationChallengeParser;
import net.optionfactory.spring.upstream.auth.digest.AuthenticationChallengeParser.AuthenticationChallenge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AuthenticationChallengeParserTest {

    @Test
    public void canParseExampleFromRfc2617() {
        final AuthenticationChallengeParser parser = new AuthenticationChallengeParser();
        final AuthenticationChallenge challenge = parser.parse("Digest realm=\"testrealm@host.com\",qop=\"auth,auth-int\",nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\",opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"");
        Assertions.assertEquals("digest", challenge.scheme(), "the scheme must be lowercased");
        Assertions.assertEquals("testrealm@host.com", challenge.params().get("realm"), "the realm must be unquoted");
        Assertions.assertEquals("auth,auth-int", challenge.params().get("qop"), "a quoted value must keep its commas");
        Assertions.assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", challenge.params().get("nonce"), "the nonce must be unquoted");
        Assertions.assertEquals("5ccc069c403ebaf9f0171e9517f40e41", challenge.params().get("opaque"), "the opaque must be unquoted");
    }

    @Test
    public void canParseExampleFromRfc2617WithSpaces() {
        final AuthenticationChallengeParser parser = new AuthenticationChallengeParser();
        final AuthenticationChallenge challenge = parser.parse("Digest realm=\"testrealm@host.com\",  qop=\"auth,auth-int\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"  ");
        Assertions.assertEquals("digest", challenge.scheme(), "the scheme must be lowercased");
        Assertions.assertEquals("testrealm@host.com", challenge.params().get("realm"), "the realm must be unquoted");
        Assertions.assertEquals("auth,auth-int", challenge.params().get("qop"), "a quoted value must keep its commas");
        Assertions.assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", challenge.params().get("nonce"), "the nonce must be unquoted");
        Assertions.assertEquals("5ccc069c403ebaf9f0171e9517f40e41", challenge.params().get("opaque"), "the opaque must be unquoted");
    }

    @Test
    public void canParseExampleFromRfc2617Unquoted() {
        final AuthenticationChallengeParser parser = new AuthenticationChallengeParser();
        final AuthenticationChallenge challenge = parser.parse("Digest realm=testrealm@host.com,qop=auth,nonce=dcd98b7102dd2f0e8b11d0f600bfb0c093,opaque=5ccc069c403ebaf9f0171e9517f40e41");
        Assertions.assertEquals("digest", challenge.scheme(), "the scheme must be lowercased");
        Assertions.assertEquals("testrealm@host.com", challenge.params().get("realm"), "the realm must be unquoted");
        Assertions.assertEquals("auth", challenge.params().get("qop"), "a quoted value must keep its commas");
        Assertions.assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", challenge.params().get("nonce"), "the nonce must be unquoted");
        Assertions.assertEquals("5ccc069c403ebaf9f0171e9517f40e41", challenge.params().get("opaque"), "the opaque must be unquoted");
    }

    @Test
    public void nullChallengeIsRejected() {
        final var parser = new AuthenticationChallengeParser();
        Assertions.assertThrows(IllegalStateException.class, () -> parser.parse(null), "a missing challenge header must be rejected");
    }

    @Test
    public void blankChallengeIsRejected() {
        final var parser = new AuthenticationChallengeParser();
        Assertions.assertThrows(IllegalStateException.class, () -> parser.parse("   "), "a challenge without a scheme must be rejected");
    }

    @Test
    public void schemeWithoutParametersYieldsNoParameters() {
        final var challenge = new AuthenticationChallengeParser().parse("Basic");
        Assertions.assertEquals("basic", challenge.scheme(), "a bare scheme must be parsed");
        Assertions.assertTrue(challenge.params().isEmpty(), "a bare scheme must carry no parameters");
    }

    @Test
    public void parameterWithoutValueIsMappedToNull() {
        final var challenge = new AuthenticationChallengeParser().parse("Digest stale, realm=r");
        Assertions.assertTrue(challenge.params().containsKey("stale"), "a valueless parameter must be kept");
        Assertions.assertNull(challenge.params().get("stale"), "a valueless parameter must map to null");
        Assertions.assertEquals("r", challenge.params().get("realm"), "parameters after a valueless one must be parsed");
    }

    @Test
    public void escapedQuotesAreKeptVerbatim() {
        final var challenge = new AuthenticationChallengeParser().parse("Digest realm=\"a \\\"quoted\\\" realm, really\", nonce=n");
        Assertions.assertEquals("a \\\"quoted\\\" realm, really", challenge.params().get("realm"), "an escaped quote must neither end the value nor be unescaped");
        Assertions.assertEquals("n", challenge.params().get("nonce"), "parameters after an escaped value must be parsed");
    }
}
