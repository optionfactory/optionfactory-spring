package net.optionfactory.spring.upstream.mocks;

import net.optionfactory.spring.upstream.UpstreamBuilder.RequestFactoryConfigurer;
import org.springframework.http.client.ClientHttpResponse;

public class MocksCustomizer {

    private UpstreamHttpResponseFactory responseFactory = new MockResourcesUpstreamHttpResponseFactory();

    public MocksCustomizer responseFactory(UpstreamHttpResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
        return this;
    }

    public MocksCustomizer response(ClientHttpResponse o) {
        this.responseFactory = (invocation, uri, method, headers) -> o;
        return this;
    }

    public RequestFactoryConfigurer toRequestFactoryConfigurer() {
        return (scopeHandler, klass, expressions, endpoints) -> {
            final var hrf = new MockUpstreamRequestFactory(responseFactory);
            hrf.preprocess(klass, expressions, endpoints);
            return scopeHandler.adapt(hrf);
        };
    }
}
