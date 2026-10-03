package net.optionfactory.spring.upstream.soap;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.StringExpression;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import org.springframework.http.client.ClientHttpRequest;

/// Sets the content type and the SOAP action of every request of a SOAP client.
///
/// The action is the endpoint's [Upstream.SoapAction], evaluated (a template by default) with
/// `#upstream`, `#endpoint`, `#invocation`, `#args` and the method parameters by name; how it is
/// sent depends on the protocol, see [Protocol#headers(Optional)]. Endpoints without the
/// annotation get the content type only. Installed by the `soap` methods of `UpstreamBuilder`.
public class UpstreamSoapActionInitializer implements UpstreamHttpRequestInitializer {

    private final Map<Method, StringExpression> soapActions = new ConcurrentHashMap<>();
    private final Protocol protocol;

    /// @param protocol the SOAP version spoken
    public UpstreamSoapActionInitializer(Protocol protocol) {
        this.protocol = protocol;
    }

    /// Compiles the [Upstream.SoapAction] of every endpoint, read from the method or from the
    /// declaration it overrides (see [Annotations#onMethod]).
    ///
    /// @param k the client interface
    /// @param expressions the parser of the action expressions
    /// @param endpoints the endpoints of the client, by method
    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            Annotations.onMethod(endpoint.method(), Upstream.SoapAction.class)
                    .ifPresent(ann -> soapActions.put(endpoint.method(), expressions.string(ann.value(), ann.valueType())));
        }
    }

    /// Adds the protocol headers to the request.
    ///
    /// @param invocation the invocation in progress
    /// @param request the request to initialize
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
