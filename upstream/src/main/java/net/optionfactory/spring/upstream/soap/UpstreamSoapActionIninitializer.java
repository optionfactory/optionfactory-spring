package net.optionfactory.spring.upstream.soap;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;

public class UpstreamSoapActionIninitializer implements UpstreamHttpRequestInitializer {

    private final Map<Method, String> soapActions = new ConcurrentHashMap<>();

    @Override
    public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
        for (Method m : k.getMethods()) {
            if (m.isSynthetic() || m.isBridge() || m.isDefault()) {
                continue;
            }
            final Upstream.SoapAction ann = m.getAnnotation(Upstream.SoapAction.class);
            if (ann != null) {
                soapActions.put(m, ann.value());
            }
        }
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        final var soapAction = soapActions.get(invocation.method());
        if (soapAction != null) {
            request.getHeaders().set("SOAPAction", String.format("\"%s\"", soapAction));
        }
    }

}
