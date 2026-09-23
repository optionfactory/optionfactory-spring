# optionfactory-spring/data-jpa-web

Spring MVC support for `data-jpa`, including `FilterRequest` parsing and simplified `Page` serialization.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>data-jpa-web</artifactId>
</dependency>
```

Note: `net.optionfactory.spring:data-jpa` is a transitive dependency.

## Usage

### FilterRequestArgumentResolver

Register the `FilterRequestArgumentResolver` in your `WebMvcConfigurer` to automatically parse `FilterRequest` from request parameters:

```java
@Override
public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new FilterRequestArgumentResolver());
}
```

Now you can use `FilterRequest` in your controller methods:

```java
@GetMapping("/api/people")
public Page<Person> search(FilterRequest filter, Pageable pageable) {
    return personRepository.findAll(filter, pageable);
}
```

### PageMixin

Configure `PageMixin` on your `JsonMapper` to serialize `Page` objects in a simplified form:

```java
JsonMapper mapper = JsonMapper.builder()
    .addModule(new SimpleModule().setMixInAnnotation(Page.class, PageMixin.class))
    .build();
```

Page instances will be serialized as:

```json
{
    "data": [...],
    "size": 10
}
```
