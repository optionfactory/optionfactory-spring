package net.optionfactory.spring.upstream;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public interface UpstreamFaultsSpooler<CTX> {

    void add(UpstreamFault<CTX> fault);

    public static class UpstreamFault<CTX> {

        public CTX context;
        public String reqId;
        public URI uri;
        public HttpStatus status;
        public MediaType contentType;
        public Instant requestInstant;
        public String request;
        public Instant responseInstant;
        public String response;
        public Throwable exception;

        public static <CTX> UpstreamFault<CTX> of(CTX context, String reqId, URI uri, HttpStatus status, MediaType contentType, Instant requestInstant, String request, Instant responseInstant, String response, Throwable exception) {
            final var f = new UpstreamFault<CTX>();
            f.context = context;
            f.reqId = reqId;
            f.uri = uri;
            f.status = status;
            f.contentType = contentType;
            f.requestInstant = requestInstant;
            f.request = request;
            f.responseInstant = responseInstant;
            f.response = response;
            f.exception = exception;
            return f;
        }

    }

}
