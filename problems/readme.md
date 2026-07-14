# optionfactory-spring/problems

Problem data types and Exceptions for RFC-7807 like error reporting.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>problems</artifactId>
</dependency>
```

## Usage

Use the `Failure` exception to report errors:

```java
// Field error
throw Failure.field("fieldName", "errorCode", "Rejected value", "Reason");

// Generic error
throw Failure.of("errorCode", "Reason");
```


