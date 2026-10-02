package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.spring.data.jpa.filtering.Filter;

/// Marks an annotation as a filter whitelisting annotation, naming the [Filter] implementation it
/// configures.
///
/// The repository instantiates a filter for each annotation on the entity that is meta-annotated
/// with it, repeated annotations included, choosing the only public constructor whose parameters
/// are all among: the annotation itself, the `JpaEntityInformation`, the `EntityManager`, the
/// `EntityManagerFactory` and the `EntityType` of the entity. This is how custom filter
/// annotations, with attributes of their own, are defined:
///
/// ```java
/// @Target(ElementType.TYPE)
/// @Retention(RetentionPolicy.RUNTIME)
/// @WhitelistedFilter(NearbyFilter.class)
/// public @interface Nearby {
///     String name();
///     double maxKilometers();
/// }
/// ```
@Documented
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WhitelistedFilter {

    /// @return the filter implementation instantiated for each annotated entity
    Class<? extends Filter> value();
}
