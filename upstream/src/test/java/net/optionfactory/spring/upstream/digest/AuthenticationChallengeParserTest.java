package net.optionfactory.spring.upstream.digest;

import net.optionfactory.spring.upstream.digest.AuthenticationChallengeParser.AuthenticationChallenge;
import org.junit.Assert;
import org.junit.Test;

public class AuthenticationChallengeParserTest {

    @Test
    public void canParseExampleFromRfc2617() {
        final AuthenticationChallengeParser parser = new AuthenticationChallengeParser();
        final AuthenticationChallenge challenge = parser.parse("Digest realm=\"testrealm@host.com\",qop=\"auth,auth-int\",nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\",opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"");
        Assert.assertEquals("digest", challenge.scheme);
        Assert.assertEquals("testrealm@host.com", challenge.params.get("realm"));
        Assert.assertEquals("auth,auth-int", challenge.params.get("qop"));
        Assert.assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", challenge.params.get("nonce"));
        Assert.assertEquals("5ccc069c403ebaf9f0171e9517f40e41", challenge.params.get("opaque"));
    }

    @Test
    public void canParseExampleFromRfc2617WithSpaces() {
        final AuthenticationChallengeParser parser = new AuthenticationChallengeParser();
        final AuthenticationChallenge challenge = parser.parse("Digest realm=\"testrealm@host.com\",  qop=\"auth,auth-int\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"  ");
        Assert.assertEquals("digest", challenge.scheme);
        Assert.assertEquals("testrealm@host.com", challenge.params.get("realm"));
        Assert.assertEquals("auth,auth-int", challenge.params.get("qop"));
        Assert.assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", challenge.params.get("nonce"));
        Assert.assertEquals("5ccc069c403ebaf9f0171e9517f40e41", challenge.params.get("opaque"));
    }

    @Test
    public void canParseExampleFromRfc2617Unquoted() {
        final AuthenticationChallengeParser parser = new AuthenticationChallengeParser();
        final AuthenticationChallenge challenge = parser.parse("Digest realm=testrealm@host.com,qop=auth,nonce=dcd98b7102dd2f0e8b11d0f600bfb0c093,opaque=5ccc069c403ebaf9f0171e9517f40e41");
        Assert.assertEquals("digest", challenge.scheme);
        Assert.assertEquals("testrealm@host.com", challenge.params.get("realm"));
        Assert.assertEquals("auth", challenge.params.get("qop"));
        Assert.assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", challenge.params.get("nonce"));
        Assert.assertEquals("5ccc069c403ebaf9f0171e9517f40e41", challenge.params.get("opaque"));
    }
}
