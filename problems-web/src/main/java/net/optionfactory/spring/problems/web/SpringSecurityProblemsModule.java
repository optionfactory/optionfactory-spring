package net.optionfactory.spring.problems.web;

import java.util.List;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;

/// Built in: spring security's `AccessDeniedException`, with a problem whose details are the
/// exception's message, answered:
///
/// - `401`, with an `UNAUTHORIZED` problem, when the caller is anonymous: it must authenticate;
/// - `403`, with a `FORBIDDEN` problem, when the caller is authenticated: it is not allowed;
///
/// unless the exception's class declares another status with `@ResponseStatus`.
///
/// Answering it here means the exception thrown by a `@ResponseBody` handler, by method security
/// for instance, does not reach spring security's `ExceptionTranslationFilter`: an anonymous api
/// caller gets a `401` in the problems format, never the configured authentication entry point,
/// which in an application with form login would redirect it to the login page. No
/// `WWW-Authenticate` header is sent.
///
/// A caller is anonymous when the request has no user principal (spring security's servlet api
/// integration presents anonymous authentications that way) and the authentication on
/// `SecurityContextHolder`'s strategy, if any, is anonymous too; the second check covers
/// applications that turn the servlet api integration off.
public class SpringSecurityProblemsModule implements ProblemsModule {

    private static final AuthenticationTrustResolver TRUST = new AuthenticationTrustResolverImpl();

    /// @return the module's single classifier
    @Override
    public List<ExceptionClassifier> classifiers() {
        return List.of(SpringSecurityProblemsModule::classify);
    }

    private static @Nullable HttpStatusAndProblems classify(ExceptionClassifier.Context context, Exception ex) {
        if (!(ex instanceof AccessDeniedException ade)) {
            return null;
        }
        if (anonymous(context)) {
            return new HttpStatusAndProblems(ExceptionClassifier.annotatedStatusOr(ade, HttpStatus.UNAUTHORIZED), List.of(Problem.unauthorized(null, ade.getMessage())));
        }
        return new HttpStatusAndProblems(ExceptionClassifier.annotatedStatusOr(ade, HttpStatus.FORBIDDEN), List.of(Problem.forbidden(null, ade.getMessage())));
    }

    private static boolean anonymous(ExceptionClassifier.Context context) {
        if (context.request().getUserPrincipal() != null) {
            return false;
        }
        final var authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated() || TRUST.isAnonymous(authentication);
    }
}
