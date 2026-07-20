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
/// 
/// ### How Subselects Work & The Role of JoinType
/// 
/// When dealing with collection attributes, the engine delegates predicate evaluation into an `EXISTS` subquery. 
/// The underlying adapter compiles the subquery by selecting a literal from a *correlated secondary root* rather than 
/// joining straight from the primary table query. The generated structure looks like this:
/// 
/// ```sql
/// WHERE EXISTS (
///     SELECT 1 FROM primary_table secondary_root
///     [JoinType] JOIN collection_table c ON c.parent_id = secondary_root.id
///     WHERE secondary_root.id = primary_table.id AND <FILTER_CONDITION>
/// )
/// ```
/// 
/// Because the relational navigation happens entirely *inside* the subquery context, changing the `joinType` 
/// yields distinct logical results when handling empty collections combined with negative assertions (like `NEQ` filters):
/// 
/// #### Case A: `JoinType.INNER` inside the Subquery
/// If a parent entity has an empty child collection (e.g., a Company with zero Departments), an `INNER JOIN` inside 
/// the subquery drops the row immediately. Because the dataset becomes empty before evaluating the condition, 
/// the filter logic never executes. The subquery returns zero rows, causing `EXISTS` to evaluate to `FALSE`.
/// * **Semantic Consequence:** Parent entities with empty collections are universally excluded from `NEQ` operations 
///   (e.g., a query for "Companies not in the HR department" will fail to return companies that have no departments at all).
/// 
/// #### Case B: `JoinType.LEFT` inside the Subquery (Default)
/// A `LEFT JOIN` preserves the driving parent row inside the subquery even if the relationship collection is completely 
/// empty, populating the target columns as `NULL`. Filter types with explicit null handling (such as `TextCompare`'s `NEQ` 
/// logic) will successfully match this `NULL` state[. The subquery yields a row, causing `EXISTS` to evaluate to `TRUE`.
/// * **Semantic Consequence:** Parent entities with empty collections are correctly preserved in negative-matching result sets.
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
/// @TextCompare(name = "byEmpName", path = "departments.employees.name")
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