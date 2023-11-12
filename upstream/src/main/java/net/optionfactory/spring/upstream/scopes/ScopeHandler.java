package net.optionfactory.spring.upstream.scopes;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.apache.hc.client5.http.utils.Hex;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.HttpMessageConverter;

public interface ScopeHandler {

    public static final String BOOT_ID = Hex.encodeHexString(ByteBuffer.allocate(Integer.BYTES).putInt((int) Instant.now().getEpochSecond()).array());

    MethodInterceptor interceptor(List<HttpMessageConverter<?>> cs);

    ClientHttpRequestInterceptor adapt(UpstreamHttpInterceptor interceptor);

    ClientHttpRequestFactory adapt(UpstreamHttpRequestFactory factory);
}
