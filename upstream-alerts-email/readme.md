# optionfactory-spring/upstream-alerts-email

An upstream interceptor buffering and sending emails when alerts are generated.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>upstream-alerts-email</artifactId>
</dependency>
```

Note: both `net.optionfactory.spring:upstream` and `net.optionfactory.spring:email` are transitive dependencies.

## Usage

Configure the buffered scheduled spooler for upstream alerts in your configuration:

```java
@Bean
public BufferedScheduledSpooler<UpstreamAlertEvent> alertsEmailsSpooler(
        EmailPaths paths,
        ConfigurableApplicationContext ac,
        TaskScheduler ts) {
    final var prototype = EmailMessage.builder()
            .sender("noreply@example.com", "Alerts")
            .recipient("integrations@example.com")
            .subject("Integration alerts")
            .htmlBodyEngine(AlertsEmailsSpooler.templateEngine("/emails/", ac))
            .htmlBodyTemplate("alerts.html")
            .htmlBodyPostprocessor(new CssInliner())
            .prototype();
    return AlertsEmailsSpooler.builder(paths, ac, ts)
            .initialDelay(Duration.ofSeconds(10))
            .rate(Duration.ofMinutes(5))
            .gracePeriod(Duration.ofSeconds(5))
            .bufferedScheduled(prototype);
}
```

`initialDelay`, `rate` and `gracePeriod` are optional; the values shown are the defaults.

The template receives the alerts as the `alerts` variable and usually includes the shipped layout
(`AlertsEmailsSpooler.LAYOUT`) as `~{alert-layout.html :: alerts(...)}`. The prototype needs no
spooling configuration: the builder adds it.

To address each integration to its own owners, pass `bufferedScheduled` a selector from the upstream
name to a prototype instead; the selector must return the same prototype instance for a given
upstream, since alerts are grouped into emails by prototype identity.
