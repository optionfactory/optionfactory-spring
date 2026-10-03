package net.optionfactory.spring.authentication.tokens;

import java.util.HexFormat;
import net.optionfactory.spring.authentication.tokens.jwt.ClaimsPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// A strict static token rejects every other token on its header and scheme, so whatever would
/// process a token there after it can never authenticate anything: such a configuration is
/// rejected when the processors are assembled, i.e. when the security chain is built.
public class HttpHeaderAuthenticationShadowingTest {

    private static final byte[] HS256_KEY = HexFormat.of().parseHex("7465737400000000000000000000000000000000000000000000000000000000");

    @Test
    public void aJwtOnTheHeaderOfAStrictTokenIsRejected() {
        final var configurer = HttpHeaderAuthentication.configurer()
                .bearerStrict("STATIC", "static", "ROLE_STATIC")
                .jws(ClaimsPolicy.permissive(), jc -> {
                    jc.verify(HS256_KEY);
                    jc.principal("jwt");
                });
        final var thrown = Assertions.assertThrows(IllegalStateException.class, configurer::tokenProcessors, "a jws on the header and scheme of a strict token could never authenticate, and is rejected");
        Assertions.assertTrue(thrown.getMessage().contains("AUTHORIZATION") || thrown.getMessage().contains("Authorization"), "the failure names the shadowed header, got: " + thrown.getMessage());
    }

    @Test
    public void aStaticTokenAfterAStrictOneOnTheSameHeaderIsRejected() {
        final var configurer = HttpHeaderAuthentication.configurer()
                .bearerStrict("FIRST", "first", "ROLE_FIRST")
                .bearer("SECOND", "second", "ROLE_SECOND");
        Assertions.assertThrows(IllegalStateException.class, configurer::tokenProcessors, "a static token after a strict one on the same header and scheme could never authenticate, and is rejected");
    }

    @Test
    public void aLaxTokenBeforeAStrictOneOnTheSameHeaderIsAccepted() {
        final var configurer = HttpHeaderAuthentication.configurer()
                .bearer("FIRST", "first", "ROLE_FIRST")
                .bearerStrict("SECOND", "second", "ROLE_SECOND");
        Assertions.assertEquals(2, configurer.tokenProcessors().size(), "a lax token passes other tokens on, so a strict one after it still sees its own");
    }

    @Test
    public void aStrictTokenOnAnotherHeaderShadowsNothing() {
        final var configurer = HttpHeaderAuthentication.configurer()
                .tokenStrict("X-Service-Token", "", "STATIC", "static", "ROLE_STATIC")
                .bearer("BEARER", "bearer", "ROLE_BEARER")
                .jws(ClaimsPolicy.permissive(), jc -> {
                    jc.verify(HS256_KEY);
                    jc.principal("jwt");
                });
        Assertions.assertEquals(3, configurer.tokenProcessors().size(), "a strict token only shadows its own header and scheme");
    }

    @Test
    public void aCustomProcessorAfterAStrictTokenOnTheSameHeaderIsRejected() {
        final var configurer = HttpHeaderAuthentication.configurer()
                .bearerStrict("STATIC", "static", "ROLE_STATIC")
                .processor("Authorization", "Bearer", (hs, token) -> null);
        Assertions.assertThrows(IllegalStateException.class, configurer::tokenProcessors, "a custom processor on the header and scheme of a strict token could never authenticate, and is rejected");
    }
}
