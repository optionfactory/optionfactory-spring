package net.optionfactory.spring.upstream.soap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Map.Entry;
import net.optionfactory.spring.upstream.UpstreamInterceptor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.client.support.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpComponentsConnection;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

public class UpstreamSoapPort<CONTEXT> {

    private final String upstreamId;
    private final WebServiceTemplate soap;
    private final List<UpstreamInterceptor> interceptors;
    private final ThreadLocal<SoapCallContext> callContexts = new ThreadLocal<>();

    public UpstreamSoapPort(String upstreamId, Resource[] schemas, Class<?> packageToScan, SSLConnectionSocketFactory socketFactory, int connectionTimeoutInMillis, List<UpstreamInterceptor> interceptors) throws Exception {
        final var builder = HttpClientBuilder.create();
        builder.setSSLSocketFactory(socketFactory);

        final var client = builder
                .addInterceptorFirst(new HttpComponentsMessageSender.RemoveSoapHeadersInterceptor())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(connectionTimeoutInMillis).build())
                .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
                .build();

        final var inner = new WebServiceTemplate();
        final SaajSoapMessageFactory mf = new SaajSoapMessageFactory();
        mf.setSoapVersion(SoapVersion.SOAP_12);
        mf.afterPropertiesSet();
        inner.setMessageFactory(mf);
        inner.setMessageSender(new HttpComponentsMessageSender(client));
        final var ms = new Jaxb2Marshaller();
        ms.setSchemas(schemas);
        ms.setPackagesToScan(packageToScan.getPackageName());
        ms.afterPropertiesSet();
        inner.setMarshaller(ms);
        inner.setUnmarshaller(ms);
        final var validator = new PayloadValidatingInterceptor();
        validator.setSchemas(schemas);
        validator.setValidateRequest(true);
        validator.setValidateResponse(true);
        validator.afterPropertiesSet();
        inner.setInterceptors(new ClientInterceptor[]{
            validator,
            new SoapInterceptors(interceptors, upstreamId, callContexts)
        });

        this.upstreamId = upstreamId;
        this.interceptors = interceptors;
        this.soap = inner;
    }

    public <T> T exchange(CONTEXT context, RequestEntity<?> requestEntity, Class<T> responseType) {
        final var actualEntity = makeEntity(requestEntity, context);
        var got = soap.marshalSendAndReceive(actualEntity.getUrl().toString(), actualEntity.getBody(), (WebServiceMessage message) -> {
            final HttpComponentsConnection connection = (HttpComponentsConnection) TransportContextHolder.getTransportContext().getConnection();
            for (Entry<String, List<String>> header : actualEntity.getHeaders().entrySet()) {
                for (String value : header.getValue()) {
                    connection.addRequestHeader(header.getKey(), value);
                }
            }
            final SoapCallContext ctx = new SoapCallContext();
            ctx.requestUri = actualEntity.getUrl();
            ctx.requestHeaders = actualEntity.getHeaders();
            callContexts.set(ctx);
        });
        return (T) got;
    }

    private RequestEntity<?> makeEntity(RequestEntity<?> requestEntity, CONTEXT context) {
        final var headers = new HttpHeaders();
        headers.addAll(requestEntity.getHeaders());
        for (var interceptor : interceptors) {
            final var newHeaders = interceptor.prepare(upstreamId, context, requestEntity);
            if (newHeaders != null) {
                headers.addAll(newHeaders);
            }
        }
        return new RequestEntity<>(requestEntity.getBody(), headers, requestEntity.getMethod(), requestEntity.getUrl(), requestEntity.getType());
    }
    
    public static class SoapCallContext {
        public HttpHeaders requestHeaders;
        public Resource requestBody;
        public URI requestUri;
        
    }

    public static class SoapInterceptors implements ClientInterceptor {

        private final List<UpstreamInterceptor> interceptors;
        private final String upstreamId;
	private final ThreadLocal<SoapCallContext> callContexts;
        private final HttpHeaders fakeResponseHeaders;
        

        public SoapInterceptors(List<UpstreamInterceptor> interceptors, String upstreamId, ThreadLocal<SoapCallContext> callContexts) {
            this.interceptors = interceptors;
            this.upstreamId = upstreamId;
            this.callContexts = callContexts;
            this.fakeResponseHeaders = new HttpHeaders();
            this.fakeResponseHeaders.setContentType(MediaType.APPLICATION_XML);
        }

        @Override
        public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
            final SoapCallContext ctx = callContexts.get();
            ctx.requestBody = toResource(messageContext.getRequest());
            for (var interceptor : interceptors) {
                interceptor.before(upstreamId, ctx.requestHeaders, ctx.requestUri, ctx.requestBody);
            }

            return true;
        }

        @Override
        public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
            final SoapCallContext ctx = callContexts.get();
            final Resource responseBody = toResource(messageContext.getResponse());
            for (var interceptor : interceptors) {
                interceptor.after(upstreamId, ctx.requestHeaders, ctx.requestUri, ctx.requestBody, HttpStatus.OK, fakeResponseHeaders, responseBody);
            }
            return true;
        }

        @Override
        public boolean handleFault(MessageContext messageContext) throws WebServiceClientException {
            final SoapCallContext ctx = callContexts.get();
            final Resource faultBody = toResource(messageContext.getResponse());
            for (var interceptor : interceptors) {
                interceptor.after(upstreamId, ctx.requestHeaders, ctx.requestUri, ctx.requestBody, HttpStatus.INTERNAL_SERVER_ERROR, fakeResponseHeaders, faultBody);
            }
            return true;
        }

        @Override
        public void afterCompletion(MessageContext messageContext, Exception ex) throws WebServiceClientException {
            if(ex == null){
                return;
            }
            final SoapCallContext ctx = callContexts.get();
            for (var interceptor : interceptors) {
                interceptor.error(upstreamId, ctx.requestHeaders, ctx.requestUri, ctx.requestBody, ex);
            }            
        }

        private static Resource toResource(WebServiceMessage message) {
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                message.writeTo(buffer);
                return new ByteArrayResource(buffer.toByteArray());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }
}
