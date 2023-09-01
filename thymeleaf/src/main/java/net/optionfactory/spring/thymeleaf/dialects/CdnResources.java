package net.optionfactory.spring.thymeleaf.dialects;

import java.net.URI;
import java.util.Arrays;
import java.util.stream.Collectors;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public class CdnResources {

    private final URI baseUri;

    public CdnResources(URI baseUri) {
        final String s = baseUri.toString();
        this.baseUri = s.endsWith("/") ? baseUri : URI.create(s + "/");
    }

    public String url(String... parts) {
        final var path = Arrays.stream(parts).collect(Collectors.joining("/"));
        return baseUri.resolve(path).toString();
    }
    
    public String presignedUrl(String parts){
        final S3Presigner presigner = S3Presigner.builder()
                .region(Region.EU_WEST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(parts, parts)))
                .build();
        
        presigner.
        
    }
}
