package net.optionfactory.spring.upstream.errors;

import net.optionfactory.spring.upstream.paths.JsonPath;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor.HttpMessageConverters;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor.InvocationContext;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import net.optionfactory.spring.upstream.paths.XmlPath;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.FileCopyUtils;

public class UpstreamErrorsHandler implements UpstreamResponseErrorHandler {

    private record Expressions(Set<HttpStatus.Series> series, Expression predicate, Expression message) {

    }

    private final Map<Method, List<Expressions>> conf = new ConcurrentHashMap<>();
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final TemplateParserContext templateParserContext = new TemplateParserContext();

    @Override
    public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
        for (Method m : k.getDeclaredMethods()) {
            if (m.isDefault()) {
                continue;
            }
            final var anns = Stream.of(m.getAnnotationsByType(Upstream.Error.class))
                    .map(annotation -> {
                        final var predicate = parser.parseExpression(annotation.value());
                        final var message = parser.parseExpression(annotation.reason(), templateParserContext);
                        return new Expressions(Set.of(annotation.series()), predicate, message);
                    })
                    .toList();
            conf.put(m, anns);
        }
    }

    @Override
    public boolean hasError(InvocationContext ctx, ClientHttpResponse response) throws IOException {
        return firstMatching(ctx, response).isPresent();
    }

    @Override
    public void handleError(UpstreamHttpInterceptor.InvocationContext ctx, ClientHttpResponse response) throws IOException {
        final var e = firstMatching(ctx, response).orElseThrow().message;
        final var ectx = new StandardEvaluationContext();
        ectx.setVariable("ctx", ctx);
        ectx.setVariable("response", response);
        ectx.setVariable("status", response.getStatusCode());
        final String reason = e.getValue(ectx, String.class);

        throw new RestClientUpstreamException(
                ctx.upstream(),
                ctx.endpoint(),
                reason,
                response.getStatusCode(),
                response.getStatusText(),
                response.getHeaders(),
                getResponseBody(response),
                getCharset(response)
        );
    }

    // see: DefaultResponseErrorHandler#handleError
    private static Charset getCharset(ClientHttpResponse response) {
        HttpHeaders headers = response.getHeaders();
        MediaType contentType = headers.getContentType();
        return (contentType != null ? contentType.getCharset() : null);
    }

    // see: DefaultResponseErrorHandler#handleError
    private static byte[] getResponseBody(ClientHttpResponse response) {
        try {
            return FileCopyUtils.copyToByteArray(response.getBody());
        } catch (IOException ex) {
            // ignore
        }
        return new byte[0];
    }

    private Optional<Expressions> firstMatching(InvocationContext ctx, ClientHttpResponse response) throws IOException {
        final List<Expressions> es = conf.get(ctx.method());
        if (es == null) {
            return Optional.empty();
        }
        for (Expressions e : es) {
            final Series serie = Series.valueOf(response.getStatusCode().value());
            if (!e.series().contains(serie)) {
                continue;
            }
            final var ectx = new StandardEvaluationContext();
            ectx.setVariable("ctx", ctx);
            ectx.setVariable("response", response);
            ectx.setVariable("status", response.getStatusCode());
            ectx.registerFunction("json_path", JsonPath.boundMethodHandle(ctx.converters(), response));
            ectx.registerFunction("xpath_bool", XmlPath.xpathBooleanBoundMethodHandle(response));
            if (e.predicate.getValue(ectx, boolean.class)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }


}
