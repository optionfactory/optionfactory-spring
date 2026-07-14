# optionfactory-spring/data-jpa

Declarative whitelisted filters on JPA `@Entity` types.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>data-jpa</artifactId>
</dependency>
```

## Usage

### 1. Enable Filtering Repositories

Use `@EnableJpaWhitelistFilteringRepositories` instead of `@EnableJpaRepositories`:

```java
@Configuration
@EnableJpaWhitelistFilteringRepositories(basePackageClasses = MyRepository.class)
public class JpaConfig {
}
```

### 2. Annotate the Entity

Configure the filters you want to allow:

```java
@Entity
@Filters({
    @Filter(name = "name", property = "name", operators = TextCompare.Operator.EQ),
    @Filter(name = "age", property = "age", operators = NumberCompare.Operator.GTE)
})
public class Person {
    private String name;
    private int age;
}
```

### 3. Create a Repository

Extend `WhitelistFilteringRepository`:

```java
public interface PersonRepository extends WhitelistFilteringRepository<Person> {
}
```

### 4. Use the Repository

Configure the `FilterRequest` (e.g., from user input):

```java
FilterRequest fr = FilterRequest.builder()
    .filter("name", "John")
    .filter("age", "18")
    .build();

List<Person> results = personRepository.findAll(fr);
```

