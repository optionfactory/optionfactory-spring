# optionfactory-spring/optionfactory-spring-dependencies

Bill of Materials (BOM) for the optionfactory-spring modules and the third-party dependencies they
are built and tested with.

## Maven

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>net.optionfactory.spring</groupId>
            <artifactId>optionfactory-spring-dependencies</artifactId>
            <version>${optionfactory-spring.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Usage

Import the BOM in your `dependencyManagement` section to manage, with one version, both the
`optionfactory-spring` modules (it extends
[`optionfactory-spring-bom`](../optionfactory-spring-bom/readme.md)) and the platform they target:
the Spring Framework, Spring Security, Spring Data and Jackson BOMs, the Boot artifacts for the
embedded server, Hibernate, Tomcat, Thymeleaf, the JDBC drivers and pool, JUnit and the other
libraries the modules use (see [0004](../docs/decisions/0004-platform.md)).

The BOM declares no scopes: state them in your dependencies, `provided` for
`jakarta.servlet-api` and `test` for JUnit and the other test libraries. The versions the library
only uses to build itself are not managed.

To manage the third-party versions on your own, import `optionfactory-spring-bom` instead.
