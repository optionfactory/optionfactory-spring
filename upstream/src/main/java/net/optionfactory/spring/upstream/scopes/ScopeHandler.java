package net.optionfactory.spring.upstream.scopes;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import net.optionfactory.spring.upstream.UpstreamAfterMappingHandler;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor.HttpMessageConverters;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.apache.hc.client5.http.utils.Hex;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.ResponseErrorHandler;

public interface ScopeHandler {

    public static final String BOOT_ID = Hex.encodeHexString(ByteBuffer.allocate(Integer.BYTES).putInt((int) Instant.now().getEpochSecond()).array());

    MethodInterceptor interceptor(HttpMessageConverters cs, List<UpstreamAfterMappingHandler> afterMappingHandlers);

    ClientHttpRequestInterceptor adapt(UpstreamHttpInterceptor interceptor);

    ClientHttpRequestInitializer adapt(UpstreamHttpRequestInitializer initializer);

    ClientHttpRequestFactory adapt(UpstreamHttpRequestFactory factory);

    ResponseErrorHandler adapt(UpstreamResponseErrorHandler factory);

}
