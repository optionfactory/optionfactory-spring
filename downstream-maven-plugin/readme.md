# optionfactory-spring/downstream-maven-plugin


## Maven

```xml
        <dependency>
            <groupId>net.optionfactory.spring</groupId>
            <artifactId>downstream-maven-plugin</artifactId>
        </dependency>
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
                            <targetPackage>net.optionfactory.myclient</targetPackage>
                            <targetClientName>MyClient</targetClientName>
                            <buildDtosAsClasses>true</buildDtosAsClasses>
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