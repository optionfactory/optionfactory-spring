package net.optionfactory.spring.upstream.scopes;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.service.invoker.HttpExchangeAdapter;

/// Binds the invocation in progress to the spring callbacks a client is made of.
///
/// `UpstreamBuilder` installs [#interceptor(MessageConverters)] around the client proxy, which
/// opens the invocation scope, and uses the `adapt` methods to turn each upstream component, which
/// receives an [InvocationContext], into its spring counterpart, which does not.
/// [ThreadLocalScopeHandler] is the implementation in use.
public interface ScopeHandler {

    /// Identifies this JVM run in the logs: the hex encoded epoch seconds at which the class was
    /// loaded.
    public static final String BOOT_ID = HexFormat.of().formatHex(ByteBuffer.allocate(Integer.BYTES).putInt((int) Instant.now().getEpochSecond()).array());

    /// The source of the invocation ids, shared by every client of the JVM.
    public static final AtomicLong INVOCATION_COUNTER = new AtomicLong();

    /// @param cs the client's message converters, exposed by the invocation contexts
    /// @return the advice opening an invocation scope around each call to the client
    MethodInterceptor interceptor(MessageConverters cs);

    /// @param adapter the exchange adapter
    /// @return the adapter as used by the `HttpServiceProxyFactory`
    HttpExchangeAdapter adapt(UpstreamHttpExchangeAdapter adapter);

    /// @param interceptors the interceptors, in execution order
    /// @return a single `RestClient` interceptor running them, see [InterceptorChainAdapter]
    ClientHttpRequestInterceptor adapt(List<UpstreamHttpInterceptor> interceptors);

    /// @param initializer the request initializer
    /// @return the initializer as used by the `RestClient`
    ClientHttpRequestInitializer adapt(UpstreamHttpRequestInitializer initializer);

    /// @param factory the request factory
    /// @return the factory as used by the `RestClient`
    ClientHttpRequestFactory adapt(UpstreamHttpRequestFactory factory);

    /// @param factory the response error handler
    /// @return the handler as used by the `RestClient`
    ResponseErrorHandler adapt(UpstreamResponseErrorHandler factory);

}
