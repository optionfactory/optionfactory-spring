package net.optionfactory.spring.upstreamlegacy.soap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import net.optionfactory.spring.upstreamlegacy.UpstreamInterceptor;
import net.optionfactory.spring.upstreamlegacy.UpstreamInterceptor.ErrorContext;
import net.optionfactory.spring.upstreamlegacy.UpstreamInterceptor.ExchangeContext;
import net.optionfactory.spring.upstreamlegacy.UpstreamInterceptor.RequestContext;
import net.optionfactory.spring.upstreamlegacy.UpstreamInterceptor.ResponseContext;
import net.optionfactory.spring.upstreamlegacy.UpstreamPort;
import net.optionfactory.spring.upstreamlegacy.counters.UpstreamRequestCounter;
import net.optionfactory.spring.upstreamlegacy.soap.UpstreamSoapPort.SoapInterceptors;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
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
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpComponents5Connection;
import org.springframework.ws.transport.http.HttpComponents5MessageSender;
/**
 * 
 * @deprecated use UpstreamBuilder from net.optionfactory.spring.upstream
 */
@Deprecated
public class UpstreamSoapPort<CTX> implements UpstreamPort<CTX> {

    private final String upstreamId;
    private final UpstreamRequestCounter requestCounter;
    private final WebServiceTemplate soap;
    private final List<UpstreamInterceptor<CTX>> interceptors;
    private final ThreadLocal<ExchangeContext<CTX>> callContexts = new ThreadLocal<>();

    public UpstreamSoapPort(SoapVersion soapVersion, String upstreamId, UpstreamRequestCounter requestCounter, Resource[] schemas, Class<?> packageToScan, SSLConnectionSocketFactory socketFactory, int connectionTimeoutInMillis, List<ClientInterceptor> additionalInterceptors, List<UpstreamInterceptor<CTX>> interceptors) {
        final var client = HttpClientBuilder.create()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(socketFactory)
                        .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(5, TimeUnit.SECONDS).build())
                        .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
                        .build())
                .addRequestInterceptorFirst(new HttpComponents5MessageSender.RemoveSoapHeadersInterceptor())
                .addResponseInterceptorLast((HttpResponse hr, EntityDetails entity, HttpContext hc) -> {
                    final var headers = new HttpHeaders();
                    for (Header header : hr.getHeaders()) {
                        headers.add(header.getName(), header.getValue());
                    }
                    final ExchangeContext<CTX> ctx = callContexts.get();
                    ctx.response = new ResponseContext();
                    ctx.response.headers = headers;
                    ctx.response.status = HttpStatus.resolve(hr.getCode());
                    ctx.response.at = Instant.now();
                    ctx.response.body = null;
                })
                .build();

        final var inner = new WebServiceTemplate();
        final SaajSoapMessageFactory mf = new SaajSoapMessageFactory();
        mf.setSoapVersion(soapVersion);
        initBean(mf);
        inner.setMessageFactory(mf);
        inner.setMessageSender(new HttpComponents5MessageSender(client));
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

        final ClientInterceptor[] clientInterceptors = Stream.of(
                Stream.of(validator),
                additionalInterceptors.stream(),
                Stream.of(new SoapInterceptors<>(interceptors, callContexts))
        ).flatMap(Function.identity()).toArray(n -> new ClientInterceptor[n]);

        inner.setInterceptors(clientInterceptors);

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
    public <T> ResponseEntity<T> exchange(CTX context, String endpointId, RequestEntity<?> requestEntity, Class<T> responseType, Hints<CTX> hints) {
        return exchange(context, endpointId, requestEntity, hints);
    }

    @Override
    public <T> ResponseEntity<T> exchange(CTX context, String endpointId, RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType, Hints<CTX> hints) {
        return exchange(context, endpointId, requestEntity, hints);
    }

    private <T> ResponseEntity<T> exchange(CTX context, String endpointId, RequestEntity<?> requestEntity, Hints<CTX> hints) {
        final ExchangeContext<CTX> ctx = new ExchangeContext<>();
        ctx.hints = hints;
        ctx.prepare = new UpstreamInterceptor.PrepareContext<>();
        ctx.prepare.requestId = requestCounter.next();
        ctx.prepare.ctx = context;
        ctx.prepare.endpointId = endpointId;
        ctx.prepare.entity = requestEntity;
        ctx.prepare.upstreamId = upstreamId;
        callContexts.set(ctx);
        try {
            final var headers = new HttpHeaders();
            headers.addAll(ctx.prepare.entity.getHeaders());
            headers.remove("SOAPAction");
            for (var interceptor : interceptors) {
                final var newHeaders = interceptor.prepare(ctx.hints, ctx.prepare);
                if (newHeaders != null) {
                    headers.addAll(newHeaders);
                }
            }
            final var soapAction = ctx.prepare.entity.getHeaders().getFirst("SOAPAction");
            ctx.prepare.entity = new RequestEntity<>(ctx.prepare.entity.getBody(), headers, ctx.prepare.entity.getMethod(), ctx.prepare.entity.getUrl(), ctx.prepare.entity.getType());
            final var got = soap.marshalSendAndReceive(ctx.prepare.entity.getUrl().toString(), ctx.prepare.entity.getBody(), (WebServiceMessage message) -> {
                if (soapAction != null) {
                    ((SoapMessage) message).setSoapAction(soapAction);
                }
                final HttpComponents5Connection connection = (HttpComponents5Connection) TransportContextHolder.getTransportContext().getConnection();
                for (Entry<String, List<String>> header : ctx.prepare.entity.getHeaders().entrySet()) {
                    for (String value : header.getValue()) {
                        connection.addRequestHeader(header.getKey(), value);
                    }
                }
            });
            final ResponseEntity<T> response = ResponseEntity.ok().headers(ctx.response.headers).body((T) got);
            for (UpstreamInterceptor<CTX> interceptor : interceptors) {
                interceptor.mappingSuccess(ctx.hints, ctx.prepare, ctx.request, ctx.response, response);
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
                interceptor.before(ctx.hints, ctx.prepare, ctx.request);
            }

            return true;
        }

        @Override
        public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
            final ExchangeContext<CTX> ctx = callContexts.get();
            ctx.response.body = toResource(messageContext.getResponse());
            for (var interceptor : interceptors) {
                interceptor.remotingSuccess(ctx.hints, ctx.prepare, ctx.request, ctx.response);
            }
            return true;
        }

        @Override
        public boolean handleFault(MessageContext messageContext) throws WebServiceClientException {
            final ExchangeContext<CTX> ctx = callContexts.get();
            ctx.response.body = toResource(messageContext.getResponse());
            for (var interceptor : interceptors) {
                interceptor.remotingSuccess(ctx.hints, ctx.prepare, ctx.request, ctx.response);
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
                interceptor.remotingError(ctx.hints, ctx.prepare, ctx.request, ctx.error);
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
