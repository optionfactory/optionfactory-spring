# optionfactory-spring/email

Email spooling, templating and inlining. This module provides a robust way to send emails by decoupling email generation from delivery using a file-system based spool.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>email</artifactId>
</dependency>
```

## Usage

### EmailSender

The `EmailSender` component is responsible for sending emails stored as `.eml` files in the spool directory.

```java
EmailPaths paths = EmailPaths.provide(Paths.get("/tmp/emails/spool"), Paths.get("/tmp/emails/sent"), Paths.get("/tmp/emails/dead"));
EmailSenderConfiguration conf = ...;
EmailSender sender = new EmailSender(paths, conf);

// Process the spool and send emails
sender.processSpool();
```

### CSS Inliner

The `CssInliner` moves the rules of `<style data-inlined>` elements into `style` attributes, for better client compatibility.
It is an `HtmlBodyPostprocessor`, usually configured on a message builder or prototype:

```java
EmailMessage.Prototype prototype = EmailMessage.builder()
        .sender("noreply@example.com", "Example")
        .subject("Welcome")
        .htmlBodyEngine(f -> f.html("/emails/", ac))
        .htmlBodyTemplate("welcome.html")
        .htmlBodyPostprocessor(new CssInliner())
        .prototype();
```

It can also be used on its own:

```java
String inlinedHtml = new CssInliner().postprocess(htmlBody);
```


