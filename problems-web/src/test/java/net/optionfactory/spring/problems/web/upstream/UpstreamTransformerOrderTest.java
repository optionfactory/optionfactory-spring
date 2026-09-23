package net.optionfactory.spring.problems.web.upstream;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.optionfactory.spring.problems.web.FailureTransformer;
import net.optionfactory.spring.problems.web.RestExceptionResolver;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import net.optionfactory.spring.upstream.errors.RestClientUpstreamException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.json.JsonMapper;

public class UpstreamTransformerOrderTest {

    @ResponseBody
    @UpstreamProblems.Forward(target = HttpStatus.BAD_REQUEST)
    public void forwarding() {

    }

    @Test
    public void anApplicationTransformerSeesTheForwardedProblems() throws Exception {
        final var seen = new ArrayList<String>();
        final FailureTransformer recording = (saps, request, response, handler, ex) -> {
            saps.problems().forEach(p -> seen.add(p.type));
            return saps;
        };
        final var er = RestExceptionResolver.builder().withTransformer(recording).build(new JsonMapper());
        final var converters = new MessageConverters(HttpMessageConverters.forClient().registerDefaults().withJsonConverter(new JacksonJsonHttpMessageConverter()).build());
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/failures+json"));
        final var ex = new RestClientUpstreamException(converters, "upstream", "endpoint", "reason", HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase(), headers,
                "[{\"type\": \"FIELD_ERROR\", \"context\": \"field\", \"reason\": \"must not be null\"}]".getBytes(StandardCharsets.UTF_8));
        final var handler = new HandlerMethod(new UpstreamTransformerOrderTest(), UpstreamTransformerOrderTest.class.getMethod("forwarding"));

        er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), handler, ex);

        Assertions.assertEquals(List.of("FIELD_ERROR"), seen);
    }
}
