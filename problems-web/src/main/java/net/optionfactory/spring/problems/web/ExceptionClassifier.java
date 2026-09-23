package net.optionfactory.spring.problems.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.web.method.HandlerMethod;

/// Classifies an exception the resolver does not handle itself, answering the status and the problems
/// to report it with.
///
/// This is how a library whose exceptions describe a bad request gets them answered as one — and
/// logged as one — without either module depending on the other: the library ships a classifier, and
/// the application registers it with `RestExceptionResolver.Builder#withClassifier`, or together with
/// everything else the library contributes, as a [ProblemsModule], with
/// `RestExceptionResolver.Builder#withModule`.
///
/// Classifiers are consulted in registration order, only for exceptions outside the resolver's
/// built-in cases (a `Failure` or a binding error, for instance, is never offered to them), and
/// before the resolver falls back to spring's defaults and to reporting an unexpected error. A
/// classified exception is logged at `DEBUG`, like every other client error. The configured
/// [FailureTransformer]s still run on what a classifier returns, so details are still omitted in
/// production.
///
/// A [FailureTransformer] cannot do this job: it transforms a failure after the resolver has
/// classified it, and by then an unknown exception has already been logged as an error.
public interface ExceptionClassifier {

    /// @param context the request being answered, and what the resolver localizes with
    /// @param ex the exception to classify
    /// @return the status and problems to answer with, or `null` to decline, leaving the exception
    ///         to the next classifier and eventually to the resolver's defaults
    @Nullable
    HttpStatusAndProblems classify(Context context, Exception ex);

    /// What a classifier is given besides the exception: the request being answered, and the message
    /// source and locale the resolver localizes with. A parameter object rather than a list of
    /// parameters, so that it can grow without breaking the classifiers already written.
    ///
    /// @param request the current request
    /// @param response the current response
    /// @param handler the handler method that threw
    /// @param messages the resolver's message source, the application's own falling back to the
    ///        validation messages
    /// @param locale the locale of the current request
    public record Context(HttpServletRequest request, HttpServletResponse response, HandlerMethod handler, MessageSource messages, Locale locale) {

        /// Resolves a message in the current locale, falling back to `defaultMessage` when the code
        /// is unknown.
        ///
        /// @param code the message code
        /// @param defaultMessage the message to use when the code is unknown
        /// @return the localized message
        public String localized(String code, String defaultMessage) {
            return messages.getMessage(code, null, defaultMessage, locale);
        }
    }

}
