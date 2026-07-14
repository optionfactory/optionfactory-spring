# optionfactory-spring/marshaling-jaxb

JAXB `XmlAdapter`s for Temporals and Money.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>marshaling-jaxb</artifactId>
</dependency>
```

## Usage

Use `@XmlJavaTypeAdapter` to use the provided adapters:

```java
public class MyBean {
    @XmlJavaTypeAdapter(XsdDateToLocalDate.class)
    public LocalDate at;
    
    @XmlJavaTypeAdapter(XsdDateTimeToInstant.class)
    public Instant timestamp;
}
```

Available adapters in `net.optionfactory.spring.marshaling.jaxb.time`:
- `XsdDateToLocalDate`
- `XsdDateTimeToInstant`
- `XsdDateTimeToLocalDateTime`
- `XsdDateTimeToOffsetDateTime`
- `XsdTimeToLocalTime`


