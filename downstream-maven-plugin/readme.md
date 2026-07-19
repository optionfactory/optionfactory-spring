# optionfactory-spring/downstream-maven-plugin

Maven plugin to generate Java DTOs (as records or classes) and TypeScript type definitions from Spring MVC controllers annotated with `@Downstream.Method`.

## Maven

```xml
<plugin>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>downstream-maven-plugin</artifactId>
    <version>${opfa.spring.version}</version>
</plugin>
```



## Usage

Annotate endpoints/methods in your "server" project with `@Downstream.Method`:

```java
    @PostMapping("/api/notification/")
    @Downstream.Method    
    public long create(@RequestPart @Valid MulticastMessageNotificationRequest req, @RequestPart(required = false) MultipartFile file)

```



Configure this maven plugin in your "client" project:
Make sure you add the "server" as a dependency of the plugin.

## generate-dtos

Generates types in `target/generated-sources/downstream-{target}`

### Configuration params

| Param              | Mandatory | Default  | Description
| --                 | --        | --       | -- 
| `sourceBasePackage`| `true`    | N/A      | The base package to scan for `@Downstream.Method`s
| `target`           | `false`   | `targetClientName` or ""        | A name for this target
| `targetPackage`    | `true`    | N/A       | The package where DTOs and enums are generated
| `targetClientName` | `false`   | `null`    | The client name used to filter `@Downstream.Method`s for this execution
| `nesting`          | `true`    | `NESTED`  | The nesting strategy to use when generating code: `NESTED` tries keeping inner classes as inner classes, `FLATTEN` moves them to the root. 
| `translations`     | `false`   | N/A       | Type translations to apply
| `outputStyle`      | `false`   | `RECORDS` | The strategy used to generate Java DTOs: Can either be `RECORDS` or  `CLASSES`
| `outputStyleOverrides`      | `false`   | `empty set` | Classes that should use the non default outputStyle

### Example

```xml
<plugin>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>downstream-maven-plugin</artifactId>
    <version>${opfa.spring.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>generate-dtos</goal>
            </goals>
            <configuration>
                <sourceBasePackage>net.optionfactory.server</sourceBasePackage>
                <targetClientName>my-client</targetClientName>
                <translations>
                    <org.springframework.web.multipart.MultipartFile>byte[]</org.springframework.web.multipart.MultipartFile>
                    <java.time.LocalDate>java.lang.String</java.time.LocalDate>
                </translations>            
            </configuration>
        </execution>
    </executions>
    <dependencies>
        <dependency>
            <groupId>net.optionfactory.server</groupId> 
            <artifactId>my-server</artifactId>
            <version>${project.version}</version>
            <type>war</type>
        </dependency>                                
    </dependencies>
</plugin>          

```

## generate-ts

Generates types in `target/generated-resources/downstream-{target}`


### Configuration params

| Param               | Mandatory | Default   | Description
|---------------------| --        | --        | -- 
| `sourceBasePackage` | `true`    | N/A       | The base package to scan for `@Downstream.Method`s
| `target`            | `false`   | `targetClientName` or ""        | A name for this target
| `targetClientName`  | `false`   | `null`    | The client name used to filter `@Downstream.Method`s for this execution
| `targetDirectory`   | `false`   | `null`    | Target directory in which to create the generated spec.d.ts
| `nesting`           | `true`    | `FLATTEN` | The nesting strategy to use when generating code: `NESTED` renames inner classes with the outer class as prefix, `FLATTEN` moves them to the root.
| `translations`      | `false`   | N/A       | Type translations to apply



### Example


```xml
<plugin>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>downstream-maven-plugin</artifactId>
    <version>${opfa.spring.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>generate-ts</goal>
            </goals>
            <configuration>
                <sourceBasePackage>net.optionfactory.server</sourceBasePackage>
                <targetClientName>my-client</targetClientName>
                <nesting>FLATTEN</nesting>
                <translations>
                    <org.springframework.web.multipart.MultipartFile>byte[]</org.springframework.web.multipart.MultipartFile>
                    <java.time.LocalDate>java.lang.String</java.time.LocalDate>
                </translations>                                
            </configuration>
        </execution>
    </executions>
    <dependencies>
        <dependency>
            <groupId>net.optionfactory.server</groupId> 
            <artifactId>my-server</artifactId>
            <version>${project.version}</version>
            <type>war</type>
        </dependency>                                
    </dependencies>
</plugin>          
```