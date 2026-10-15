# optionfactory-spring/optionfactory-spring-bom

Bill of Materials (BOM) for the optionfactory-spring modules only.

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

Import the BOM in your `dependencyManagement` section to manage the versions of all the
`optionfactory-spring` modules. Third-party dependencies are not managed: the modules bring them
transitively, and the application states the version of any it declares itself.

To have the third-party stack aligned by the same import, use
[`optionfactory-spring-dependencies`](../optionfactory-spring-dependencies/readme.md) instead.

The BOM declares no scopes and has no parent: importing it brings the module versions and nothing
else.
