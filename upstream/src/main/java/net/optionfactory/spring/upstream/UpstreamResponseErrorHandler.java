package net.optionfactory.spring.upstream;

import java.io.IOException;
import java.util.List;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;

public class UpstreamResponseErrorHandler extends DefaultResponseErrorHandler {
    
    private final String upstreamId;
    private final List<UpstreamInterceptor> interceptors;

    public UpstreamResponseErrorHandler(String upstreamId, List<UpstreamInterceptor> interceptors) {
        this.upstreamId = upstreamId;
        this.interceptors = interceptors;
    }
    
    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        throw new UpstreamException(upstreamId, "GENERIC_ERROR", Integer.toString(response.getRawStatusCode()));
    }
}
