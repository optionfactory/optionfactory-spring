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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import tools.jackson.databind.json.JsonMapper;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {ClientConfig.class})
@TestPropertySource(properties = {
    "myclient.type=mock",
    "myclient.client.id=client-id",
    "myclient.client.secret=client-secret"
})
public class UpstreamRestExampleTest {

    @Configuration
    public static class ClientConfig {

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
                        //mocks behaviour can be customized here
                    })
                    .requestFactoryHttpComponentsIf(!isMock, c -> {
                        //http components configuration can be customized here
                        //e.g: TLS configuration, retries, timeouts                        
                        c.tlsSocketStrategy(HcSocketStrategies.system());
                    })
                    //Configures the client for JSON/HTTP 
                    .json(mapper)
                    .observations(observations.orElse(null))
                    //cofigures the beanFactory so you can reference beans
                    //in annotations' SpEl expressions
                    .expressions(ac)
                    //configures where events (e.g: Alerts) are published
                    .publisher(ac)
                    .baseUri("https://hub.dummyapis.com/auth/")
                    .build();

            return UpstreamBuilder
                    // the interface to be implemented
                    .create(ExampleRestClient.class)
                    .requestFactoryMockIf(isMock, c -> {
                        //mocks behaviour can be customized here
                        //check src/test/resources/net/optionfactory/spring/upstream/examples/ok.json 
                        //for this example
                    })
                    .requestFactoryHttpComponentsIf(!isMock, c -> {
                        //http components configuration can be customized here
                        //e.g: TLS configuration, retries, timeouts
                        c.disableAutomaticRetries();
                    })
                    //initializers and interceptors can be registered 
                    .initializer(new OauthClientCredentialsAuthenticator(clientId, clientSecret, oauthClient))
                    //Configures the client for JSON/HTTP 
                    .json(mapper)
                    //optional monitoring 
                    .observations(observations.orElse(null))
                    //cofigures the beanFactory so you can reference beans
                    //in annotations' SpEl expressions
                    .expressions(ac)
                    //configures where events (e.g: Alerts) are published
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

        @GetExchange("/200")
        @Upstream.Endpoint("ok-endpoint")
        //mock resources are tried in order
        @Upstream.Mock("ok-#{#id}.json")
        @Upstream.Mock("ok.json")
        Map<String, String> ok(@RequestParam String id);

    }

    @Autowired
    private ExampleRestClient client;

    @Test
    public void canUseClientConfiredWithMocks() throws Exception {
        final var got = client.ok("1");

        Assert.assertEquals(Map.of("mocked", "response"), got);
    }
}
