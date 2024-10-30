# optionfactory-spring/data-jpa-web

Spring MVC support for data-jpa.

## Maven

```xml
        <dependency>
            <groupId>net.optionfactory.spring</groupId>
            <artifactId>data-jpa-web</artifactId>
        </dependency>
```
Note: `net.optionfactory.spring:data-jpa` is a transitive dependency



## Usage

`FilterRequestArgumentResolver` can be configured in your `WebMvcConfigurer` to handle parsing of filter requests from requestParameters:

```java    
@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

    /*...*/

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        final var pageableResolver = new PageableHandlerMethodArgumentResolver();
        pageableResolver.setFallbackPageable(PageRequest.of(0, 100));
        pageableResolver.setMaxPageSize(Integer.MAX_VALUE);
        resolvers.add(pageableResolver);
        resolvers.add(new FilterRequestArgumentResolver(restObjectMapper));
    }
}
```

`PageMixin` can be configured on the objectMapper to handle seralization of `Page`s in a simplified form:

```java
    @Bean
    public ObjectMapper restObjectMapper() {
        return new Jackson2ObjectMapperBuilder()
                /*...*/
                .mixIn(Page.class, PageMixin.class)
                .build();
    }
```
Page instances will be serialized as:

```json
[{
    "data": [],
    "size": 0
}]
```