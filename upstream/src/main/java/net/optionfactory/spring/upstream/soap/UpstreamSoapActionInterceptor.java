/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.optionfactory.spring.upstream.soap;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

public class UpstreamSoapActionInterceptor implements UpstreamHttpInterceptor {

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
