package net.optionfactory.spring.data.jpa.filtering.filters;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;

/**
 * Full-text search over one or more text properties. The searched document is
 * composed of the given {@code paths}; how the document is indexed and matched
 * depends on the database: postgres renders
 * {@code to_tsvector(language, p1 || ' ' || p2) @@ <query>}, mysql (and
 * mariadb) render {@code MATCH(p1, p2) AGAINST(<query> IN BOOLEAN MODE)}.
 *
 * <p>
 * Elements are engine-neutral:
 * <ul>
 * <li>{@code paths}: the document's fields, in order. May cross singular
 * associations on postgres; mysql {@code MATCH()} needs plain columns of the
 * root table, so association crossings are rejected at startup there.
 * Collection paths are rejected at startup on every engine.</li>
 * <li>{@code language}: the document's language, consumed where the engine
 * supports linguistics. Postgres uses it as the {@code regconfig} (stemming,
 * stopwords); mysql has no per-query language support and matches whole words
 * with collation case-folding.</li>
 * <li>{@code syntax}: how the client query text is interpreted, with identical
 * semantics on every engine. Matching <i>recall</i> is not portable: stemming
 * means postgres finds {@code cat} when searching {@code cats}, mysql does
 * not.</li>
 * </ul>
 *
 * <p>
 * Index pairing, per engine (the predicate uses the index only if the DDL
 * matches what the filter renders):
 *
 * <pre>{@code
 * // postgres, @TextSearch(paths = {"title", "body"}, language = "english")
 * CREATE INDEX by_content_fts_idx ON article
 *     USING GIN (to_tsvector('english', coalesce(title, '') || ' ' || coalesce(body, '')));
 * // postgres, @TextSearch(paths = "title", language = "english")
 * CREATE INDEX by_title_fts_idx ON article USING GIN (to_tsvector('english', coalesce(title, '')));
 * // mysql, any @TextSearch(paths = {"title", "body"})
 * CREATE FULLTEXT INDEX by_content_fts_idx ON article (title, body);
 * }</pre>
 *
 * On mysql a fulltext index is required: without one the query fails with
 * {@code Can't find FULLTEXT index matching the column list}.
 */
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(TextSearch.TextSearchFilter.class)
@Repeatable(TextSearch.RepeatableTextSearch.class)
public @interface TextSearch {

    /**
     * How the client-provided query text is interpreted. The semantics hold
     * on every supported engine.
     */
    public enum Syntax {

        /**
         * Every term must match, in any order; no client-controlled syntax.
         * The default.
         */
        PLAIN,
        /**
         * The client may use {@code "quoted phrases"}, {@code OR} between two
         * terms and {@code -term} to exclude a term.
         */
        WEBSEARCH,
        /**
         * The terms must appear adjacent and in the given order.
         */
        PHRASE;
    }

    String name();

    /**
     * The text properties composing the searched document, in order.
     */
    String[] paths();

    /**
     * The document's language: the postgres {@code regconfig} used both to
     * normalize the document and to parse the query; ignored on mysql, which
     * matches whole words with collation case-folding.
     */
    String language() default "simple";

    Syntax syntax() default Syntax.PLAIN;

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableTextSearch {

        TextSearch[] value();
    }

    public static class TextSearchFilter implements net.optionfactory.spring.data.jpa.filtering.Filter {

        private final String name;
        private final List<Traversal> traversals;
        private final String language;
        private final Syntax syntax;
        private final Support support;

        public TextSearchFilter(TextSearch annotation, EntityManagerFactory emf, EntityType<?> entity) {
            this.name = annotation.name();
            this.language = annotation.language();
            this.syntax = annotation.syntax();
            Filters.ensureConfiguration(annotation.paths().length > 0, annotation.name(), entity, "at least one path is required");
            final var collected = new ArrayList<Traversal>();
            for (String path : annotation.paths()) {
                final Traversal traversal = Filters.traversal(entity, annotation.name(), path);
                Filters.ensurePropertyOfAnyType(entity, annotation.name(), traversal, String.class);
                Filters.ensureConfiguration(traversal.group() == null, annotation.name(), entity, "path %s crosses a collection, not supported by @TextSearch".formatted(path));
                collected.add(traversal);
            }
            this.traversals = List.copyOf(collected);
            this.support = Support.of(emf, annotation.name(), entity, this.traversals);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            Filters.ensure(values.length == 1, root, name, "expected query, got %s", Arrays.toString(values));
            final String text = values[0];
            Filters.ensure(text != null && !text.isBlank(), root, name, "query cannot be null or blank");
            final var paths = new ArrayList<Path<String>>(traversals.size());
            for (Traversal traversal : traversals) {
                paths.add(Filters.path(root, name, traversal));
            }
            return support.matches(builder, paths, text, syntax, language);
        }

        private interface Support {

            Predicate matches(CriteriaBuilder builder, List<Path<String>> documentPaths, String text, Syntax syntax, String language);

            static Support of(EntityManagerFactory emf, String filterName, EntityType<?> entity, List<Traversal> traversals) {
                final var dialect = emf.unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect();
                if (dialect instanceof PostgreSQLDialect) {
                    return new PostgresSupport();
                }
                if (dialect instanceof MySQLDialect) {
                    Filters.ensureConfiguration(traversals.size() <= TextSearchFunctions.MAX_DOCUMENT_PATHS, filterName, entity, "at most %s paths are supported by @TextSearch on mysql, got %s".formatted(TextSearchFunctions.MAX_DOCUMENT_PATHS, traversals.size()));
                    for (Traversal traversal : traversals) {
                        Filters.ensureConfiguration(traversal.steps().stream().noneMatch(step -> step.type() != null), filterName, entity, "path %s crosses an association, not supported by @TextSearch on mysql".formatted(traversal.leaf()));
                    }
                    return new MysqlSupport();
                }
                throw new InvalidFilterConfiguration(filterName, entity, "@TextSearch requires postgres or mysql, got dialect %s".formatted(dialect.getClass().getSimpleName()));
            }
        }

        private static class PostgresSupport implements Support {

            @Override
            public Predicate matches(CriteriaBuilder builder, List<Path<String>> documentPaths, String text, Syntax syntax, String language) {
                Expression<String> document = null;
                for (Path<String> path : documentPaths) {
                    final Expression<String> piece = builder.coalesce(path, builder.literal(""));
                    document = document == null ? piece : builder.concat(builder.concat(document, builder.literal(" ")), piece);
                }
                final var tsvector = builder.function("to_tsvector", Object.class, builder.literal(language), document);
                final var tsquery = builder.function(queryFunction(syntax), Object.class, builder.literal(language), builder.literal(text));
                return builder.isTrue(builder.function(TextSearchFunctions.TS_MATCHES, Boolean.class, tsvector, tsquery));
            }

            private static String queryFunction(Syntax syntax) {
                return switch (syntax) {
                    case PLAIN -> "plainto_tsquery";
                    case WEBSEARCH -> "websearch_to_tsquery";
                    case PHRASE -> "phraseto_tsquery";
                };
            }
        }

        private static class MysqlSupport implements Support {

            @Override
            public Predicate matches(CriteriaBuilder builder, List<Path<String>> documentPaths, String text, Syntax syntax, String language) {
                final String booleanModeQuery = booleanModeQuery(syntax, text);
                final var arguments = new Expression<?>[documentPaths.size() + 1];
                for (int i = 0; i < documentPaths.size(); i++) {
                    arguments[i] = documentPaths.get(i);
                }
                arguments[documentPaths.size()] = builder.literal(booleanModeQuery);
                final var functionName = TextSearchFunctions.MATCH_AGAINST_PREFIX + documentPaths.size();
                return builder.isTrue(builder.function(functionName, Boolean.class, arguments));
            }

            /**
             * Translates the engine-neutral syntax to a mysql boolean mode
             * query. Every term is double-quoted, so operators inside client
             * text are literal; required terms get {@code +}, excluded ones
             * {@code -}, and the two operands of a websearch {@code OR} lose
             * their {@code +} (juxtaposition is mysql's OR).
             */
            static String booleanModeQuery(Syntax syntax, String text) {
                return switch (syntax) {
                    case PLAIN -> {
                        final var sb = new StringBuilder();
                        for (String term : text.strip().split("\\s+")) {
                            if (term.isBlank()) {
                                continue;
                            }
                            if (!sb.isEmpty()) {
                                sb.append(' ');
                            }
                            sb.append("+\"").append(stripQuotes(term)).append('"');
                        }
                        yield sb.toString();
                    }
                    case PHRASE ->
                        "+\"" + stripQuotes(text.strip()) + "\"";
                    case WEBSEARCH -> {
                        final var terms = new ArrayList<Term>();
                        boolean excluded = false;
                        boolean orNext = false;
                        for (var part : tokenize(text)) {
                            if (part.text().equals("-")) {
                                excluded = true;
                            } else if (!part.quoted() && part.text().equals("OR")) {
                                if (!terms.isEmpty()) {
                                    terms.getLast().orLinked = true;
                                }
                                orNext = true;
                                excluded = false;
                            } else {
                                final var term = new Term(part.text(), excluded);
                                term.orLinked = orNext;
                                terms.add(term);
                                excluded = false;
                                orNext = false;
                            }
                        }
                        final var sb = new StringBuilder();
                        for (Term term : terms) {
                            if (!sb.isEmpty()) {
                                sb.append(' ');
                            }
                            sb.append(term.excluded ? "-" : (term.orLinked ? "" : "+")).append('"').append(stripQuotes(term.text)).append('"');
                        }
                        yield sb.toString();
                    }
                };
            }

            private static String stripQuotes(String text) {
                return text.replace("\"", " ").strip();
            }

            private record Part(String text, boolean quoted) {

            }

            private static final class Term {

                final String text;
                final boolean excluded;
                boolean orLinked;

                Term(String text, boolean excluded) {
                    this.text = text;
                    this.excluded = excluded;
                }
            }

            /**
             * Splits websearch text into whitespace-separated parts, treating
             * double-quoted segments as single parts and a {@code -} attached
             * to the next part as an exclusion marker.
             */
            private static List<Part> tokenize(String text) {
                final var parts = new ArrayList<Part>();
                var current = new StringBuilder();
                boolean inQuote = false;
                for (int i = 0; i < text.length(); i++) {
                    final char c = text.charAt(i);
                    if (inQuote) {
                        if (c == '"') {
                            inQuote = false;
                            parts.add(new Part(current.toString(), true));
                            current.setLength(0);
                        } else {
                            current.append(c);
                        }
                        continue;
                    }
                    if (c == '"') {
                        if (!current.isEmpty()) {
                            parts.add(new Part(current.toString(), false));
                            current.setLength(0);
                        }
                        inQuote = true;
                        continue;
                    }
                    if (Character.isWhitespace(c)) {
                        if (!current.isEmpty()) {
                            parts.add(new Part(current.toString(), false));
                            current.setLength(0);
                        }
                        continue;
                    }
                    if (c == '-' && current.isEmpty()) {
                        parts.add(new Part("-", false));
                        continue;
                    }
                    current.append(c);
                }
                if (inQuote || !current.isEmpty()) {
                    parts.add(new Part(current.toString(), inQuote));
                }
                return parts;
            }
        }
    }

    public enum Filter {

        INSTANCE;

        public String[] of(String query) {
            return new String[]{query};
        }
    }

}
