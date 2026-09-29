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

### 1. Annotate your enums

```java
@LocalizedEnum(category = "order-status")
public enum OrderStatus {
    PENDING, SHIPPED, DELIVERED
}
```

`category` is optional: without it the category is the enum's simple name (`OrderStatus`).

### 2. Translate them in a resource bundle

Each constant is looked up under `<prefix>.<category>.<NAME>`:

```properties
# localization_it.properties
enums.order-status.PENDING=In attesa
enums.order-status.SHIPPED=Spedito
enums.order-status.DELIVERED=Consegnato
```

### 3. Configure the localization service

```java
@Bean
public EnumsLocalizationService enumsLocalizationService() {
    final var source = new ResourceBundleMessageSource();
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    source.setBasenames("localization");
    return new ResourceBundleEnumsLocalizationService("enums", source, OrderStatus.class, ResolutionMode.MISSING_AS_NAME);
}
```

- `prefix` (`"enums"`) is the first segment of every bundle key.
- `root` is a class whose package, subpackages included, is scanned for `@LocalizedEnum` enums at construction.
- `mode` decides what a missing translation resolves to: `MISSING_AS_NAME` answers the constant's name (`SHIPPED`), `MISSING_AS_NULL` answers nothing. With `setUseCodeAsDefaultMessage(true)` on the message source, `MISSING_AS_NULL` answers the bundle key instead.

### 4. Use it

```java
// one value
Optional<String> label = les.value(EnumKey.of("order-status", "SHIPPED"), locale);
// every constant of an enum, in declaration order
List<LocalizedEnumResponse> statuses = les.values((Class<Enum<?>>) (Class<?>) OrderStatus.class, locale);
// every constant of a category, or of every scanned enum with Optional.empty()
List<LocalizedEnumResponse> all = les.values(Optional.empty(), locale);
```

`LocalizedEnumResponse` is a `(category, name, value)` record, ready to be serialized for a client (a select's options, a translations endpoint).

`values(Class, Locale)` also accepts an enum that is not annotated, and not scanned: its category is its simple name.

### 5. Thymeleaf (optional)

Expose the `LocalizedEnums` functions through the `SingletonDialect` of [thymeleaf](../thymeleaf/readme.md):

```java
@Bean
public SingletonDialect localizedEnumsDialect(EnumsLocalizationService les) {
    return SingletonDialect.of("enums", new LocalizedEnums(les));
}
```

or, on a template engine configured by hand, `templateEngine.addDialect(SingletonDialect.of("enums", new LocalizedEnums(les)))`.

The functions resolve in the current request locale (`LocaleContextHolder`):

| function | answers |
|---|---|
| `#enums.value(category, name)` | the label of one constant; throws `NoSuchElementException` when it is missing and the mode is `MISSING_AS_NULL` |
| `#enums.values(category)` | the `LocalizedEnumResponse`s of a category, in declaration order |
| `#enums.values(enumClass)` | the same, for an enum class |
| `#enums.in(localizedEnum, collection)` | whether the collection holds a constant with the same name |

```html
<span th:text="${#enums.value('order-status', order.status.name())}"></span>

<select name="statuses" multiple>
    <option th:each="s : ${#enums.values('order-status')}"
            th:value="${s.name}"
            th:selected="${#enums.in(s, filter.statuses)}"
            th:text="${s.value}"></option>
</select>
```
