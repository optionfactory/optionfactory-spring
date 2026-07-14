# optionfactory-spring/thymeleaf

`SingletonDialect` for Thymeleaf, allowing to easily expose beans as expression objects.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>thymeleaf</artifactId>
</dependency>
```

## Usage

### SingletonDialect

Register a bean as a Thymeleaf expression object:

```java
@Bean
public SingletonDialect myDialect() {
    return SingletonDialect.of("myutils", new MyUtils());
}
```

Then use it in your Thymeleaf templates:

```html
<span th:text="${#myutils.format(value)}"></span>
```

### Money Dialect

The module also provides a `Money` dialect:

```java
@Bean
public SingletonDialect moneyDialect() {
    return SingletonDialect.of("money", new net.optionfactory.spring.thymeleaf.dialects.Money());
}
```


