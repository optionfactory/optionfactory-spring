# optionfactory-spring/problems-web

REST exception resolver for reporting errors in API responses (e.g., validation errors) using the `problems` module types.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>problems-web</artifactId>
</dependency>
```

Note: `net.optionfactory.spring:problems` is a transitive dependency.

## Usage

Configure the exception resolvers in your `WebMvcConfigurer`:

```java
@Override
public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
    ExceptionResolvers.configurer(resolvers)
            .rest(jsonMapper) // Registers RestExceptionResolver
            .configure();
}
```

This will automatically map standard Spring exceptions (like `MethodArgumentNotValidException`) and custom `Failure` exceptions to a unified JSON error response.

### Classifying another library's exceptions

A library whose exceptions describe a bad request can have them answered as one without
depending on this module, and without this module depending on it. The library ships an
`ExceptionClassifier`, usually as part of a `ProblemsModule` bundling everything it contributes,
and the application registers the module on the rest resolver once. For instance, `data-jpa-web`'s
module, which answers rejected filters with a `400`:

```java
ExceptionResolvers.configurer(resolvers)
        .rest(jsonMapper, rest -> rest.withModule(new DataJpaProblemsModule()))
        .configure();
```

Classifiers are consulted in registration order, only for exceptions the resolver has no built-in
case for, and before it falls back to reporting an unexpected error. A classified exception is
therefore answered with the classifier's status and problems and logged at `DEBUG`, rather than
as an `ERROR` with a stack trace. A classifier receives the request being answered together with
the resolver's message source and locale, so it can localize what it reports.

A module contributes classifiers and transformers and nothing else, so it cannot change how the
resolver itself is configured. In particular, it cannot include details in production: detail
omission runs after every transformer, a module's included. A single classifier can also be
registered on its own, with `withClassifier(...)`.

A `FailureTransformer` cannot do a classifier's job: it transforms a failure the resolver has
already classified, and by then an unknown exception has already been logged as an error.

## Resolvers

Each is opt-in, and `configure()` places them in the order below.

| option | resolver | answers |
| ------ | -------- | ------- |
| `undeliverables()` | `UndeliverableResponseExceptionResolver` | nothing, for requests that can no longer be answered |
| `rest(mapper)` | `RestExceptionResolver` | a json problem document, for `@ResponseBody` handlers |
| `binaries()` | `BinaryResponseExceptionResolver` | a status, for downloads and streamed binaries |
| `pages()` | `PagesExceptionResolver` | an error view, for everything left |

### undeliverables()

Add it when the application has an endpoint that streams, such as server-sent
events or a `StreamingResponseBody` download.

A streaming endpoint commits its response with the first byte it sends, and the
usual way such a stream ends is that the client goes away. The resolvers behind
this one answer an exception by setting a status and rendering a body, neither
of which is possible once the response is committed, so the attempt throws a
second exception that buries the first. Without it, one server-sent events
client closing its tab produced two stack traces: the disconnect reported as an
unexpected error with a json problem document that could not be written, then
the failure to write it, escaping `render` where no resolver can catch it and
surfacing as a container level `ERROR`.

It declines every other exception, so the resolvers behind it are unaffected.
A disconnect is recognised through spring's `DisconnectedClientHelper`, which
reads the whole cause chain and excludes `DataAccessException` and
`RestClientException`, so a broken pipe to an upstream of yours still reports
loudly. A genuine fault on an already committed response is still logged at
`WARN`: it cannot be told to the client, but it is still worth knowing.

