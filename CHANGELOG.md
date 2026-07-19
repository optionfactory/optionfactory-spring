# version 26.0

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
 
* Annotation configuration: The `mode()` element has been deleted across all built-in whitelist filtering annotations. If you have classes explicitly specifying a query routing mode on the filter itself, you must remove it:  
 
 ```java
 // old (Will not compile)
@BooleanCompare(name = "flag", path = "leaves.flag", mode = QueryMode.SUBSELECT)
```
```java
// new (Relational layout is declared once via FilterGroups)
@FilterGroup.Subselect(prefix = "leaves", reuse = true)
@BooleanCompare(name = "flag", path = "leaves.flag")
```

* `LowercaseUnderscoreSeparatedPhysicalNamingStrategy` has been removed, use hibernate `CamelCaseToUnderscoreNamingStrategy` 