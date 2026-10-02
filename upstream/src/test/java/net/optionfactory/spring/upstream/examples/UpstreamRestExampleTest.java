package net.optionfactory.spring.upstream.examples;

import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import java.util.Optional;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.auth.OauthClient;
import net.optionfactory.spring.upstream.auth.OauthClientCredentialsAuthenticator;
import net.optionfactory.spring.upstream.examples.UpstreamRestExampleTest.ClientConfig;
import net.optionfactory.spring.upstream.hc5.HcSocketStrategies;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitConfig(ClientConfig.class)
@TestPropertySource(properties = {
    "myclient.type=mock",
    "myclient.client.id=client-id",
    "myclient.client.secret=client-secret"
})
public class UpstreamRestExampleTest {

    @Configuration
    public static class ClientConfig {

        /// Builds the client the way an application would: mocked when `myclient.type` is `mock`,
        /// backed by Apache HttpComponents otherwise.
        ///
        /// - the mock request factory serves the `@Upstream.Mock` resources, here
        ///   `src/test/resources/net/optionfactory/spring/upstream/examples/ok.json`;
        /// - the HttpComponents factory is where TLS, retries and timeouts are customized;
        /// - initializers (here the oauth client credentials authenticator) and interceptors are
        ///   registered on the builder;
        /// - `json` configures the converters for a JSON/HTTP api;
        /// - `observations` is optional monitoring;
        /// - `expressions` exposes the bean factory to the annotations' SpEL expressions;
        /// - `publisher` is where alert events are published.
        @Bean
        public ExampleRestClient exampleRestClient(
                @Value("${myclient.type}") String type,
                @Value("${myclient.client.id}") String clientId,
                @Value("${myclient.client.secret}") String clientSecret,
                Optional<ObservationRegistry> observations,
                ConfigurableApplicationContext ac
        ) {
            final boolean isMock = "mock".equals(type);

            final JsonMapper mapper = new JsonMapper();

            final var oauthClient = UpstreamBuilder.named(OauthClient.class, "example-auth")
                    .requestFactoryMockIf(isMock, c -> {
                    })
                    .requestFactoryHttpComponentsIf(!isMock, c -> {
                        c.tlsSocketStrategy(HcSocketStrategies.system());
                    })
                    .json(mapper)
                    .observations(observations.orElse(null))
                    .expressions(ac)
                    .publisher(ac)
                    .baseUri("https://hub.dummyapis.com/auth/")
                    .build();

            return UpstreamBuilder
                    .create(ExampleRestClient.class)
                    .requestFactoryMockIf(isMock, c -> {
                    })
                    .requestFactoryHttpComponentsIf(!isMock, c -> {
                        c.disableAutomaticRetries();
                    })
                    .initializer(OauthClientCredentialsAuthenticator.builder(oauthClient).clientId(clientId).clientSecret(clientSecret).build())
                    .json(mapper)
                    .observations(observations.orElse(null))
                    .expressions(ac)
                    .publisher(ac)
                    .baseUri("https://hub.dummyapis.com/statuscode/")
                    .build();

        }
    }

    @Upstream("dummy-apis")
    @Upstream.Logging
    @Upstream.AlertOnRemotingError
    @Upstream.AlertOnResponse(Upstream.AlertOnResponse.STATUS_IS_ERROR)
    @Upstream.Mock.DefaultContentType("application/json")
    public interface ExampleRestClient {

        /// Mock resources are tried in order: `ok-1.json` does not exist, so `ok.json` is served.
        ///
        /// @param id the identifier
        /// @return the response
        @GetExchange("/200")
        @Upstream.Endpoint("ok-endpoint")
        @Upstream.Mock("ok-#{#id}.json")
        @Upstream.Mock("ok.json")
        Map<String, String> ok(@RequestParam String id);

    }

    @Autowired
    private ExampleRestClient client;

    @Test
    public void canUseClientConfiguredWithMocks() throws Exception {
        final var got = client.ok("1");

        Assertions.assertEquals(Map.of("mocked", "response"), got, "the first existing mock resource must be served, through the mocked oauth authentication");
    }
}
