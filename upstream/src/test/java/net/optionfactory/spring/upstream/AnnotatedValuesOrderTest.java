package net.optionfactory.spring.upstream;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.optionfactory.spring.upstream.auth.digest.DigestAuth;
import net.optionfactory.spring.upstream.auth.digest.DigestAuthClient;
import net.optionfactory.spring.upstream.auth.digest.DigestAuthenticator;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.GetExchange;
import tools.jackson.databind.json.JsonMapper;

/// The annotated headers, cookies and query params are applied before the application's
/// interceptors, so that those see, and can sign, the request as it is sent.
public class AnnotatedValuesOrderTest {

    public interface Client {

        @GetExchange("/endpoint")
        @Upstream.Endpoint("endpoint")
        @Upstream.QueryParam(key = "q", value = "v", valueType = Expressions.Type.STATIC)
        @Upstream.Header(key = "X-Annotated", value = "h", valueType = Expressions.Type.STATIC)
        Map<String, String> call();
    }

    private static UpstreamBuilder<Client> builder() {
        return UpstreamBuilder.create(Client.class)
                .requestFactoryMock(c -> c.response(MediaType.APPLICATION_JSON, "{}"))
                .json(JsonMapper.builder().build())
                .baseUri("http://example.com");
    }

    @Test
    public void applicationInterceptorsSeeTheAnnotatedValues() {
        final var seen = new ArrayList<String>();
        builder()
                .interceptor((invocation, request, execution) -> {
                    seen.add(request.uri().toString());
                    seen.add(request.headers().getFirst("X-Annotated"));
                    return execution.execute(invocation, request);
                })
                .build()
                .call();
        Assertions.assertEquals(List.of("http://example.com/endpoint?q=v", "h"), seen, "an application interceptor sees the annotated query param and header");
    }

    @Test
    public void theDigestCoversTheAnnotatedQueryParams() {
        final var digested = new ArrayList<URI>();
        final var digests = new DigestAuthClient() {
            @Override
            public HttpHeaders challenge(URI uri) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String authenticate(DigestAuth da, HttpMethod method, URI uri) {
                digested.add(uri);
                return "Digest test";
            }
        };
        builder()
                .interceptor(new DigestAuthenticator("id", "secret", digests))
                .build()
                .call();
        Assertions.assertEquals(List.of(URI.create("http://example.com/endpoint?q=v")), digested, "the digest is computed over the uri as it is sent, annotated query params included");
    }
}
