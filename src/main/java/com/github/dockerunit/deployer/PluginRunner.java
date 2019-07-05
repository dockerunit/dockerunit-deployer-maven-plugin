package com.github.dockerunit.deployer;

import com.github.dockerunit.deployer.spring.ShellLauncher;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Mojo(name = "run", defaultPhase = LifecyclePhase.TEST,
        requiresDependencyResolution = ResolutionScope.TEST,
requiresProject = true)
public class PluginRunner extends AbstractMojo {


    @Parameter(defaultValue="${project}", readonly=true, required=true)
    private MavenProject project;

    @Parameter( property = "dockerunit-deployer.className")
    private String className;

    private static Class<?> svcClass;

    public static Class<?> getSvcClass() {
        return svcClass;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (null == className || className.isEmpty()) {
            throw new MojoExecutionException("You must specify the fully qualified name of the Dockerunit class to run " +
                    "using the <className> tag inside the plugin <configuration>.");
        }


        try {
            List<String> args = new ArrayList<>();
            args.add(className);
            List<String> testClasspathElements = project.getTestClasspathElements();
            List<Resource> testResources = project.getTestResources();
            testClasspathElements.addAll(testResources
                    .stream()
                    .map(res -> res.getDirectory())
                    .collect(Collectors.toSet()));
            args.addAll(testClasspathElements.stream()
                    .map(elem -> Arrays.asList("--classpath", elem))
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList()));

            ShellLauncher.run(args.toArray(new String[]{}));
        } catch (Exception e) {
            throw new MojoFailureException("Could not initialise shell.", e);
        }
    }



}
