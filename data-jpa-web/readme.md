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

https://github.com/optionfactory/optionfactory-spring/blob/88478051ee362442aad58ccab3943b846dc19d7d/data-jpa-web/src/test/java/net/optionfactory/spring/data/jpa/web/examples/DataJpaWebExampleTest.java#L69-L81


`PageMixin` can be configured on the jsonMapper to handle seralization of `Page`s in a simplified form:

https://github.com/optionfactory/optionfactory-spring/blob/88478051ee362442aad58ccab3943b846dc19d7d/data-jpa-web/src/test/java/net/optionfactory/spring/data/jpa/web/examples/DataJpaWebExampleTest.java#L47-L56

Page instances will be serialized as:

```json
[{
    "data": [],
    "size": 0
}]
```

See an example usage here:

https://github.com/optionfactory/optionfactory-spring/blob/88478051ee362442aad58ccab3943b846dc19d7d/data-jpa-web/src/test/java/net/optionfactory/spring/data/jpa/web/examples/DataJpaWebExampleTest.java#L1-L135
