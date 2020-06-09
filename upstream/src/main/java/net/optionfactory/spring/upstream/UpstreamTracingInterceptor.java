package net.optionfactory.spring.upstream;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;

public class UpstreamTracingInterceptor<CTX> implements UpstreamInterceptor<CTX> {

    private final AtomicLong requestsCounter;
    private final UpstreamContextTransformer<CTX> ctxMapper;

    public UpstreamTracingInterceptor(AtomicLong requestsCounter, UpstreamContextTransformer<CTX> ctx) {
        this.requestsCounter = requestsCounter;
        this.ctxMapper = ctx;
    }

    @Override
    public HttpHeaders prepare(String upstreamId, CTX userId, RequestEntity<?> entity) {
        final HttpHeaders headers = new HttpHeaders();
        final long requestId = requestsCounter.incrementAndGet();
        ctxMapper.toMap(userId).forEach((k, v) -> {
            headers.add(k, v);
        });
        headers.set("X-HI-TIMESTAMP", Long.toString(new Date().getTime()));
        headers.set("X-HI-REQID", Long.toString(requestId));
        return headers;
    }

    public Map<String, String> contextAsMap(HttpHeaders requestHeaders) {
        return ctxMapper.toMap(ctxMapper.fromMap(requestHeaders.toSingleValueMap()));
    }

    public CTX context(HttpHeaders requestHeaders) {
        return ctxMapper.fromMap(requestHeaders.toSingleValueMap());
    }

    public String logPrefix(HttpHeaders requestHeaders) {
        final CTX ctx = ctxMapper.fromMap(requestHeaders.toSingleValueMap());
        return ctxMapper.toLogPrefix(ctx);
    }

    public String requestId(HttpHeaders requestHeaders) {
        final String value = requestHeaders.getFirst("X-HI-REQID");
        if (value == null) {
            throw new IllegalStateException("missing REQID header: tracing interceptor is not configured");
        }
        return value;
    }

    public Instant timestamp(HttpHeaders requestHeaders) {
        final String value = requestHeaders.getFirst("X-HI-TIMESTAMP");
        if (value == null) {
            throw new IllegalStateException("missing TIMESTAMP header: tracing interceptor is not configured");
        }
        return Instant.ofEpochMilli(Long.parseLong(value));
    }

}
