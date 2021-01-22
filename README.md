Dockerunit Deployer Maven Plugin
================================
A simple maven plugin that allows you to run a Dockerunit test configuration as a real deployment.
Simply add the following to your pom.xml:

```xml
<plugin>
    <groupId>com.github.dockerunit</groupId>
    <artifactId>dockerunit-deployer-maven-plugin</artifactId>
    <version>0.2.0-SNAPSHOT</version>
    <configuration>
        <className>org.example.YourClass</className>
    </configuration>
</plugin>
```

`org.example.YourClass` is the name of the class where you are importing your descriptors using the `@WithSvc` 
annotation(see [examples](https://dockerunit.github.io/multiple-services/)). 

To run your deployment, use the following command:
`mvn com.github.dockerunit:dockerunit-deployer-maven-plugin:run -Ddocker.bridge.ip=$(docker inspect --format='{{range .IPAM.Config}}{{println .Gateway}}{{end}}' bridge)`
