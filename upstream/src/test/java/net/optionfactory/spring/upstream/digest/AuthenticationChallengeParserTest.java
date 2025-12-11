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
        Assertions.assertEquals("digest", challenge.scheme);
        Assertions.assertEquals("testrealm@host.com", challenge.params.get("realm"));
        Assertions.assertEquals("auth,auth-int", challenge.params.get("qop"));
        Assertions.assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", challenge.params.get("nonce"));
        Assertions.assertEquals("5ccc069c403ebaf9f0171e9517f40e41", challenge.params.get("opaque"));
    }

    @Test
    public void canParseExampleFromRfc2617WithSpaces() {
        final AuthenticationChallengeParser parser = new AuthenticationChallengeParser();
        final AuthenticationChallenge challenge = parser.parse("Digest realm=\"testrealm@host.com\",  qop=\"auth,auth-int\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"  ");
        Assertions.assertEquals("digest", challenge.scheme);
        Assertions.assertEquals("testrealm@host.com", challenge.params.get("realm"));
        Assertions.assertEquals("auth,auth-int", challenge.params.get("qop"));
        Assertions.assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", challenge.params.get("nonce"));
        Assertions.assertEquals("5ccc069c403ebaf9f0171e9517f40e41", challenge.params.get("opaque"));
    }

    @Test
    public void canParseExampleFromRfc2617Unquoted() {
        final AuthenticationChallengeParser parser = new AuthenticationChallengeParser();
        final AuthenticationChallenge challenge = parser.parse("Digest realm=testrealm@host.com,qop=auth,nonce=dcd98b7102dd2f0e8b11d0f600bfb0c093,opaque=5ccc069c403ebaf9f0171e9517f40e41");
        Assertions.assertEquals("digest", challenge.scheme);
        Assertions.assertEquals("testrealm@host.com", challenge.params.get("realm"));
        Assertions.assertEquals("auth", challenge.params.get("qop"));
        Assertions.assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", challenge.params.get("nonce"));
        Assertions.assertEquals("5ccc069c403ebaf9f0171e9517f40e41", challenge.params.get("opaque"));
    }
}
