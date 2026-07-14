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

The `CssInliner` can be used to inline CSS into HTML email bodies for better client compatibility.

```java
CssInliner inliner = new CssInliner();
String inlinedHtml = inliner.inline(htmlBody);
```


