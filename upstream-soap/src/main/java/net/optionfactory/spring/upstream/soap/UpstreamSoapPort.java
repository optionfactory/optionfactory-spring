package net.optionfactory.spring.upstream.soap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import net.optionfactory.spring.upstream.UpstreamInterceptor;
import net.optionfactory.spring.upstream.UpstreamInterceptor.ErrorContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.ExchangeContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.RequestContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.ResponseContext;
import net.optionfactory.spring.upstream.UpstreamPort;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
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

public class UpstreamSoapPort<CTX> implements UpstreamPort<CTX> {

    private final String upstreamId;
    private final AtomicLong requestCounter;
    private final WebServiceTemplate soap;
    private final List<UpstreamInterceptor<CTX>> interceptors;
    private final ThreadLocal<ExchangeContext<CTX>> callContexts = new ThreadLocal<>();

    public UpstreamSoapPort(String upstreamId, AtomicLong requestCounter, Resource[] schemas, Class<?> packageToScan, SSLConnectionSocketFactory socketFactory, int connectionTimeoutInMillis, List<UpstreamInterceptor<CTX>> interceptors) {
        final var builder = HttpClientBuilder.create();
        builder.setSSLSocketFactory(socketFactory);

        final var client = builder
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(connectionTimeoutInMillis).build())
                .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
                .addInterceptorFirst(new HttpComponentsMessageSender.RemoveSoapHeadersInterceptor())
                .addInterceptorLast((HttpResponse hr, HttpContext hc) -> {
                    final var headers = new HttpHeaders();
                    for (Header header : hr.getAllHeaders()) {
                        headers.add(header.getName(), header.getValue());
                    }
                    final ExchangeContext<CTX> ctx = callContexts.get();
                    ctx.response = new ResponseContext();
                    ctx.response.headers = headers;
                    ctx.response.status = HttpStatus.resolve(hr.getStatusLine().getStatusCode());
                    ctx.response.at = Instant.now();
                    ctx.response.body = null;
                })
                .build();

        final var inner = new WebServiceTemplate();
        final SaajSoapMessageFactory mf = new SaajSoapMessageFactory();
        mf.setSoapVersion(SoapVersion.SOAP_12);
        initBean(mf);
        inner.setMessageFactory(mf);
        inner.setMessageSender(new HttpComponentsMessageSender(client));
        final var ms = new Jaxb2Marshaller();
        ms.setSchemas(schemas);
        ms.setPackagesToScan(packageToScan.getPackageName());
        initBean(ms);
        inner.setMarshaller(ms);
        inner.setUnmarshaller(ms);
        final var validator = new PayloadValidatingInterceptor();
        validator.setSchemas(schemas);
        validator.setValidateRequest(true);
        validator.setValidateResponse(true);
        initBean(validator);
        inner.setInterceptors(new ClientInterceptor[]{
            validator,
            new SoapInterceptors<>(interceptors, callContexts)
        });

        this.upstreamId = upstreamId;
        this.requestCounter = requestCounter;
        this.interceptors = interceptors;
        this.soap = inner;
    }

    private void initBean(InitializingBean b) {
        try {
            b.afterPropertiesSet();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public <T> ResponseEntity<T> exchange(CTX context, String endpointId, RequestEntity<?> requestEntity, Class<T> responseType) {
        return exchange(context, endpointId, requestEntity);
    }

    @Override
    public <T> ResponseEntity<T> exchange(CTX context, String endpointId, RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType) {
        return exchange(context, endpointId, requestEntity);
    }

    private <T> ResponseEntity<T> exchange(CTX context, String endpointId, RequestEntity<?> requestEntity) {
        final ExchangeContext<CTX> ctx = new ExchangeContext<>();
        ctx.prepare = new UpstreamInterceptor.PrepareContext<>();
        ctx.prepare.requestId = requestCounter.incrementAndGet();
        ctx.prepare.ctx = context;
        ctx.prepare.endpointId = endpointId;
        ctx.prepare.entity = requestEntity;
        ctx.prepare.upstreamId = upstreamId;
        callContexts.set(ctx);
        try {
            final var headers = new HttpHeaders();
            headers.addAll(ctx.prepare.entity.getHeaders());
            for (var interceptor : interceptors) {
                final var newHeaders = interceptor.prepare(ctx.prepare);
                if (newHeaders != null) {
                    headers.addAll(newHeaders);
                }
            }
            ctx.prepare.entity = new RequestEntity<>(ctx.prepare.entity.getBody(), headers, ctx.prepare.entity.getMethod(), ctx.prepare.entity.getUrl(), ctx.prepare.entity.getType());
            final var got = soap.marshalSendAndReceive(ctx.prepare.entity.getUrl().toString(), ctx.prepare.entity.getBody(), (WebServiceMessage message) -> {
                final HttpComponentsConnection connection = (HttpComponentsConnection) TransportContextHolder.getTransportContext().getConnection();
                for (Entry<String, List<String>> header : ctx.prepare.entity.getHeaders().entrySet()) {
                    for (String value : header.getValue()) {
                        connection.addRequestHeader(header.getKey(), value);
                    }
                }
            });
            final ResponseEntity<T> response = ResponseEntity.ok().headers(ctx.response.headers).body((T) got);
            for (UpstreamInterceptor<CTX> interceptor : interceptors) {
                interceptor.mappingSuccess(ctx.prepare, ctx.request, ctx.response, response);
            }
            return response;
        } finally {
            callContexts.remove();
        }
    }

    public static class SoapInterceptors<CTX> implements ClientInterceptor {

        private final List<UpstreamInterceptor<CTX>> interceptors;
        private final ThreadLocal<ExchangeContext<CTX>> callContexts;

        public SoapInterceptors(List<UpstreamInterceptor<CTX>> interceptors, ThreadLocal<ExchangeContext<CTX>> callContexts) {
            this.interceptors = interceptors;
            this.callContexts = callContexts;
        }

        @Override
        public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
            final ExchangeContext<CTX> ctx = callContexts.get();
            ctx.request = new RequestContext();
            ctx.request.at = Instant.now();
            ctx.request.body = toResource(messageContext.getRequest());
            ctx.request.headers = ctx.prepare.entity.getHeaders();
            for (var interceptor : interceptors) {
                interceptor.before(ctx.prepare, ctx.request);
            }

            return true;
        }

        @Override
        public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
            final ExchangeContext<CTX> ctx = callContexts.get();
            ctx.response.body = toResource(messageContext.getResponse());
            for (var interceptor : interceptors) {
                interceptor.remotingSuccess(ctx.prepare, ctx.request, ctx.response);
            }
            return true;
        }

        @Override
        public boolean handleFault(MessageContext messageContext) throws WebServiceClientException {
            final ExchangeContext<CTX> ctx = callContexts.get();
            ctx.response.body = toResource(messageContext.getResponse());
            for (var interceptor : interceptors) {
                interceptor.remotingSuccess(ctx.prepare, ctx.request, ctx.response);
            }
            return true;
        }

        @Override
        public void afterCompletion(MessageContext messageContext, Exception ex) throws WebServiceClientException {
            if (ex == null) {
                return;
            }
            final ExchangeContext<CTX> ctx = callContexts.get();
            ctx.error = new ErrorContext();
            ctx.error.at = Instant.now();
            ctx.error.ex = ex;
            for (var interceptor : interceptors) {
                interceptor.remotingError(ctx.prepare, ctx.request, ctx.error);
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
