package net.optionfactory.spring.upstream.alerts.spooler;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailPaths;
import net.optionfactory.spring.email.spooling.BufferedScheduledSpooler;
import net.optionfactory.spring.email.spooling.Spooler;
import net.optionfactory.spring.upstream.alerts.UpstreamAlertEvent;
import net.optionfactory.spring.thymeleaf.SingletonDialect;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.Assert;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/// Renders batches of intercepted {@link UpstreamAlertEvent}s into email files and
/// writes them to the spool directory of an {@link EmailPaths} — *spooling* here
/// means exactly that hand-off to disk. Nothing is delivered by this class: an
/// email sender polls the spool independently, sends what it finds and moves each
/// file on to the `sent` or `dead` directory.
///
/// A batch is grouped by the {@link EmailMessage.Prototype} its alerts resolve to,
/// and each group becomes one email. The prototype is what makes two emails
/// different — its recipients, subject and template — so it is the only thing worth
/// grouping by: alerts sharing a prototype belong in one email, and alerts with
/// different prototypes cannot share one, because a single email carries a single
/// recipient set and recipients are never templated.
///
/// ## Choosing the prototype
///
/// `bufferedScheduled(prototype)` sends every alert to one prototype, which is the
/// right choice when all integration failures go to the same people; it is simply
/// the degenerate case where the whole batch resolves to a single group.
/// `bufferedScheduled(prototypeByUpstream)` resolves the prototype from
/// `invocation().endpoint().upstream()`, so each integration can be addressed to its
/// own owners and a failure of one upstream is not mailed to the owners of another.
///
/// ### The selector must be stable and total
///
/// Prototypes are grouped by identity, because {@link EmailMessage.Prototype} does
/// not define equality. A selector must therefore return *the same instance* every
/// time it is asked about a given upstream — resolve the prototypes once and look
/// them up, rather than building one per call, or every alert lands in a group of
/// its own and one email per alert is spooled. Returning the same instance for
/// several upstreams is how co-owned integrations get batched into a single email
/// instead of near-duplicates to the same people.
///
/// The selector should also be defined for every upstream it may be asked about:
/// returning a fallback prototype keeps the alert, whereas throwing or returning
/// `null` discards it.
///
/// ## Failures are isolated, and best-effort
///
/// {@link BufferedScheduledSpooler} drains its buffer *before* calling
/// {@link #spool}, and there is no retry or re-queue, so anything that escapes
/// `spool` discards that whole batch of alerts permanently. Nothing is allowed to
/// escape: an alert whose prototype cannot be resolved is dropped on its own, and a
/// group that fails to marshal is dropped without affecting the other groups. Both
/// are logged at WARN.
///
/// Failures are never retried, so a persistent template or configuration error
/// silently disables alert delivery for the affected groups until it is fixed.
/// Monitor the `[spool-emails][alerts]` WARN lines: they are the only signal that
/// alerts are being discarded.
///
/// ## Ordering
///
/// Alerts keep their encounter order within each email, so a single email reads
/// chronologically. Groups are spooled in the order their prototype is first
/// resolved in the batch.
public class AlertsEmailsSpooler implements Spooler<List<UpstreamAlertEvent>> {

    private final Logger logger = LoggerFactory.getLogger(AlertsEmailsSpooler.class);
    private final Function<String, EmailMessage.Prototype> prototypes;

    /// Prefer {@link #builder}, which applies the spool configuration for you.
    /// Prototypes given here must already carry `spooling(...)`, since
    /// {@link #spool} marshals straight to the spool directory.
    ///
    /// @param prototypeByUpstream stable, total selector, see the class documentation
    public AlertsEmailsSpooler(Function<String, EmailMessage.Prototype> prototypeByUpstream) {
        Assert.notNull(prototypeByUpstream, "prototypeByUpstream must be non null");
        this.prototypes = prototypeByUpstream;
    }

    @Override
    public List<Path> spool(List<UpstreamAlertEvent> alerts) {
        final var spooled = new ArrayList<Path>();
        for (var group : groupByPrototype(alerts).entrySet()) {
            final var path = spoolGroup(group.getKey(), group.getValue());
            if (path != null) {
                spooled.add(path);
            }
        }
        return List.copyOf(spooled);
    }

    /// Resolving the prototype happens here rather than while spooling, so a
    /// selector that throws costs only the alert it was asked about instead of the
    /// whole drained batch.
    private LinkedHashMap<EmailMessage.Prototype, List<UpstreamAlertEvent>> groupByPrototype(List<UpstreamAlertEvent> alerts) {
        final var groups = new LinkedHashMap<EmailMessage.Prototype, List<UpstreamAlertEvent>>();
        for (var alert : alerts) {
            final var upstream = alert.invocation().endpoint().upstream();
            EmailMessage.Prototype prototype;
            try {
                prototype = prototypes.apply(upstream);
            } catch (RuntimeException ex) {
                logger.warn("[spool-emails][alerts] failed to resolve a prototype for upstream {}, dropping its alert", upstream, ex);
                continue;
            }
            if (prototype == null) {
                logger.warn("[spool-emails][alerts] no prototype for upstream {}, dropping its alert", upstream);
                continue;
            }
            groups.computeIfAbsent(prototype, p -> new ArrayList<>()).add(alert);
        }
        return groups;
    }

    private Path spoolGroup(EmailMessage.Prototype prototype, List<UpstreamAlertEvent> group) {
        try {
            final var p = prototype.builder()
                    .variable("alerts", group)
                    .marshalToSpool();
            logger.info("[spool-emails][alerts] spooled {} with {} alerts", p.getFileName(), group.size());
            return p;
        } catch (RuntimeException ex) {
            logger.warn("[spool-emails][alerts] failed to dump email for {} alerts", group.size(), ex);
            return null;
        }
    }

    /// The layout this module ships, which an alert template includes as
    /// `~{alert-layout.html :: alerts(...)}`.
    public static final String LAYOUT = "alert-layout.html";

    /// Builds a template engine that can render an alert template kept anywhere on
    /// the classpath, not only alongside the layout.
    ///
    /// A Thymeleaf fragment reference is resolved against the engine's resolvers, so
    /// an engine holding a single resolver rooted at the application's own prefix
    /// cannot find {@link #LAYOUT}, which this module ships under `/email/`. That
    /// forces the including template to live under `/email/` too. The engine returned
    /// here resolves the layout from this module and everything else from `prefix`,
    /// so an application is free to keep its alert template wherever its other
    /// templates live.
    ///
    /// Resolution is decided by resolvable patterns rather than by trying and failing,
    /// so no existence checks are involved: only {@link #LAYOUT} reaches the module's
    /// resolver, and every other name reaches the application's.
    ///
    /// The `bodies` expression object the layout needs is registered for you.
    ///
    /// @param prefix the classpath prefix holding the application's alert template
    /// @param ms message source for `#{...}` lookups, may be null
    /// @param dialects further dialects to register
    /// @return an engine that resolves both the application's templates and the layout
    public static SpringTemplateEngine templateEngine(String prefix, @Nullable MessageSource ms, IDialect... dialects) {
        final var engine = new SpringTemplateEngine();
        engine.addTemplateResolver(resolver(1, "/email/", Set.of(LAYOUT)));
        engine.addTemplateResolver(resolver(2, prefix, Set.of("*.html")));
        engine.setTemplateEngineMessageSource(ms);
        engine.addDialect(new SingletonDialect("bodies", new AlertBodiesFunctions()));
        for (IDialect dialect : dialects) {
            engine.addDialect(dialect);
        }
        return engine;
    }

    private static ClassLoaderTemplateResolver resolver(int order, String prefix, Set<String> patterns) {
        final var resolver = new ClassLoaderTemplateResolver();
        resolver.setOrder(order);
        resolver.setResolvablePatterns(patterns);
        resolver.setPrefix(prefix);
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("utf-8");
        resolver.setCacheable(true);
        return resolver;
    }

    /// Starts configuring a spooler and the {@link BufferedScheduledSpooler} that
    /// feeds it.
    ///
    /// @param paths where emails are spooled to
    /// @param applicationContext publishes spooling events and backs template expressions
    /// @param scheduler runs the periodic drain
    /// @return a builder whose only remaining options are the three durations
    public static Builder builder(EmailPaths paths, ConfigurableApplicationContext applicationContext, TaskScheduler scheduler) {
        return new Builder(paths, applicationContext, scheduler);
    }

    /// Configures the {@link BufferedScheduledSpooler} that feeds the spooler.
    /// Everything mandatory is a parameter of {@link #builder} or of
    /// `bufferedScheduled`, so a misconfigured builder does not compile; the three
    /// durations are the only options, defaulting to the values every optionfactory
    /// application uses.
    public static class Builder {

        private final EmailPaths paths;
        private final ConfigurableApplicationContext applicationContext;
        private final TaskScheduler scheduler;
        private Duration initialDelay = Duration.ofSeconds(10);
        private Duration rate = Duration.ofMinutes(5);
        private Duration gracePeriod = Duration.ofSeconds(5);

        private Builder(EmailPaths paths, ConfigurableApplicationContext applicationContext, TaskScheduler scheduler) {
            Assert.notNull(paths, "paths must be non null");
            Assert.notNull(applicationContext, "applicationContext must be non null");
            Assert.notNull(scheduler, "scheduler must be non null");
            this.paths = paths;
            this.applicationContext = applicationContext;
            this.scheduler = scheduler;
        }

        /// Delays the first drain, so startup noise is not mailed immediately.
        ///
        /// @param initialDelay how long after startup the first drain runs, default 10 seconds
        /// @return this builder
        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        /// Sets how often the buffer is checked for alerts to spool.
        ///
        /// @param rate how often the buffer is drained, default 5 minutes
        /// @return this builder
        public Builder rate(Duration rate) {
            this.rate = rate;
            return this;
        }

        /// Throttles drains, so a burst of failures becomes one email rather than many.
        ///
        /// @param gracePeriod the minimum interval between two drains, default 5 seconds
        /// @return this builder
        public Builder gracePeriod(Duration gracePeriod) {
            this.gracePeriod = gracePeriod;
            return this;
        }

        /// Every alert in a batch becomes one email.
        ///
        /// @param prototype the email all alerts are spooled as
        /// @return the buffered spooler, already listening for {@link UpstreamAlertEvent}s
        public BufferedScheduledSpooler<UpstreamAlertEvent> bufferedScheduled(EmailMessage.Prototype prototype) {
            Assert.notNull(prototype, "prototype must be non null");
            return bufferedScheduled(upstream -> prototype);
        }

        /// One email per distinct prototype the selector returns.
        ///
        /// @param prototypeByUpstream stable, total selector, see the class documentation
        /// @return the buffered spooler, already listening for {@link UpstreamAlertEvent}s
        public BufferedScheduledSpooler<UpstreamAlertEvent> bufferedScheduled(Function<String, EmailMessage.Prototype> prototypeByUpstream) {
            Assert.notNull(prototypeByUpstream, "prototypeByUpstream must be non null");
            // memoized: grouping keys on prototype identity, so the spooling wrapper
            // must return the same instance for a given prototype every time
            final var spoolable = new ConcurrentHashMap<EmailMessage.Prototype, EmailMessage.Prototype>();
            final Function<String, EmailMessage.Prototype> spooling = upstream -> {
                final var prototype = prototypeByUpstream.apply(upstream);
                return prototype == null ? null : spoolable.computeIfAbsent(prototype, pr -> pr.builder().spooling(paths, "alerts.", applicationContext).prototype());
            };
            return new BufferedScheduledSpooler<>(
                    UpstreamAlertEvent.class,
                    applicationContext,
                    scheduler,
                    initialDelay,
                    rate,
                    gracePeriod,
                    new AlertsEmailsSpooler(spooling)
            );
        }
    }

}
