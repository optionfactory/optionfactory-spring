package net.optionfactory.spring.upstream.mocks;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClientException;

public class MockResourcesUpstreamHttpResponseFactory implements UpstreamHttpResponseFactory {

    private final SpelExpressionParser parser;
    private final Map<Method, List<Expression>> methodToExpression = new ConcurrentHashMap<>();
    private final Map<Method, MediaType> methodToContentType = new ConcurrentHashMap<>();
    private final Map<Method, HttpStatus> methodToStatus = new ConcurrentHashMap<>();
    private final TemplateParserContext templateParserContext = new TemplateParserContext();

    public MockResourcesUpstreamHttpResponseFactory() {
        this.parser = new SpelExpressionParser();
    }

    public record MockEvaluationContext(String upstream, String endpoint, Map<String, Object> args) {

    }

    @Override
    public void prepare(Class<?> klass) {

        final var interfaceConf = klass.getAnnotationsByType(Upstream.Mock.class);

        final var defaultContentType = AnnotationUtils.synthesizeAnnotation(Upstream.MockContentType.class);
        final var defaultStatus = AnnotationUtils.synthesizeAnnotation(Upstream.MockStatus.class);

        for (Method m : klass.getDeclaredMethods()) {
            final var methodConf = m.getAnnotationsByType(Upstream.Mock.class);
            final var conf = Stream.concat(Stream.of(methodConf), Stream.of(interfaceConf)).toArray(i -> new Upstream.Mock[i]);
            if (conf.length == 0) {
                continue;
            }
            final var expressions = new ArrayList<Expression>();
            for (Upstream.Mock annotation : conf) {
                expressions.add(parser.parseExpression(annotation.value(), templateParserContext));
            }
            methodToExpression.put(m, expressions);

            final var contentType = Optional
                    .ofNullable(m.getAnnotation(Upstream.MockContentType.class))
                    .or(() -> Optional.ofNullable(m.getDeclaringClass().getAnnotation(Upstream.MockContentType.class)))
                    .orElse(defaultContentType);

            methodToContentType.put(m, MediaType.parseMediaType(contentType.value()));

            final var status = Optional.ofNullable(m.getAnnotation(Upstream.MockStatus.class))
                    .or(() -> Optional.ofNullable(m.getDeclaringClass().getAnnotation(Upstream.MockStatus.class)))
                    .orElse(defaultStatus);
            methodToStatus.put(m, status.value());

        }
    }

    @Override
    public ClientHttpResponse create(UpstreamHttpInterceptor.InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) {
        final var expressions = methodToExpression.get(ctx.method());
        if (expressions == null) {
            throw new RestClientException(String.format("mock resource not configured for %s:%s", ctx.upstream(), ctx.endpoint()));
        }
        final var contentType = methodToContentType.get(ctx.method());
        final var status = methodToStatus.get(ctx.method());

        final var args = IntStream.range(0, ctx.method().getParameters().length).mapToObj(i -> i).collect(Collectors.toMap(i -> ctx.method().getParameters()[i].getName(), i -> ctx.arguments()[i]));
        final var context = new StandardEvaluationContext(new MockEvaluationContext(ctx.upstream(), ctx.endpoint(), args));
        context.addPropertyAccessor(new MapAccessor());
        for (Expression expression : expressions) {
            final var path = expression.getValue(context, String.class);
            final var resource = new ClassPathResource(path, ctx.method().getDeclaringClass());
            if (!resource.exists()) {
                continue;
            }
            final var responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(contentType);
            return new MockClientHttpResponse(status, status.getReasonPhrase(), responseHeaders, resource);
        }
        throw new RestClientException(String.format("mock resource not found for %s:%s", ctx.upstream(), ctx.endpoint()));
    }

}
