package net.optionfactory.spring.data.jpa.filtering.filters;

import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.boot.model.FunctionContributions;

/// Registers the hibernate functions [TextSearch] renders, which have no portable criteria
/// equivalent.
///
/// It is discovered by hibernate as a `FunctionContributor` service (listed in
/// `META-INF/services`), so it needs no configuration: the functions are registered on every
/// dialect, and only rendered by the dialect they are written for.
public class TextSearchFunctions implements FunctionContributor {

    /// The postgres `@@` match operator, as the function `of_ts_matches(tsvector, tsquery)`.
    public static final String TS_MATCHES = "of_ts_matches";
    /// The prefix of the mysql `MATCH(...) AGAINST(... IN BOOLEAN MODE)` functions: one is
    /// registered per number of document columns, `of_match_against_N` taking the `N` columns
    /// followed by the query.
    public static final String MATCH_AGAINST_PREFIX = "of_match_against_";
    /// The most document paths a [TextSearch] can have on mysql, one `of_match_against_N` function
    /// being registered for each count up to it.
    public static final int MAX_DOCUMENT_PATHS = 8;

    /// @param contributions where the functions are registered
    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        final var registry = contributions.getFunctionRegistry();
        registry.registerPattern(TS_MATCHES, "((?1 @@ ?2))");
        for (int n = 1; n <= MAX_DOCUMENT_PATHS; n++) {
            final var pattern = new StringBuilder("((MATCH(?1");
            for (int i = 2; i <= n; i++) {
                pattern.append(", ?").append(i);
            }
            pattern.append(") AGAINST(?").append(n + 1).append(" IN BOOLEAN MODE)))");
            registry.registerPattern(MATCH_AGAINST_PREFIX + n, pattern.toString());
        }
    }
}
