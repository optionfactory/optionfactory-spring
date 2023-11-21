package net.optionfactory.spring.upstream.faults;

import java.io.IOException;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

public interface UpstreamFaultStrategies {

    public static HttpStatusCode status(ClientHttpResponse response) {
        try {
            return response.getStatusCode();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static interface OnRemotingSuccess {

        boolean isFault(UpstreamHttpInterceptor.InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpResponse response);
    }

    public static class OkOnRemotingSuccessPredicate implements OnRemotingSuccess {

        @Override
        public boolean isFault(UpstreamHttpInterceptor.InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpResponse response) {
            return false;
        }
    }

    public static class OkOn2xxPredicate implements OnRemotingSuccess {

        @Override
        public boolean isFault(UpstreamHttpInterceptor.InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpResponse response) {
            return !status(response).is2xxSuccessful();
        }
    }

    public static class FaultOn5xxPredicate implements OnRemotingSuccess {

        @Override
        public boolean isFault(UpstreamHttpInterceptor.InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpResponse response) {
            return status(response).is5xxServerError();
        }
    }

    public static class FaultOn4xxPredicate implements OnRemotingSuccess {

        @Override
        public boolean isFault(UpstreamHttpInterceptor.InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpResponse response) {
            return status(response).is4xxClientError();
        }
    }

    public static class FaultOn4xxOr5xxPredicate implements OnRemotingSuccess {

        @Override
        public boolean isFault(UpstreamHttpInterceptor.InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpResponse response) {
            return status(response).isError();
        }
    }

    public static interface OnRemotingError {

        boolean isFault(UpstreamHttpInterceptor.InvocationContext ctx, HttpRequest request, byte[] body, Exception ex);
    }

    public static class OkOnRemotingErrorPredicate implements OnRemotingError {

        @Override
        public boolean isFault(UpstreamHttpInterceptor.InvocationContext ctx, HttpRequest request, byte[] body, Exception ex) {
            return false;
        }
    }

    public static class FaultOnRemotingErrorPredicate implements OnRemotingError {

        @Override
        public boolean isFault(UpstreamHttpInterceptor.InvocationContext ctx, HttpRequest request, byte[] body, Exception ex) {
            return true;
        }
    }

}
