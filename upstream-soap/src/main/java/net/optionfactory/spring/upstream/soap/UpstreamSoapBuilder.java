package net.optionfactory.spring.upstream.soap;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.SoapHeaderWriter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

public class UpstreamSoapBuilder extends UpstreamBuilder {

    private final Protocol protocol;
    private final JAXBContext context;
    private final SoapHeaderWriter headerWriter;

    public UpstreamSoapBuilder(Protocol protocol, JAXBContext context, SoapHeaderWriter headerWriter) {
        this.protocol = protocol;
        this.context = context;
        this.headerWriter = headerWriter;
        if (protocol == Protocol.SOAP_1_1) {
            this.interceptors.add(new UpstreamSoapActionInterceptor());
        }
    }

    public static UpstreamSoapBuilder create(Protocol protocol, SoapHeaderWriter headerWriter, Class<?> clazz, Class<?>... more) {
        final var contextPaths = Stream
                .concat(Stream.of(clazz), Stream.of(more))
                .map(k -> k.getPackageName())
                .collect(Collectors.joining(":"));
        try {
            return new UpstreamSoapBuilder(protocol, JAXBContext.newInstance(contextPaths), headerWriter);
        } catch (JAXBException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static UpstreamSoapBuilder create(Protocol protocol, Class<?> clazz, Class<?>... more) {
        return create(protocol, null, clazz, more);
    }

    @Override
    protected void configureRestClient(RestClient.Builder rcb) {
        rcb.messageConverters(c -> {
            c.clear();
            c.add(new SoapMessageHttpMessageConverter(protocol));
            c.add(new SoapJaxbHttpMessageConverter(protocol, context, headerWriter));
        });
    }

    public static class UpstreamSoapActionInterceptor implements UpstreamHttpInterceptor {

        private final Map<Method, String> soapActions = new ConcurrentHashMap<>();

        @Override
        public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
            for (Method m : k.getDeclaredMethods()) {
                final UpstreamSoapAction ann = AnnotationUtils.findAnnotation(m, UpstreamSoapAction.class);
                if (ann != null) {
                    soapActions.put(m, ann.value());
                }
            }
        }

        @Override
        public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            final var soapAction = soapActions.get(ctx.method());
            if (soapAction != null) {
                request.getHeaders().set("SOAPAction", soapAction);
            }
            return execution.execute(request, body);
        }
    }

}
