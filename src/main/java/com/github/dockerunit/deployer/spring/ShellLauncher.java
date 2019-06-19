package com.github.dockerunit.deployer.spring;

import com.github.dockerunit.core.ServiceContext;
import com.github.dockerunit.core.ServiceInstance;
import com.github.dockerunit.core.discovery.DiscoveryProvider;
import com.github.dockerunit.core.discovery.DiscoveryProviderFactory;
import com.github.dockerunit.core.internal.ServiceContextBuilder;
import com.github.dockerunit.core.internal.ServiceContextBuilderFactory;
import com.github.dockerunit.core.internal.UsageDescriptor;
import com.github.dockerunit.core.internal.reflect.DependencyDescriptorBuilderFactory;
import com.github.dockerunit.core.internal.reflect.UsageDescriptorBuilder;
import com.github.dockerunit.deployer.DockerUnitSetup;
import com.github.dockerunit.deployer.ServiceContextProvider;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.shell.jline.PromptProvider;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@SpringBootApplication
@ComponentScan("com.github.dockerunit.deployer.commands")
public class ShellLauncher {


    private static final Logger logger = Logger.getLogger(ShellLauncher.class.getSimpleName());

    private static final String CLASSPATH_OPTION = "--classpath";


    private static DiscoveryProvider discoveryProvider;

    private static final UsageDescriptorBuilder descriptorBuilder = DependencyDescriptorBuilderFactory.create();
    private static final ServiceContextBuilder contextBuilder = ServiceContextBuilderFactory.create();


    public static void run(String[] args) throws Exception {
        initDiscovery();
        Class<?> svcClass = loadSvcClass(args[0], Arrays.asList(Arrays.copyOfRange(args, 1, args.length))
                .stream()
                .filter(arg -> !arg.equals(CLASSPATH_OPTION))
                .collect(Collectors.toList()));

        doSetup(svcClass);
        SpringApplication.run(ShellLauncher.class, args);
    }

    @Bean
    public PromptProvider myPromptProvider() {
        return () -> new AttributedString("dude-shell:>", AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
    }

    private static void initDiscovery() {
        ServiceLoader<DiscoveryProviderFactory> loader = ServiceLoader.load(DiscoveryProviderFactory.class);

        discoveryProvider = StreamSupport.stream(loader.spliterator(), false)
                .peek(impl -> logger.info(
                        "Found discovery provider factory of type " + impl.getClass().getSimpleName()))
                .findFirst()
                .map(impl -> {
                    logger.info("Using discovery provider factory " + impl.getClass().getSimpleName());
                    return impl;
                })
                .map(DiscoveryProviderFactory::getProvider)
                .orElseThrow(() -> new RuntimeException("No discovery provider factory found. Aborting."));

    }

    private static void doSetup(final Class<?> clazz) {
        UsageDescriptor descriptor = descriptorBuilder.buildDescriptor(clazz);
        UsageDescriptor discoveryProviderDescriptor = descriptorBuilder.buildDescriptor(discoveryProvider.getDiscoveryConfig());

        // Build discovery context
        ServiceContext discoveryContext = contextBuilder.buildContext(discoveryProviderDescriptor);
        ServiceContextProvider.setDiscoveryContext(discoveryContext);
        if (!discoveryContext.checkStatus(ServiceInstance.Status.STARTED)) {
            throw new RuntimeException(discoveryContext.getFormattedErrors());
        }

        ServiceContext svcContext = new DockerUnitSetup(contextBuilder, discoveryProvider).setup(descriptor);
        ServiceContextProvider.setSvcContext(svcContext);
        if (!svcContext.checkStatus(ServiceInstance.Status.DISCOVERED)) {
            throw new RuntimeException(svcContext.getFormattedErrors());
        }

    }


    private static Class<?> loadSvcClass(String className, List<String> runtimeClasspathElements)
            throws ClassNotFoundException, MalformedURLException {

        URL[] runtimeUrls = new URL[runtimeClasspathElements.size()];
        for (int i = 0; i < runtimeClasspathElements.size(); i++) {
            String element = (String) runtimeClasspathElements.get(i);
            runtimeUrls[i] = new File(element).toURI().toURL();
        }
        URLClassLoader newLoader = new URLClassLoader(runtimeUrls,
                Thread.currentThread().getContextClassLoader());
        return newLoader.loadClass(className);
    }

}
