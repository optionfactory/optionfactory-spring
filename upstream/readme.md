# optionfactory-spring/upstream

[`HTTP Interface`](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-http-interface) 
/ [`RestClient`](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html) 
/ [`HttpComponents 5`](https://hc.apache.org/httpcomponents-client-5.4.x/migration-guide/index.html) 
SOAP and REST clients.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>upstream</artifactId>
</dependency>
```

## Usage

### 1. Define the Client Interface

```java
@Upstream("my-service")
@Upstream.Logging
public interface MyServiceClient {
    @GetExchange("/api/data")
    Map<String, String> fetchData(@RequestParam("id") String id);
}
```

### 2. Build the Client

```java
@Bean
public MyServiceClient myServiceClient(JsonMapper mapper, ConfigurableApplicationContext ac) {
    return UpstreamBuilder.create(MyServiceClient.class)
            .requestFactoryHttpComponents(c -> {
                c.tlsSocketStrategy(HcSocketStrategies.system());
            })
            .json(mapper)
            .expressions(ac)
            .publisher(ac)
            .baseUri("https://api.example.com")
            .build();
}
```