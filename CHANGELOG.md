# version 27.0

## `spring-data-jpa`: Whitelist Filtering Optimization
This release introduces a massive architectural refinement to the query compilation engine. 
The framework now uses the **JPA Metamodel** as a single source of truth to auto-deduce
 database joining and subquery strategies dynamically. 
 This completely eliminates the need for manual, verbose, string-based filter relationship decorations.

### 💥 Breaking Changes
*   **Removed `@FilterGroup`:** The `@FilterGroup` annotations (`@FilterGroup.Join` and 
    `@FilterGroup.Subselect`) has been removed.
*   **Removal of Startup Join Validation:** Because relationship paths and subquery boundaries are now 
    evaluated and adapted dynamically at runtime based on the JPA Metamodel, 
    the startup check `Repositories.validateJoinTypes` has been entirely removed.

### 1. JPA Metamodel-Driven Auto-Deduction (Zero-Configuration Defaults)

The engine now inspects the structural type of your entity relationships segment-by-segment
 as it parses dot-notated filter paths:

*   **Singular Relationships (`@ManyToOne`, `@OneToOne`):** Paths crossing singular attributes automatically
    default to an inline `JoinType.LEFT`. This guarantees that parent entities are not
     inadvertently dropped from search results during negation (`NEQ`) or nullability filtering.
*   **Plural Relationships (`@OneToMany`, `@ManyToMany`):** Paths crossing collection boundaries automatically
    group themselves and execute via an optimized `EXISTS` subquery. 
    This natively protects your queries from row-multiplication side effects and ensures Spring
     Data JPA pagination functions safely on collections.
*   **Safe Structural Fallbacks:** Paths targeting non-association fields (such as Embeddables, 
    Records, or Postgres JSON columns mapped via `@JdbcTypeCode(SqlTypes.JSON)`) 
    fall back to clean, dot-notated object paths with zero injected SQL joins.

### 2. Automatic Nested Subquery Folding

When traversing deeply nested collections (e.g., `departments.employees.name`), the engine defaults to 
**Subquery Folding** (`reuse = true`). It automatically merges the nested collection predicate inside 
the parent's ongoing `EXISTS` context. This translates to a semantically unified constraint check 
(e.g., *"Find a company that has an IT department containing an employee named John"*), 
without requiring any manual mapping annotations.

### Migration & Usage Guide: `@FilterTraversal`

For most use cases, filtering behaves exactly as intended out of the box with zero extra annotations. 
If you need to fine-tune index paths or segregate complex nested relationship contexts, use the 
new `@FilterTraversal` annotation applied directly at the Entity root.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(FilterTraversal.List.class)
public @interface FilterTraversal {
    String path();
    JoinType joinType() default JoinType.LEFT;
    boolean reuse() default true;
}
```

### Scenario A: Performance Tuning (Join Customization)
Path evaluation occurs step-by-step. For a deep path like `"address.state.city.street"`, 
you can choose to force a strict `INNER JOIN` on a single specific hop to optimize database
index alignment, while allowing the preceding and trailing hops to safely 
inherit default `LEFT JOIN` safety rules.

```java
@Entity
// Overriding the exact graph hop we want to index optimize
@FilterTraversal(path = "address.state.city", joinType = JoinType.INNER)
@TextCompare(name = "byStreet", path = "address.state.city.street")
public class User {
    // ...
}
```

### Scenario B: Disabling Context Folding (Isolating Subqueries)
If you filter across multiple constraints on a collection and want them evaluated independently 
rather than unified together, flip `reuse = false` on that path. 
This splits the relational context, assigning a random `UUID` identifier under the hood to force the query
 adapter to compile a completely standalone, isolated `EXISTS` block.

```java
@Entity
// Forces each filter on 'leaves' to execute in a standalone, isolated EXISTS clause
@FilterTraversal(path = "leaves", reuse = false)
@BooleanCompare(name = "flag1", path = "leaves.flag1")
@BooleanCompare(name = "flag2", path = "leaves.flag2")
public class RootEntityWithSubselectFilters {
    // ...
}
```

* When `reuse = true` (Default): Generates `WHERE EXISTS (SELECT 1 ... WHERE flag1 = true AND flag2 = true)`. 
  This finds root records where a single relation matches both conditions.

* When `reuse = false`: Generates `WHERE EXISTS (SELECT 1 ... WHERE flag1 = true) AND EXISTS (SELECT 1 ... WHERE flag2 = true)`. 
  This finds root records where any arbitrary collection items satisfy the filters separately.


# version 26.0

## email 
💥 `EmailcheckServerIdentity` now has a `checkServerIdentity` (defaulting to true)

## downstream-maven-plugin
New: per class `outputStyle` can now be overridden by configuring `outputStyleOverrides`.

## upstream-alerts-email
New alert email template: used email templates can be simplified.


## spring-data-jpa:

### Added / Enhanced Subquery Planner:

The filtering engine now acts as a query planner. 
 
Multiple filters assigned to a reusable subselect context are automatically collected and collapsed into a single optimized `EXISTS (SELECT ...)` SQL expression rather than spawning individual independent subqueries.  

Longest Match Prefix Routing: path navigation now maps against entity-declared @FilterGroup parameters using a "longest match wins" logic. 
 
 
### Removed / Simplified QueryMode, TraversalType:
 
Removed legacy internal structures. Relational strategies are now fully declared via `@FilterGroup` configuration mappings and managed by the adapter planner.  
 
Simplified `TraversalFilter`: The interface has been completely stripped of query execution orchestration logic, leaving implementations to focus 100% on compiling criteria predicates.  
 
 
### Breaking Changes
 
* 💥 Annotation configuration: The `mode()` element has been deleted across all built-in whitelist filtering annotations. If you have classes explicitly specifying a query routing mode on the filter itself, you must remove it and replace it with `@FilterGroup`s
 
 ```java
 // old (Will not compile)
@BooleanCompare(name = "flag", path = "leaves.flag", mode = QueryMode.SUBSELECT)
```
```java
// new (Relational layout is declared once via FilterGroups)
@FilterGroup.Subselect(prefix = "leaves", reuse = true)
@BooleanCompare(name = "flag", path = "leaves.flag")
```

* 💥 `LowercaseUnderscoreSeparatedPhysicalNamingStrategy` has been removed, use hibernate `CamelCaseToUnderscoreNamingStrategy` 