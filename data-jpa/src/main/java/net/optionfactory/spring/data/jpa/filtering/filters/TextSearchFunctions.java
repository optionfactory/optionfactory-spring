package net.optionfactory.spring.data.jpa.filtering.filters;

import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.boot.model.FunctionContributions;

public class TextSearchFunctions implements FunctionContributor {

    public static final String TS_MATCHES = "of_ts_matches";
    public static final String MATCH_AGAINST_PREFIX = "of_match_against_";
    public static final int MAX_DOCUMENT_PATHS = 8;

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
