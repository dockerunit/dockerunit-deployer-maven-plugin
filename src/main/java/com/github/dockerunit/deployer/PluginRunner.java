package com.github.dockerunit.deployer;

import com.github.dockerunit.deployer.spring.ShellLauncher;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * @requiresDependencyResolution test
 *
 */
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

        ClassLoader loader = initialiseClassLoader();
        try {
            svcClass = loader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new MojoFailureException("Cannot find class " + className, e);
        }

        try {
            ShellLauncher.run(new String[]{ className });
        } catch (Exception e) {
            throw new MojoFailureException("Could not initialise shell.", e);
        }

    }

    private ClassLoader initialiseClassLoader() throws MojoFailureException {
        List<String> runtimeClasspathElements = null;
        try {
            runtimeClasspathElements = project.getTestClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
        URL[] runtimeUrls = new URL[runtimeClasspathElements.size()];
        for (int i = 0; i < runtimeClasspathElements.size(); i++) {
            String element = (String) runtimeClasspathElements.get(i);
            try {
                runtimeUrls[i] = new File(element).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new MojoFailureException(e.getMessage(), e);
            }
        }
        URLClassLoader newLoader = new URLClassLoader(runtimeUrls,
                Thread.currentThread().getContextClassLoader());
        return  newLoader;
    }

}
