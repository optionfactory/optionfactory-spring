# optionfactory-spring/context

Property source and devtools configurations.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>context</artifactId>
</dependency>
```

## Usage

### ApplicationPropertiesConfig

Configures a `PropertySourcesPlaceholderConfigurer` that reads properties from:

1. `project.properties` from classpath (Maven filtered, git information included).
2. `${project.name}.properties` from classpath (unfiltered).
3. `~/.${project.name}.properties` (local overrides, optional).
4. `/opt/${project.name}/conf/project.properties` (env overrides, optional).

A later source overrides an earlier one.

To use it, import `ApplicationPropertiesConfig`:

```java
@Configuration
@Import(ApplicationPropertiesConfig.class)
public class MyConfig {
}
```


