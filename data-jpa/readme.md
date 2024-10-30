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

Enable by using `@EnableJpaWhitelistFilteringRepositories` instead of `@EnableJpaRepositories`:

https://github.com/optionfactory/optionfactory-spring/blob/6327185b5f4ea2620fc08fd8f5275474146ac952/data-jpa/src/test/java/net/optionfactory/spring/data/jpa/filtering/psql/HibernateOnPsqlTestConfig.java#L25-L29

Create a repository extending `WhitelistFilteringRepository<T>`:

https://github.com/optionfactory/optionfactory-spring/blob/6327185b5f4ea2620fc08fd8f5275474146ac952/data-jpa/src/test/java/net/optionfactory/spring/data/jpa/filtering/psql/examples/PetOwnersRepository.java#L1-L8

Annotate the root entity to configure the filters you want to allow:

https://github.com/optionfactory/optionfactory-spring/blob/6327185b5f4ea2620fc08fd8f5275474146ac952/data-jpa/src/test/java/net/optionfactory/spring/data/jpa/filtering/psql/examples/PetOwner.java#L17-L24

Configure the filter (possibly from user controlled data) when using the repository:

https://github.com/optionfactory/optionfactory-spring/blob/6327185b5f4ea2620fc08fd8f5275474146ac952/data-jpa/src/test/java/net/optionfactory/spring/data/jpa/filtering/psql/examples/PetOwnerExampleTest.java#L52-L59

