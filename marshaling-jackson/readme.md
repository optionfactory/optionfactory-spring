# optionfactory-spring/marshaling-jackson

Jackson modules and adapters for common types and serialization quirks.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>marshaling-jackson</artifactId>
</dependency>
```

## Usage

### QuirksModule

The `QuirksModule` handles specific serialization/deserialization needs, such as boolean mapping (SI/NO) and instant formatting.

```java
JsonMapper mapper = JsonMapper.builder()
    .addModule(Quirks.defaults().build())
    .build();
```

Use `@Quirks.Bool` to map booleans to "SI"/"NO":

```java
public record MyDto(@Quirks.Bool boolean active) {}
```


