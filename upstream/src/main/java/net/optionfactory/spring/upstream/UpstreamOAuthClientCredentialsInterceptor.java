package net.optionfactory.spring.upstream;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import net.optionfactory.spring.upstream.UpstreamPort.Hints;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class UpstreamOAuthClientCredentialsInterceptor<T> implements UpstreamInterceptor<T> {

    private final String clientId;
    private final String clientSecret;
    private final URI tokenUri;
    private final RestTemplate restOauth;

    public UpstreamOAuthClientCredentialsInterceptor(String clientId, String clientSecret, SSLConnectionSocketFactory socketFactory, URI tokenUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        final var builder = HttpClientBuilder.create();
        builder.setSSLSocketFactory(socketFactory);
        final var client = builder.setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(5000).build())
                .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
                .build();
        final var requestFactory = new HttpComponentsClientHttpRequestFactory(client);
        
        this.restOauth = new RestTemplate(requestFactory);

    }

    @Override
    public HttpHeaders prepare(Hints<T> hints, PrepareContext<T> prepare) {
        final String token = getOauthToken();
        final var headers = new HttpHeaders();
        headers.set("Authorization", String.format("Bearer %s", token));
        return headers;
    }

    private String getOauthToken() throws RestClientException {
        final var headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON_UTF8));
        headers.setContentType(MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8"));
        headers.setBasicAuth(clientId, clientSecret);
        final var formParameters = new LinkedMultiValueMap<String, String>();
        formParameters.add("grant_type", "client_credentials");
        final RequestEntity<LinkedMultiValueMap<String, String>> re = new RequestEntity<>(formParameters, headers, HttpMethod.POST, tokenUri);
        final ResponseEntity<JsonNode> oResponse = restOauth.exchange(re, JsonNode.class);
        return oResponse.getBody().get("access_token").asText();
    }

}
