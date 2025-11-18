package net.optionfactory.spring.upstream.mocks.rendering;

import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.mocks.rendering.MocksReferencingBeansTest.ClientConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import tools.jackson.databind.json.JsonMapper;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {ClientConfig.class})
public class MocksReferencingBeansTest {

    public record Info(String help) {

    }

    @Configuration
    public static class ClientConfig {

        @Bean
        public Info info() {
            return new Info("help");
        }

        @Bean
        public ExampleMockClient client(ConfigurableApplicationContext ac) {
            final JsonMapper mapper = new JsonMapper();

            return UpstreamBuilder.create(ExampleMockClient.class)
                    .requestFactoryMock(c -> c.jsont().thymeleaf())
                    .json(mapper)
                    .applicationContext(ac)
                    .baseUri("https://hub.dummyapis.com/statuscode/")
                    .build();

        }
    }

    @Upstream("dummy-apis")
    @Upstream.Logging
    @Upstream.AlertOnRemotingError
    @Upstream.AlertOnResponse(Upstream.AlertOnResponse.STATUS_IS_ERROR)
    @Upstream.Mock.DefaultContentType("application/json")
    public interface ExampleMockClient {

        @GetExchange("/200")
        @Upstream.Endpoint("ok-endpoint")
        @Upstream.Mock("beans.tpl.json")
        Map<String, String> jsont(@RequestParam String id);

        @GetExchange("/200")
        @Upstream.Endpoint("ok-endpoint")
        @Upstream.Mock("beans.json.template")
        Map<String, String> thymeleaf(@RequestParam String id);

    }

    @Autowired
    private ExampleMockClient client;

    @Test
    public void canReferenceApplicationContextBeansInJsonTemplates() throws Exception {
        final var got = client.jsont("1");

        Assert.assertEquals(Map.of("key", "help"), got);
    }

    @Test
    public void canReferenceApplicationContextBeansInThymeleafTemplates() throws Exception {
        final var got = client.thymeleaf("1");

        Assert.assertEquals(Map.of("key", "help"), got);
    }
}
