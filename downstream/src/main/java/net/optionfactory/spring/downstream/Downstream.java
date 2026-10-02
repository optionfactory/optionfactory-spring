package net.optionfactory.spring.downstream;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marker annotations driving the client code generation of `downstream-maven-plugin`.
///
/// The server marks its endpoints with [Method]; the plugin, run in the client project with the
/// server on its classpath, collects the payload types reachable from the marked methods (return
/// type, parameters, and recursively their fields, type arguments, array components and nested
/// classes) and generates Java DTOs (`generate-dtos`) or TypeScript definitions (`generate-ts`)
/// for the ones in the configured source package. [Ignore] prunes that walk and [Rename] changes
/// the generated name.
///
/// ```java
/// @PostMapping("/api/orders")
/// @Downstream.Method(clients = "web-frontend")
/// public OrderResponse createOrder(@RequestBody OrderRequest request, @Downstream.Ignore Principal principal) {
///     ...
/// }
/// ```
///
/// This module only holds the annotations: it has no dependencies and no runtime behaviour.
public interface Downstream {

    /// Marks a method as an endpoint whose payloads are generated for the downstream clients.
    ///
    /// The plugin scans the whole classpath for it, so the method's class needs no other
    /// annotation and only the payload types are restricted to the configured source package.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Method {

        /// The clients this endpoint is generated for, matched against the plugin's
        /// `targetClientName`.
        ///
        /// Empty (the default) means every client. An execution without a `targetClientName`
        /// includes every marked method, whatever its clients.
        ///
        /// @return the client names, empty for all of them
        String[] clients() default {};
    }

    /// Excludes an element from the generation.
    ///
    /// - on a type: the type is neither generated nor walked into. A field still referencing it is
    ///   emitted with the original type in Java, and as `any` in TypeScript (or its simple name, for
    ///   an enum), so it is usually paired with a `translations` entry;
    /// - on an endpoint parameter: the parameter type is not walked into, e.g. for a `Principal`
    ///   or a framework type resolved by the server;
    /// - on a public field of a class: the field is left out of the generated type.
    ///
    /// The annotation targets neither methods nor record components, so a getter cannot be
    /// ignored (a getter of an ignored field still generates the property) and neither can a
    /// record component: on a component the annotation only lands on the private field and the
    /// canonical constructor parameter, which the generation does not look at.
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Ignore {
    }

    /// Generates the annotated type with another simple name, in place of its own.
    ///
    /// The new name also takes part in the names of the nested types derived from it (e.g. with
    /// the `PREFIXED` nesting, `Outer.Inner` with `Outer` renamed to `Root` becomes `RootInner`),
    /// and in the naming collision check, which fails the generation when two types end up with
    /// the same name.
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Rename {

        /// @return the simple name of the generated type
        String value();
    }
}
