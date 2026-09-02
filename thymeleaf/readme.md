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

### VersionedResourceDialect

This dialect adds a versioning attribute to `href` or `src` attributes to ensure that resources are not cached by the browser across deployments.

You can make this dialect available by adding it to the Thymeleaf engine:
```java
final SpringTemplateEngine engine = new SpringTemplateEngine();
engine.addDialect(new VersionedResourceDialect(() -> version));

```



By adding `version:append` to a `href` or `src` attribute, the version will be appended to the URL:

```html
<script type="text/javascript" src="/path/to/resource.js" version:append></script>
```
or
```html
<script type="text/javascript" src="/path/to/resource.js" data-version-append></script>
```

will be rendered as

```html
<script type="text/javascript" src="/path/to/resource.js?version=xxxx"></script>
```

Notes:
* if a query string is already present the version parameter will be concatenated using &amp;
* an already present `version` parameter will not be overridden
* empty `src`/`href` attributes will not be updated