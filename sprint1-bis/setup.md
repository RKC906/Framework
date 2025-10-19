1. **Folder generation**
```bash
mvn archetype:generate -DgroupId=com.giga.spring \
    -DartifactId=gigaspring \
    -Dversion=1.0-SNAPSHOT \
    -Dpackage=com.giga.spring \
    -DarchetypeArtifactId=maven-archetype-quickstart \
    -DinteractiveMode=false
```

2. **Install plugins**
Inside the `<build>` in `pom.xml` section:
add `exec` and pricise we use `JDK 17`
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <source>17</source>
                <target>17</target>
            </configuration>
        </plugin>

        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <version>3.1.0</version>
            <configuration>
                <mainClass>com.giga.spring.Main</mainClass>
            </configuration>
        </plugin>
    </plugins>
</build>
```

3. Put the latest build to local dev (in `~/.m2/repository`)
```bash
./package.sh
```