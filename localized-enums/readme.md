# optionfactory-spring/localized-enums

Declarative, annotation + resource bundle based enum localization support.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>localized-enums</artifactId>
</dependency>
```

## Usage

### 1. Annotate your Enum

```java
@LocalizedEnum(category = "order-status")
public enum OrderStatus {
    PENDING, SHIPPED, DELIVERED
}
```

### 2. Configure the Localization Service

```java
@Bean
public EnumsLocalizationService enumsLocalizationService() {
    return new ResourceBundleEnumsLocalizationService(
        ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        "messages/enums"
    );
}
```

### 3. Integrate with Thymeleaf (optional)

```java
@Bean
public SingletonDialect localizedEnumsDialect(EnumsLocalizationService service) {
    return SingletonDialect.of("enums", new net.optionfactory.spring.localizedenums.dialects.LocalizedEnums(service));
}
```

Then in Thymeleaf:

```html
<span th:text="${#enums.describe(order.status)}"></span>
```


