package net.optionfactory.spring.upstream.status;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import net.optionfactory.spring.upstream.RequestFactories;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.ResponseSpec.ErrorHandler;

public class RestStatusHandlerTest {

    private final Logger logger = LoggerFactory.getLogger(RestStatusHandlerTest.class);

    @Test
    public void asd() {
        final var rc = RestClient.builder()
                .requestFactory(RequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(30), null))
                .requestInterceptor(new ClientHttpRequestInterceptor() {
                    @Override
                    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
                        logger.info("before");
                        ClientHttpResponse response = execution.execute(request, body);
                        logger.info("after");
                        return response;
                    }
                })
                .defaultStatusHandler(new ResponseErrorHandler() {
                    @Override
                    public boolean hasError(ClientHttpResponse response) throws IOException {
                        return false;
                    }

                    @Override
                    public void handleError(ClientHttpResponse response) throws IOException {
                        logger.info("handler");
                    }

                })
                .build();

        final var status = rc.get().uri(URI.create("http://localhost:8080"))
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();

    }
}
