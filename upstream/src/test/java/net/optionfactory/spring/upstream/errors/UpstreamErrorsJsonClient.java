package net.optionfactory.spring.upstream.errors;

import net.optionfactory.spring.upstream.Upstream;
import org.springframework.web.service.annotation.GetExchange;

public interface UpstreamErrorsJsonClient {

    @GetExchange
    @Upstream.Error(value = "#json_path('success').asBoolean() == false")
    Response callWithJsonPath();

    public static class Response {

        public Metadata metadata;
        public String data;
    }

    public static class Metadata {

        public boolean success;

    }
}
