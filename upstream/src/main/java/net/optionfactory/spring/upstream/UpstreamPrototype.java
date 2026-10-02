package net.optionfactory.spring.upstream;

/// A partially configured upstream client, shared to derive clients that differ in a few
/// settings: each call to [#builder] returns an independent copy.
///
/// ```java
/// final UpstreamPrototype<PaymentsClient> prototype = UpstreamBuilder.create(PaymentsClient.class)
///         .json(mapper)
///         .applicationContext(ac);
/// final var live = prototype.builder().baseUri(liveUri).requestFactoryHttpComponents(c -> {}).build();
/// final var sandbox = prototype.builder().baseUri(sandboxUri).requestFactoryHttpComponents(c -> {}).build();
/// ```
///
/// @param <T> the client interface
public interface UpstreamPrototype<T> {

    /// @return a new builder holding a copy of this configuration, which can be changed without
    /// affecting the prototype
    UpstreamBuilder<T> builder();
}
