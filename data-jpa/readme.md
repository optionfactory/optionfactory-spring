# optionfactory-spring/data-jpa

Declarative whitelisted filters on `@Entity`.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>data-jpa</artifactId>
</dependency>
```

## Usage

1. Enable by using `@EnableJpaWhitelistFilteringRepositories` instead of
   `@EnableJpaRepositories`: [Example](https://github.com/optionfactory/optionfactory-spring/blob/92c24c910896c12fb37ef0cf3af3272434b3eddf/data-jpa/src/test/java/net/optionfactory/spring/data/jpa/filtering/psql/HibernateOnPsqlTestConfig.java#L25-L29)
2. Create a repository extending
   `WhitelistFilteringRepository<T>`: [Example](https://github.com/optionfactory/optionfactory-spring/blob/92c24c910896c12fb37ef0cf3af3272434b3eddf/data-jpa/src/test/java/net/optionfactory/spring/data/jpa/filtering/psql/examples/PetOwnersRepository.java#L1-L8)
3. Annotate the root entity to configure the filters you want to
   allow: [Example](https://github.com/optionfactory/optionfactory-spring/blob/92c24c910896c12fb37ef0cf3af3272434b3eddf/data-jpa/src/test/java/net/optionfactory/spring/data/jpa/filtering/psql/examples/PetOwner.java#L17-L24)
4. Configure the filter (possibly from user controlled data) when using the
   repository: [Example](https://github.com/optionfactory/optionfactory-spring/blob/92c24c910896c12fb37ef0cf3af3272434b3eddf/data-jpa/src/test/java/net/optionfactory/spring/data/jpa/filtering/psql/examples/PetOwnerExampleTest.java#L52-L59)

## Reduction support

If you need to perform a reduction with filter support:

1. [Create a custom Repository interface](https://github.com/optionfactory/optionfactory-spring/blob/92c24c910896c12fb37ef0cf3af3272434b3eddf/data-jpa/src/test/java/net/optionfactory/spring/data/jpa/filtering/h2/reduction/ReductionNumberEntityRepository.java)
2. [Implement it](https://github.com/optionfactory/optionfactory-spring/blob/92c24c910896c12fb37ef0cf3af3272434b3eddf/data-jpa/src/test/java/net/optionfactory/spring/data/jpa/filtering/h2/reduction/ReductionNumberEntityRepositoryImpl.java)
3. [Link in main repository (extend the interface)](https://github.com/optionfactory/optionfactory-spring/blob/92c24c910896c12fb37ef0cf3af3272434b3eddf/data-jpa/src/test/java/net/optionfactory/spring/data/jpa/filtering/h2/reduction/NumberEntityRepository.java#L6)
4. [Use it passing the FilterRequest](https://github.com/optionfactory/optionfactory-spring/blob/92c24c910896c12fb37ef0cf3af3272434b3eddf/data-jpa/src/test/java/net/optionfactory/spring/data/jpa/filtering/h2/reduction/ReductionTest.java#L44-L52)

---

TODO: document annotation options (operators, sensitivity, format)

TODO: document `QueryMode.SUBSELECT`, `QueryMode.JOIN` and implicatations

TODO: document Custom Filters

TODO: document `Pageable`, `Sort`, `Specification`

TODO: document `SessionPolicy` and eviction

TODO: reference data-jpa-web

