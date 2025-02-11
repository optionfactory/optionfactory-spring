package net.optionfactory.spring.upstream.scopes;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import net.optionfactory.spring.upstream.contexts.InvocationContext.HttpMessageConverters;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.service.invoker.HttpExchangeAdapter;

public interface ScopeHandler {

    public static final String BOOT_ID = HexFormat.of().formatHex(ByteBuffer.allocate(Integer.BYTES).putInt((int) Instant.now().getEpochSecond()).array());

    public static final AtomicLong INVOCATION_COUNTER = new AtomicLong();

    MethodInterceptor interceptor(HttpMessageConverters cs);

    HttpExchangeAdapter adapt(UpstreamHttpExchangeAdapter adapter);

    ClientHttpRequestInterceptor adapt(List<UpstreamHttpInterceptor> interceptors);

    ClientHttpRequestInitializer adapt(UpstreamHttpRequestInitializer initializer);

    ClientHttpRequestFactory adapt(UpstreamHttpRequestFactory factory);

    ResponseErrorHandler adapt(UpstreamResponseErrorHandler factory);

}
