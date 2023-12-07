package net.optionfactory.spring.upstreamlegacy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.List;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor.HttpMessageConverters;
import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.faults.UpstreamFaultEvent;
import net.optionfactory.spring.upstreamlegacy.UpstreamPort.Hints;
import net.optionfactory.spring.upstreamlegacy.UpstreamPort.UpstreamFaultPredicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;

public class UpstreamLegacyFaultsInterceptor<CTX> implements UpstreamInterceptor<CTX> {

    private final ApplicationEventPublisher publisher;
    private final Method fakeInvokedMethod;

    public UpstreamLegacyFaultsInterceptor(ApplicationEventPublisher publisher) {
        try {
            this.publisher = publisher;
            this.fakeInvokedMethod = Object.class.getMethod("toString");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record BootAndRequestId(String boot, long requestId) {

    }

    public static BootAndRequestId adaptToBootAndRequestId(PrepareContext<?> ctx) {
        if (ctx.requestId == null) {
            return new BootAndRequestId("0", 0);
        }
        final var split = ctx.requestId.split("\\.");
        if (split.length == 2) {
            try {
                return new BootAndRequestId(split[0], Long.parseLong(split[1]));
            } catch (RuntimeException ex) {
                return new BootAndRequestId(ctx.requestId, 0);
            }
        }
        try {
            return new BootAndRequestId("0", Long.parseLong(split[0]));
        } catch (RuntimeException ex) {
            return new BootAndRequestId(split[0], 0);
        }
    }

    @Override
    public void remotingSuccess(Hints<CTX> hints, PrepareContext<CTX> prepare, RequestContext request, ResponseContext response) {
        final UpstreamFaultPredicate<CTX> isFault = hints.isFault != null ? hints.isFault : UpstreamOps::defaultFaultStrategy;
        if (!isFault.apply(prepare, request, response)) {
            return;
        }

        final var bootAndRequestId = adaptToBootAndRequestId(prepare);

        final var evt = new UpstreamFaultEvent(
                new InvocationContext(
                        new HttpMessageConverters(List.of()),
                        prepare.upstreamId,
                        prepare.endpointId,
                        fakeInvokedMethod,
                        new Object[0],
                        bootAndRequestId.boot,
                        prepare.ctx
                ),
                new net.optionfactory.spring.upstream.contexts.RequestContext(
                        bootAndRequestId.requestId,
                        request.at,
                        prepare.entity.getMethod(),
                        prepare.entity.getUrl(),
                        request.headers,
                        toByteArray(request.body)
                ),
                new net.optionfactory.spring.upstream.contexts.ResponseContext(
                        response.at,
                        response.status,
                        response.status.getReasonPhrase(),
                        response.headers,
                        BodySource.of(response.body)
                ),
                null);
        publisher.publishEvent(evt);
    }

    private static byte[] toByteArray(Resource r) {
        try (var is = r.getInputStream()) {
            return is.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void remotingError(Hints<CTX> hints, PrepareContext<CTX> prepare, RequestContext request, ErrorContext error) {
        final var bootAndRequestId = adaptToBootAndRequestId(prepare);

        final var evt = new UpstreamFaultEvent(
                new InvocationContext(
                        new HttpMessageConverters(List.of()),
                        prepare.upstreamId,
                        prepare.endpointId,
                        fakeInvokedMethod,
                        new Object[0],
                        bootAndRequestId.boot,
                        prepare.ctx
                ),
                new net.optionfactory.spring.upstream.contexts.RequestContext(
                        bootAndRequestId.requestId,
                        request.at,
                        prepare.entity.getMethod(),
                        prepare.entity.getUrl(),
                        request.headers, 
                        toByteArray(request.body)
                ),
                null,
                new ExceptionContext(error.at, error.ex.getMessage()));

        publisher.publishEvent(evt);
    }

}
