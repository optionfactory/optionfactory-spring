package net.optionfactory.spring.upstream.digest;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;

public class DigestAuthTest {

    @Test
    public void canGenerateSameResultsAsRfc2617() {
        final DigestAuth da = new DigestAuth("Mufasa", "Circle Of Life", new AtomicInteger(0), () -> 172953915);
        String got = da.authHeader("GET", "/dir/index.html", "Digest asd=123,realm=\"testrealm@host.com\",qop=\"auth,auth-int\",nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\",opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"");
        Assert.assertEquals("Digest username=\"Mufasa\", realm=\"testrealm@host.com\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", uri=\"/dir/index.html\", qop=auth, nc=00000001, cnonce=\"0a4f113b\", response=\"6629fae49393a05397450978507c4ef1\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"", got);
    }    
}
