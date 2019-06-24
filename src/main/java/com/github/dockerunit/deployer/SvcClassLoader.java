package com.github.dockerunit.deployer;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class SvcClassLoader {

    private static Class<?> svcClass;
    private static URLClassLoader classLoader;

    public static synchronized Class<?> loadSvcClass(String className, List<String> runtimeClasspathElements)
            throws ClassNotFoundException, MalformedURLException {

        URL[] runtimeUrls = new URL[runtimeClasspathElements.size()];
        for (int i = 0; i < runtimeClasspathElements.size(); i++) {
            String element = runtimeClasspathElements.get(i);
            runtimeUrls[i] = new File(element).toURI().toURL();
        }
        classLoader = new URLClassLoader(runtimeUrls,
                Thread.currentThread().getContextClassLoader());
        return loadSvcClass(className);
    }

    public static synchronized Class<?> loadSvcClass(String className) throws ClassNotFoundException {
        svcClass = classLoader.loadClass(className);
        return svcClass;
    }

    public static synchronized Class<?> getSvcClass() {
        return svcClass;
    }

}
