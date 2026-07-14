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
        EmailMessage.Prototype emailMessagePrototype, 
        ConfigurableApplicationContext ac, 
        TaskScheduler ts) {
    return AlertsEmailsSpooler.bufferedScheduled(
        paths, 
        emailMessagePrototype, 
        ac, 
        ts, 
        Duration.ofSeconds(10), // initial delay
        Duration.ofMinutes(1),  // rate
        Duration.ofMinutes(5)   // grace period
    );
}
```


