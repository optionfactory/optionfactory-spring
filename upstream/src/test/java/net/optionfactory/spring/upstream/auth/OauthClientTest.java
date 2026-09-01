package net.optionfactory.spring.upstream.auth;

import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class OauthClientTest {

    private final OauthClient client = UpstreamBuilder.create(OauthClient.class)
            .requestFactoryMock(c -> {
            })
            .json(JsonMapper.builder().build())
            .baseUri("https://auth.example.com/token")
            .build();

    @Test
    public void jwtBearerExchangesAssertionForAccessToken() {
        final var got = client.jwtBearer("assertion-value");
        Assertions.assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJtb2NrIn0.GZ6RFN2HiP5-lKMItClOuEqUfVDV-2akPWzHF4u_Q7I", got.path("access_token").asString());
        Assertions.assertEquals("bearer", got.path("token_type").asString());
    }

}
