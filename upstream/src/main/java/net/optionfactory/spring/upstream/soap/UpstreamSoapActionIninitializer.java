package net.optionfactory.spring.upstream.soap;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.StringExpression;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import org.springframework.http.client.ClientHttpRequest;

public class UpstreamSoapActionIninitializer implements UpstreamHttpRequestInitializer {

    private final Map<Method, StringExpression> soapActions = new ConcurrentHashMap<>();
    private final Protocol protocol;

    public UpstreamSoapActionIninitializer(Protocol protocol) {
        this.protocol = protocol;
    }

    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            final Upstream.SoapAction ann = endpoint.method().getAnnotation(Upstream.SoapAction.class);
            if (ann != null) {
                soapActions.put(endpoint.method(), expressions.string(ann.value(), ann.valueType()));
            }
        }
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        final var maybeAction = Optional.ofNullable(soapActions.get(invocation.endpoint().method()))
                .map(expr -> {
                    final var ctx = invocation.expressions().context(invocation);
                    return expr.evaluate(ctx);
                });
        request.getHeaders().addAll(protocol.headers(maybeAction));
    }

}
