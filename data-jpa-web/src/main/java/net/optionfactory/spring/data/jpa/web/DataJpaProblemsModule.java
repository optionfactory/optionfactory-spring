package net.optionfactory.spring.data.jpa.web;

import java.util.List;
import net.optionfactory.spring.data.jpa.web.filtering.FilteringExceptionClassifier;
import net.optionfactory.spring.problems.web.ExceptionClassifier;
import net.optionfactory.spring.problems.web.ProblemsModule;

/// Everything `data-jpa-web` contributes to `problems-web`'s rest resolver: today, answering the
/// filter and sort requests a repository rejects as bad requests rather than as unexpected errors
/// (see [FilteringExceptionClassifier]).
///
/// Register it where the rest resolver is configured:
///
/// ```java
/// ExceptionResolvers.configurer(resolvers)
///         .rest(jsonMapper, rest -> rest.withModule(new DataJpaProblemsModule()))
///         .configure();
/// ```
///
/// `problems-web` is an optional dependency of this module: applications not using it never load
/// this class.
public class DataJpaProblemsModule implements ProblemsModule {

    @Override
    public List<ExceptionClassifier> classifiers() {
        return List.of(new FilteringExceptionClassifier());
    }
}
