# optionfactory-spring/optionfactory-spring-bom

Bill of Materials (BOM) for optionfactory-spring modules.

## Maven

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>net.optionfactory.spring</groupId>
            <artifactId>optionfactory-spring-bom</artifactId>
            <version>${optionfactory-spring.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Usage

Import the BOM in your `dependencyManagement` section to manage versions of all `optionfactory-spring` modules.


