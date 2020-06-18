package net.optionfactory.spring.upstream;

import org.springframework.http.HttpHeaders;

public class UpstreamTracingInterceptor<CTX> implements UpstreamInterceptor<CTX> {

    private final UpstreamContextTransformer<CTX> ctxMapper;

    public UpstreamTracingInterceptor(UpstreamContextTransformer<CTX> ctx) {
        this.ctxMapper = ctx;
    }

    @Override
    public HttpHeaders prepare(PrepareContext<CTX> prepare) {
        final HttpHeaders headers = new HttpHeaders();
        ctxMapper.toMap(prepare.ctx).forEach((k, v) -> {
            headers.add(k, v);
        });
        headers.set("X-HI-REQID", Long.toString(prepare.requestId));
        return headers;
    }

    public String logPrefix(CTX ctx) {
        return ctxMapper.toLogPrefix(ctx);
    }



}
