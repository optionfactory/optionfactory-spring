package net.optionfactory.spring.data.jpa.filtering.psql;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.TextSearch;
import net.optionfactory.spring.data.jpa.filtering.filters.TextSearch.Syntax;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import net.optionfactory.spring.data.jpa.test.containers.SharedContainer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/// Shows the postgres full-text search filter together with the GIN expression
/// indexes that make it fast: the index expression must be exactly the one the
/// filter renders, with the same language, the same paths in the same order,
/// and null-safe concatenation.
@SharedContainer(HibernateOnPsqlTestConfig.Postgres.class)
@SpringJUnitConfig(HibernateOnPsqlTestConfig.class)
@TransactionalPhases
public class TextSearchOnPsqlTest {

    @Entity
    @TextSearch(name = "byContent", paths = {"title", "body"}, language = "english")
    @TextSearch(name = "byFreeText", paths = {"title", "body"}, language = "english", syntax = Syntax.WEBSEARCH)
    @TextSearch(name = "byTitle", paths = "title", language = "italian", syntax = Syntax.PHRASE)
    public static class Article {

        @Id
        public long id;
        public String title;
        public String body;

    }

    public interface ArticlesRepository extends JpaRepository<Article, Long>, WhitelistFilteringRepository<Article> {

    }

    @Inject
    private ArticlesRepository articles;

    @PersistenceContext
    private EntityManager em;

    /// The full-text indexes cannot be declared via `@Index`: jakarta.persistence's `columnList`
    /// only accepts mapped column names (no `tsvector()` expression) and cannot request the GIN
    /// access method (everything is rendered as btree). In production they belong in a
    /// Flyway/Liquibase migration; hbm2ddl import scripts would not run either, being
    /// create/create-drop only, while this suite boots with "update". The DDL must mirror the
    /// expression the filter renders, same language, same paths, same order.
    @BeforeEach
    public void setup() {
        articles.deleteAll();
        em.createNativeQuery("CREATE INDEX IF NOT EXISTS article_by_content_fts_idx ON text_search_on_psql_test$article USING GIN (to_tsvector('english', coalesce(title, '') || ' ' || coalesce(body, '')))").executeUpdate();
        em.createNativeQuery("CREATE INDEX IF NOT EXISTS article_by_title_fts_idx ON text_search_on_psql_test$article USING GIN (to_tsvector('italian', coalesce(title, '')))").executeUpdate();
        save(1, "The cats were running", "quickly and loudly");
        save(2, "Dogs bark", null);
        save(3, "Cani randagi", null);
        save(4, "Cani e gatti", null);
        save(5, "O'Brien's cats", null);
    }

    private void save(long id, String title, String body) {
        final var a = new Article();
        a.id = id;
        a.title = title;
        a.body = body;
        articles.save(a);
    }

    /// @return the ids of the matching articles, sorted: the query itself has no order
    private List<Long> search(String filter, String query) {
        final var fr = FilterRequest.builder()
                .textSearch(filter, query)
                .build();
        final Page<Article> page = articles.findAll(null, fr, Pageable.unpaged());
        return page.getContent().stream().map(a -> a.id).sorted().toList();
    }

    @Test
    public void plainMatchesStemmedTermsAcrossAllPaths() {
        Assertions.assertEquals(List.of(1L), search("byContent", "cats running"), "PLAIN matches stemmed terms of the title");
        Assertions.assertEquals(List.of(1L), search("byContent", "running loudly"), "PLAIN matches terms spread across title and body");
    }

    @Test
    public void plainRequiresEveryTerm() {
        Assertions.assertEquals(List.of(), search("byContent", "cats sheepdog"), "PLAIN requires every term to match");
    }

    @Test
    public void websearchSupportsOrQuotedPhrasesAndNegation() {
        Assertions.assertEquals(List.of(1L, 2L, 5L), search("byFreeText", "cats OR dogs"), "WEBSEARCH OR keeps the articles matching either term");
        Assertions.assertEquals(List.of(5L), search("byFreeText", "cats -running"), "WEBSEARCH -term excludes the articles matching it");
        Assertions.assertEquals(List.of(1L), search("byFreeText", "\"were running\""), "a WEBSEARCH quoted phrase matches its terms adjacent");
    }

    @Test
    public void phraseRequiresTermsToBeAdjacent() {
        Assertions.assertEquals(List.of(3L), search("byTitle", "cani randagi"), "PHRASE matches its terms adjacent and in order");
        Assertions.assertEquals(List.of(), search("byTitle", "cani gatti"), "PHRASE does not match terms that are not adjacent");
    }

    @Test
    public void quotesInTheQueryAreEscaped() {
        Assertions.assertEquals(List.of(5L), search("byContent", "O'Brien's"), "quotes in the client text are bound as data, not parsed as syntax");
    }

    @Test
    public void contentPredicateUsesTheExpressionIndex() {
        em.createNativeQuery("SET LOCAL enable_seqscan = off").executeUpdate();
        final var planText = explainRenderedPredicate("byContent", "cats running");
        Assertions.assertTrue(planText.contains("article_by_content_fts_idx"), planText);
    }

    @Test
    public void titlePredicateUsesTheExpressionIndex() {
        em.createNativeQuery("SET LOCAL enable_seqscan = off").executeUpdate();
        final var planText = explainRenderedPredicate("byTitle", "cani randagi");
        Assertions.assertTrue(planText.contains("article_by_title_fts_idx"), planText);
    }

    /// EXPLAINs the SQL the criteria predicate actually renders, not a
    /// hand-written lookalike: if the dialect or the literal handling changes
    /// enough to stop matching the index expression, this is where it shows.
    private String explainRenderedPredicate(String filter, String query) {
        final var sqls = CapturingStatementInspector.capture(() -> search(filter, query));
        final var select = sqls.stream()
                .filter(sql -> sql.toLowerCase(Locale.ROOT).startsWith("select"))
                .filter(sql -> sql.contains("to_tsvector"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no rendered text-search select in " + sqls));
        final var plan = (List<?>) em.createNativeQuery("EXPLAIN (COSTS OFF) " + select).getResultList();
        return plan.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("\n"));
    }
}
