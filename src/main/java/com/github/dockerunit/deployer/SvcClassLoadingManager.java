package com.github.dockerunit.deployer;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class SvcClassLoadingManager {

    private static Class<?> svcClass;
    private static URLClassLoader classLoader;

    public static synchronized void initialiseClassLoader(List<String> runtimeClasspathElements)
            throws MalformedURLException {

        URL[] runtimeUrls = new URL[runtimeClasspathElements.size()];
        for (int i = 0; i < runtimeClasspathElements.size(); i++) {
            String element = runtimeClasspathElements.get(i);
            runtimeUrls[i] = new File(element).toURI().toURL();
        }
        classLoader = new URLClassLoader(runtimeUrls,
                Thread.currentThread().getContextClassLoader());
    }

    public static ClassLoader getClassLoader() {
        return classLoader;
    }

    public static synchronized Class<?> loadClass(String className) throws ClassNotFoundException {
        svcClass = classLoader.loadClass(className);
        return svcClass;
    }

    public static synchronized Class<?> getSvcClass() {
        return svcClass;
    }

}
