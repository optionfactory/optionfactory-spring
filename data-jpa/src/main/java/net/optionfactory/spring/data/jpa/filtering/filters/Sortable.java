package net.optionfactory.spring.data.jpa.filtering.filters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.spring.data.jpa.filtering.filters.Sortable.RepeatableSortable;

/// Whitelists a sorter on an entity: a name a client may sort by, and the property it sorts on.
///
/// A sort requested to the repository names sorters, not properties: `Sort.by("byOwner")` sorts by
/// the property the `byOwner` sorter points to, and a name that is not whitelisted is rejected
/// with an [net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidSortRequest]. The path
/// may cross singular associations and embeddables, joined as filters join them (see
/// [FilterTraversal]), but not collections: sorting on a collection element would multiply the
/// rows and corrupt pagination, so such a path, like one that does not resolve, is rejected when
/// the repository is built. The sort direction, the null handling and, for a `String` property, the
/// ignore-case flag of the requested order are honoured.
///
/// ```java
/// @Entity
/// @Sortable(name = "byOwner", path = "owner.name")
/// public class Pet { ... }
///
/// pets.findAll(request, PageRequest.of(0, 20, Sort.by(Sort.Order.asc("byOwner").ignoreCase())));
/// ```
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RepeatableSortable.class)
public @interface Sortable {

    /// @return the name the sorter is whitelisted under, and requested by
    String name();

    /// @return the dot-separated path of the sorted property, from the entity
    String path();

    /// The container of repeated [Sortable] annotations, used implicitly by the compiler when the
    /// annotation is repeated on an entity.
    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableSortable {

        /// @return the repeated annotations
        Sortable[] value();
    }
}
