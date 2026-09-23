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

### Filter and sort errors, with problems-web

A filter or sort request the repository rejects — an unknown filter or sorter name, an operator
outside the whitelist, a value that doesn't parse — is the client's mistake. Without help,
though, `problems-web` answers it with a `500` and an `ERROR` log: the rejection is an
`IllegalArgumentException`, which spring's JPA exception translation rewraps as an
`InvalidDataAccessApiUsageException`, and the rest resolver has no case for that.

When you use `problems-web`, register this module's `DataJpaProblemsModule` where the rest
resolver is configured:

```java
@Override
public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
    ExceptionResolvers.configurer(resolvers)
            .rest(jsonMapper, rest -> rest.withModule(new DataJpaProblemsModule()))
            .configure();
}
```

Rejections are then answered with a `400` and logged at `DEBUG`:

```json
[{"type": "FIELD_ERROR", "context": "byBirthDate", "reason": "cannot parse 'not-a-date' as a local date: ...", "details": null}]
```

It's a field error, whose `context` is the filter or sorter name the client sent — so a UI can
highlight the offending filter — and whose `reason` is phrased in terms of its request, so neither
reveals the entity behind the name. The full message, which does name
it, is kept in `details`, which `problems-web` omits in production.

The module is the stable thing to register: whatever else this library contributes to
`problems-web` in the future is added to it, with no change to your configuration. It currently
holds one `FilteringExceptionClassifier`.

`problems-web` is an optional dependency of this module: add it yourself to use it. Applications
that don't use it never load these classes.

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
