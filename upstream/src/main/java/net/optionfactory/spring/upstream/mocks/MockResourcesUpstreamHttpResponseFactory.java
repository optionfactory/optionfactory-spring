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
        
        final var interfaceConf = Optional.ofNullable(klass.getAnnotation(UpstreamMock.class));
        
        final var defaultContentType = AnnotationUtils.synthesizeAnnotation(UpstreamMockContentType.class);
        final var defaultStatus = AnnotationUtils.synthesizeAnnotation(UpstreamMockStatus.class);

        for (Method m : klass.getDeclaredMethods()) {
            final var conf = Optional.ofNullable(m.getAnnotation(UpstreamMock.class))
                    .or(() -> interfaceConf)
                    .orElse(null);
            if (conf == null) {
                continue;
            }
            final var expressions = new ArrayList<Expression>();
            expressions.add(parser.parseExpression(conf.value(), templateParserContext));
            for (String fallback : conf.fallbacks()) {
                expressions.add(parser.parseExpression(fallback, templateParserContext));
            }
            methodToExpression.put(m, expressions);

            final var contentType = Optional
                    .ofNullable(m.getAnnotation(UpstreamMockContentType.class))
                    .or(() -> Optional.ofNullable(m.getDeclaringClass().getAnnotation(UpstreamMockContentType.class)))
                    .orElse(defaultContentType);

            methodToContentType.put(m, MediaType.parseMediaType(contentType.value()));

            final var status = Optional.ofNullable(m.getAnnotation(UpstreamMockStatus.class))
                    .or(() -> Optional.ofNullable(m.getDeclaringClass().getAnnotation(UpstreamMockStatus.class)))
                    .orElse(defaultStatus);
            methodToStatus.put(m, status.value());

        }
    }

    @Override
    public ClientHttpResponse create(UpstreamHttpInterceptor.InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) {
        final var expressions = methodToExpression.get(ctx.method());
        if (expressions == null) {
            throw new RestClientException("mock resource not configured");
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
        throw new RestClientException("mock resource not found");
    }

}