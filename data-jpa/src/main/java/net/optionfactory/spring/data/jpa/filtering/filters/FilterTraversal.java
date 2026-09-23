package net.optionfactory.spring.data.jpa.filtering.filters;

import jakarta.persistence.criteria.JoinType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// An optional configuration annotation applied at the entity level to fine-tune 
/// how the dynamic filtering engine navigates relational graph boundaries.
/// 
/// ### Dynamic Auto-Deduction (Default Behavior)
/// 
/// By default, this annotation is **completely optional**. If a filter targets a relational 
/// path (e.g., `path = "address.state"`), the filtering engine automatically consults the 
/// JPA Metamodel to deduce the safest and most performant execution plan:
/// 
/// - **Singular Relationships:** Points targeting a `SingularAttribute` (like `@ManyToOne`) 
///   automatically default to an inline [JoinType#LEFT]. This guarantees that root entities are not 
///   silently dropped from result sets during inequality (`NEQ`) or nullability comparisons.
/// - **Plural Relationships:** Points crossing a `PluralAttribute` boundary (like `@OneToMany`) 
///   automatically trigger a correlated `EXISTS` subquery. This shields the query from 
///   Cartesian row-multiplication and prevents Spring Data pagination structures from failing.
///   Whether the parent is kept when *some* element matches or when *none* does is the filter's own
///   [Match] quantifier, declared on the filter annotation rather than here.
/// - **Embedded Components:** `@Embedded` (embeddable) attributes are inlined into the owning
///   entity's table and emit no SQL join of their own. They are therefore always traversed as
///   [JoinType#LEFT], and a `@FilterTraversal` whose [path()] resolves to an embedded hop has no
///   effect: its [joinType()] is silently ignored. Only genuine association/collection hops
///   (`@ManyToOne`, `@OneToMany`, ...) can be customized this way.
/// 
/// ### How Subselects Work & The Role of Match
/// 
/// When dealing with collection attributes, the engine delegates predicate evaluation into a correlated 
/// `EXISTS` subquery: the parent row is correlated directly, and the collection is joined from it, so the 
/// parent table is read once. The generated structure looks like this:
/// 
/// ```sql
/// WHERE [NOT] EXISTS (
///     SELECT 1 FROM collection_table c
///     WHERE c.parent_id = primary_table.id AND <FILTER_CONDITION>
/// )
/// ```
/// 
/// The join into the collection is always an `INNER` one: an outer join would yield a row for every parent, 
/// making `EXISTS` vacuously true. Whether a parent is kept is decided by the filter's [Match] quantifier, which is 
/// the axis that actually matters when filtering across a collection, and which such a filter must therefore
/// state:
/// 
/// #### Case A: [Match#ANY]
/// The parent is kept when at least one element satisfies the condition. A parent whose collection is empty is 
/// dropped: it has no element to satisfy anything. This is the reading every positive operator (`EQ`, `CONTAINS`, 
/// `GT`, ...) has always had.
/// 
/// #### Case B: [Match#NONE]
/// The subquery is negated, and the parent is kept when *no* element satisfies the condition. A parent whose 
/// collection is empty therefore matches, correctly so. This is what a UI filter labelled "without a tag `x`" or 
/// "not in the HR department" means, and it is expressed as `NONE` over the condition `= x` — not as `ANY` over 
/// `<> x`, which keeps a parent that has both an `x` element and some other one, and drops the parent with no 
/// elements at all.
/// 
/// ### Usage Nuances & Deep Relations
/// 
/// Use this annotation strictly as an escape hatch to override those automated conventions:
/// 
/// ```java
/// @Entity
/// @FilterTraversal(path = "address", joinType = JoinType.INNER)
/// @FilterTraversal(path = "departments.employees", reuse = false)
/// @TextCompare(name = "byState", path = "address.state")
/// @TextCompare(name = "byEmpName", path = "departments.employees.name", match = Match.ANY)
/// public class Company { ... }
/// ```
/// 
/// #### 1. Performance Tuning Deep Hops
/// 
/// Path evaluation occurs segment-by-segment as the engine maps the graph traversal. For a deeply nested 
/// path like `"address.state.city.street"`, you can surgically target a single relational hop 
/// (e.g., `path = "address.state.city"`) to introduce a strict [JoinType#INNER] for database 
/// index optimization, while allowing the remaining hops to safely cascade under default `LEFT` semantics.
/// 
/// #### 2. Nested Subquery Folding vs. Isolation
/// 
/// When navigating multiple nested collections (e.g., `"departments.employees"`), the query builder 
/// defaults to **Subquery Folding (reuse = true)**. It groups the child collection inside the parent's 
/// ongoing `EXISTS` subquery, translating semantically to: *"Find a company with a department that 
/// contains an employee named X."*
/// 
/// Flipping [reuse()] to `false` splits the context, forcing the engine to evaluate the nested 
/// collection in its own completely separate, isolated `EXISTS` subquery, translating semantically to: 
/// *"Find a company that has a department, AND which also has an employee named X anywhere within the entity tree."*
/// 
/// @see net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository
/// @see net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters#traversal
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(FilterTraversal.List.class)
public @interface FilterTraversal {

    /// The tokenized relational path boundary prefix to customize (e.g., `"address"`, 
    /// `"departments.employees"`). This must match the leading segment of a declared 
    /// property filter path.
    /// 
    /// @return the targeted relational path prefix
    String path();

    /// Overrides the database joining mode applied when this specific hop is evaluated.
    /// Defaults to [JoinType#LEFT] to ensure standard filtering expressions do not inadvertently 
    /// discard parent entries with missing or null relations.
    ///
    /// Applies to singular hops only. A plural hop is always joined as [JoinType#INNER] inside its
    /// `EXISTS` subquery, since an outer join there would produce a row for every parent and make
    /// the subquery vacuously true; whether the parent is kept or dropped is decided by the filter's
    /// [Match] quantifier instead. As with an embedded hop, a `joinType` on a plural path is silently
    /// ignored.
    /// 
    /// @return the SQL join type constraint
    JoinType joinType() default JoinType.LEFT;

    /// Governs subquery segregation when evaluating collection-based paths.
    /// 
    /// - `true` (Default): Aggregates all overlapping path properties sequentially inside the 
    ///   same subquery block.
    /// - `false`: Instructs the query compiler to bypass parent folding and isolate this 
    ///   constraint into a standalone, independent `EXISTS` subquery node.
    /// 
    /// @return whether to reuse or isolate the subquery group context
    boolean reuse() default true;

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface List {
        FilterTraversal[] value();
    }
}